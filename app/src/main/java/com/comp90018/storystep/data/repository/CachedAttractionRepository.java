package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.AttractionAreaEntity;
import com.comp90018.storystep.data.remote.AttractionSource;
import com.comp90018.storystep.domain.attraction.AttractionAreaBucket;
import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.attraction.AttractionFreshnessPolicy;
import com.comp90018.storystep.domain.geo.NearbyAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.platform.connectivity.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * The production {@link AttractionRepository}: a local cache in front of one network source.
 *
 * <p>The same four policies as {@code CachedWeatherRepository}, for the same four reasons, and one
 * more that is specific to this path.
 *
 * <ul>
 *   <li><b>Cache first.</b> An area fetched within the last day answers without a request.
 *       Location fixes arrive every few seconds and the nearby screen re-queries as the user
 *       moves, so without this a walk would be a stream of network calls for content that had not
 *       changed.</li>
 *   <li><b>Coalescing.</b> At most one request per area is in flight. The nearby screen and the
 *       exploration dashboard both ask on resume, and they must produce one request between them,
 *       not two.</li>
 *   <li><b>Stale fallback.</b> A failed refresh that still has presentable content is a
 *       <i>success</i> carrying stale data. Blanking a usable list of places to report a network
 *       problem is the behaviour this class exists to prevent — and the bundled seed set means
 *       there is nearly always something to show.</li>
 *   <li><b>Atomic replace.</b> Content is written only from records the mapper already validated,
 *       in one transaction with the area's fetch marker, so a malformed response cannot overwrite
 *       good data and a partial write cannot be mistaken for a completed fetch.</li>
 *   <li><b>Empty is an answer.</b> An area the provider searched and found nothing in is cached as
 *       empty, and is not the same as an area never fetched. Without that distinction a genuinely
 *       empty area would be re-requested on every query, and a provider failure would be
 *       indistinguishable from a legitimately quiet neighbourhood.</li>
 * </ul>
 *
 * <p>Queries run on the I/O executor and results are dispatched on the main executor, matching the
 * {@code RepositoryCallback} contract.
 */
public final class CachedAttractionRepository implements AttractionRepository {

    /**
     * How many records to ask the provider for.
     *
     * <p>Overpass caps a result set with a number on its {@code out} statement rather than paging,
     * so this is where the app's pagination decision lives. It is generous enough that a city
     * centre is not truncated in a way the user would notice and bounded enough that one query
     * cannot return a response large enough to matter.
     */
    private static final int FETCH_LIMIT = 60;

    private final AttractionCache cache;
    private final AttractionSource source;
    private final ConnectivityMonitor connectivity;
    private final AttractionFreshnessPolicy freshnessPolicy;
    private final Clock clock;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    /**
     * Requests waiting on an area whose fetch is already running.
     *
     * <p>Guarded by its own monitor. Every entry is drained exactly once, by the work that created
     * it, so a callback cannot be stranded by a failure path.
     */
    private final Map<String, List<Pending>> inFlight = new HashMap<>();

    public CachedAttractionRepository(AttractionCache cache,
                                      AttractionSource source,
                                      ConnectivityMonitor connectivity,
                                      AttractionFreshnessPolicy freshnessPolicy,
                                      Clock clock,
                                      Executor ioExecutor,
                                      Executor mainExecutor) {
        this.cache = requireNonNull(cache, "cache");
        this.source = requireNonNull(source, "source");
        this.connectivity = requireNonNull(connectivity, "connectivity");
        this.freshnessPolicy = requireNonNull(freshnessPolicy, "freshnessPolicy");
        this.clock = requireNonNull(clock, "clock");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void loadNearbyAttractions(GeoPoint origin,
                                      double radiusMeters,
                                      RepositoryCallback<AttractionResult> callback) {
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        if (!(radiusMeters > 0.0)) {
            throw new IllegalArgumentException("radiusMeters must be positive: " + radiusMeters);
        }
        AttractionAreaBucket bucket = AttractionAreaBucket.of(origin);
        String areaKey = bucket.key();
        Pending pending = new Pending(origin, radiusMeters, callback);
        synchronized (inFlight) {
            List<Pending> waiting = inFlight.get(areaKey);
            if (waiting != null) {
                // Someone is already asking about this area. Joining them is the coalescing rule;
                // scheduling a second run would issue a second request for the same answer. Each
                // waiter keeps its own origin and radius, so sharing the fetch does not mean
                // sharing the filtering.
                waiting.add(pending);
                return;
            }
            List<Pending> started = new ArrayList<>();
            started.add(pending);
            inFlight.put(areaKey, started);
        }
        ioExecutor.execute(() -> resolve(bucket, areaKey));
    }

    /** Runs on the I/O executor. Always completes the area's waiting list exactly once. */
    private void resolve(AttractionAreaBucket bucket, String areaKey) {
        AttractionFreshness cachedFreshness = AttractionFreshness.EXPIRED;
        try {
            // Before anything else, so the bundled set is present whichever way this call goes:
            // it is what a failed first fetch falls back to, and what a cold offline start shows.
            cache.ensureSeeded();
            AttractionAreaEntity area = cache.findArea(areaKey);
            if (area != null) {
                cachedFreshness =
                        freshnessPolicy.classify(area.fetchedAtMillis, clock.wallClockMillis());
                if (cachedFreshness == AttractionFreshness.FRESH) {
                    succeed(areaKey, AttractionFreshness.FRESH);
                    return;
                }
            }
        } catch (RuntimeException areaUnreadable) {
            // Bookkeeping that cannot be read is not a reason to give up: the network may still
            // answer, and the stored content may still be servable. Fall through as if unfetched.
            cachedFreshness = AttractionFreshness.EXPIRED;
        }

        if (!connectivity.isOnline()) {
            // Skip a request that is certain to fail, but only after the cache has been consulted:
            // a warm offline launch must still show the places it already knows about.
            fallBack(areaKey, cachedFreshness, new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.NO_NETWORK));
            return;
        }

        double requestRadius = bucket.requestRadiusMeters(largestRequestedRadius(areaKey));
        try {
            GeoPoint center = bucket.center();
            List<Attraction> fetched =
                    source.fetchNearby(center, requestRadius, FETCH_LIMIT);
            try {
                cache.storeFetched(areaKey, center, requestRadius, fetched,
                        clock.wallClockMillis());
            } catch (RuntimeException writeFailed) {
                // The records are good even if storing them was not, but they cannot be served:
                // everything downstream reads back through the cache. Treat it as a failed
                // refresh, which falls back to whatever is already stored rather than to nothing.
                fallBack(areaKey, cachedFreshness, new AttractionUnavailableException(
                        AttractionUnavailableException.Reason.MALFORMED, writeFailed));
                return;
            }
            succeed(areaKey, AttractionFreshness.FRESH);
        } catch (AttractionUnavailableException unavailable) {
            fallBack(areaKey, cachedFreshness, unavailable);
        } catch (RuntimeException unexpected) {
            fallBack(areaKey, cachedFreshness, new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.MALFORMED, unexpected));
        }
    }

    /**
     * Serves what is stored when the refresh failed, or reports why nothing can be shown.
     *
     * <p>An area whose marker is still presentable is served at its own freshness. Beyond that —
     * an area never fetched, or one whose marker has expired — the cache is still consulted rather
     * than written off, because the bundled seed set lives in the same table and is real content
     * the user can use. It is served {@code STALE}: it is genuine, but it is not a current answer
     * from the provider, and the screen must say so.
     *
     * <p>Only an empty cache is a failure, and {@code NO_CACHE} replaces the reason when the fetch
     * never happened because the device is offline — that combination is the cold offline start,
     * and the screen has a different thing to say about it than about a timeout.
     */
    private void fallBack(String areaKey,
                          AttractionFreshness cachedFreshness,
                          AttractionUnavailableException failure) {
        if (cachedFreshness.isPresentable()) {
            succeed(areaKey, cachedFreshness);
            return;
        }
        boolean hasContent;
        try {
            hasContent = !cache.readAll().isEmpty();
        } catch (RuntimeException unreadable) {
            hasContent = false;
        }
        if (hasContent) {
            succeed(areaKey, AttractionFreshness.STALE);
            return;
        }
        if (failure.getReason() == AttractionUnavailableException.Reason.NO_NETWORK) {
            fail(areaKey, new AttractionUnavailableException(
                    AttractionUnavailableException.Reason.NO_CACHE, failure));
            return;
        }
        fail(areaKey, failure);
    }

    @Override
    public void loadAttraction(String attractionId, RepositoryCallback<Attraction> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(() -> {
            try {
                Attraction attraction = cache.findById(attractionId);
                if (attraction == null) {
                    mainExecutor.execute(
                            () -> callback.onError(new AttractionNotFoundException(attractionId)));
                    return;
                }
                mainExecutor.execute(() -> callback.onSuccess(attraction));
            } catch (RuntimeException error) {
                mainExecutor.execute(() -> callback.onError(error));
            }
        });
    }

    /**
     * The widest radius any waiter on this area asked for.
     *
     * <p>One fetch serves every waiter, so it has to cover the most demanding of them. Taking the
     * first request's radius would silently under-serve a later joiner that wanted to look
     * further.
     */
    private double largestRequestedRadius(String areaKey) {
        synchronized (inFlight) {
            List<Pending> waiting = inFlight.get(areaKey);
            double largest = 0.0;
            if (waiting != null) {
                for (Pending pending : waiting) {
                    largest = Math.max(largest, pending.radiusMeters);
                }
            }
            return largest > 0.0 ? largest : 1.0;
        }
    }

    /**
     * Answers everyone waiting on this area from the cache, each filtered from its own origin.
     *
     * <p>Read once, filtered per waiter. The stored set is shared; which of it is nearby is not.
     */
    private void succeed(String areaKey, AttractionFreshness freshness) {
        List<Pending> waiting = drain(areaKey);
        if (waiting.isEmpty()) {
            return;
        }
        List<Attraction> stored;
        try {
            stored = cache.readAll();
        } catch (RuntimeException unreadable) {
            for (Pending pending : waiting) {
                mainExecutor.execute(() -> pending.callback.onError(unreadable));
            }
            return;
        }
        for (Pending pending : waiting) {
            try {
                List<Attraction> nearby = NearbyAttractions.within(
                        stored, pending.origin, pending.radiusMeters);
                AttractionResult result = new AttractionResult(nearby, freshness);
                mainExecutor.execute(() -> pending.callback.onSuccess(result));
            } catch (RuntimeException error) {
                mainExecutor.execute(() -> pending.callback.onError(error));
            }
        }
    }

    private void fail(String areaKey, Throwable error) {
        for (Pending pending : drain(areaKey)) {
            mainExecutor.execute(() -> pending.callback.onError(error));
        }
    }

    /** Removes and returns everyone waiting on this area, so each is answered exactly once. */
    private List<Pending> drain(String areaKey) {
        synchronized (inFlight) {
            List<Pending> waiting = inFlight.remove(areaKey);
            return waiting == null ? new ArrayList<>() : waiting;
        }
    }

    /** One caller's request, kept while the area's fetch runs on its behalf. */
    private static final class Pending {

        private final GeoPoint origin;
        private final double radiusMeters;
        private final RepositoryCallback<AttractionResult> callback;

        private Pending(GeoPoint origin,
                        double radiusMeters,
                        RepositoryCallback<AttractionResult> callback) {
            this.origin = Objects.requireNonNull(origin, "origin");
            this.radiusMeters = radiusMeters;
            this.callback = Objects.requireNonNull(callback, "callback");
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
