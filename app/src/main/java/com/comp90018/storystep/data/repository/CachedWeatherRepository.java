package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.LocalWeatherMapper;
import com.comp90018.storystep.data.local.WeatherCacheDao;
import com.comp90018.storystep.data.remote.WeatherSource;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.WeatherRepository;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.domain.weather.WeatherResult;
import com.comp90018.storystep.platform.connectivity.ConnectivityMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The production {@link WeatherRepository}: a local cache in front of one network source.
 *
 * <p>Four policies live here, and each exists because of a specific way this feature can go wrong.
 *
 * <ul>
 *   <li><b>Cache first.</b> A fresh stored observation answers without a request. Weather changes
 *       over tens of minutes and location fixes arrive every few seconds, so without this every
 *       step the user takes would be a network call.</li>
 *   <li><b>Coalescing.</b> At most one request per area is in flight. The nearby screen and the
 *       exploration dashboard both ask on resume, and they must produce one request between
 *       them, not two.</li>
 *   <li><b>Stale fallback.</b> A failed refresh that still has a presentable cached observation is
 *       a <i>success</i> carrying stale data. Blanking a readable temperature to report a network
 *       problem is the behaviour this class exists to prevent.</li>
 *   <li><b>Atomic replace.</b> The cache is only written from a snapshot the mapper already
 *       validated, so a malformed response cannot overwrite good data with a broken row.</li>
 * </ul>
 *
 * <p>Queries run on the I/O executor and results are dispatched on the main executor, matching the
 * {@code RepositoryCallback} contract.
 */
public final class CachedWeatherRepository implements WeatherRepository {

    private final WeatherCacheDao dao;
    private final WeatherSource source;
    private final ConnectivityMonitor connectivity;
    private final WeatherFreshnessPolicy freshnessPolicy;
    private final Clock clock;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    /**
     * Callbacks waiting on an area whose request is already running.
     *
     * <p>Guarded by its own monitor. Every entry is drained exactly once, by the work that created
     * it, so a callback cannot be stranded by a failure path.
     */
    private final Map<String, List<RepositoryCallback<WeatherResult>>> inFlight = new HashMap<>();

    public CachedWeatherRepository(WeatherCacheDao dao,
                                   WeatherSource source,
                                   ConnectivityMonitor connectivity,
                                   WeatherFreshnessPolicy freshnessPolicy,
                                   Clock clock,
                                   Executor ioExecutor,
                                   Executor mainExecutor) {
        this.dao = requireNonNull(dao, "dao");
        this.source = requireNonNull(source, "source");
        this.connectivity = requireNonNull(connectivity, "connectivity");
        this.freshnessPolicy = requireNonNull(freshnessPolicy, "freshnessPolicy");
        this.clock = requireNonNull(clock, "clock");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void loadWeather(GeoPoint position, RepositoryCallback<WeatherResult> callback) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        WeatherAreaBucket bucket = WeatherAreaBucket.of(position);
        String areaKey = bucket.key();
        synchronized (inFlight) {
            List<RepositoryCallback<WeatherResult>> waiting = inFlight.get(areaKey);
            if (waiting != null) {
                // Someone is already asking about this area. Joining them is the coalescing rule;
                // scheduling a second run would issue a second request for the same answer.
                waiting.add(callback);
                return;
            }
            List<RepositoryCallback<WeatherResult>> started = new ArrayList<>();
            started.add(callback);
            inFlight.put(areaKey, started);
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                resolve(bucket, areaKey);
            }
        });
    }

    /** Runs on the I/O executor. Always completes the area's waiting list exactly once. */
    private void resolve(WeatherAreaBucket bucket, String areaKey) {
        WeatherSnapshot cached = null;
        WeatherFreshness cachedFreshness = WeatherFreshness.EXPIRED;
        try {
            cached = LocalWeatherMapper.toDomain(dao.findByArea(areaKey));
            cachedFreshness = freshnessPolicy.classify(cached, clock.wallClockMillis());
            if (cached != null && cachedFreshness == WeatherFreshness.FRESH) {
                succeed(areaKey, new WeatherResult(cached, WeatherFreshness.FRESH));
                return;
            }
        } catch (RuntimeException cacheUnreadable) {
            // A cache that cannot be read is not a reason to give up: the network may still
            // answer. Fall through with no cached snapshot.
            cached = null;
            cachedFreshness = WeatherFreshness.EXPIRED;
        }

        if (!connectivity.isOnline()) {
            // Skip a request that is certain to fail, but only after the cache has been consulted:
            // a warm offline launch must still show the last observation.
            fallBack(areaKey, cached, cachedFreshness,
                    new WeatherUnavailableException(WeatherUnavailableException.Reason.NO_NETWORK));
            return;
        }

        try {
            WeatherSnapshot fetched =
                    source.fetch(bucket.center(), areaKey, clock.wallClockMillis());
            try {
                dao.upsert(LocalWeatherMapper.toEntity(fetched));
            } catch (RuntimeException writeFailed) {
                // The observation is good even if storing it was not. Serve it anyway; the cost of
                // a cache write that failed is one extra request next time, not a worse answer now.
            }
            succeed(areaKey, new WeatherResult(fetched, WeatherFreshness.FRESH));
        } catch (WeatherUnavailableException unavailable) {
            fallBack(areaKey, cached, cachedFreshness, unavailable);
        } catch (RuntimeException unexpected) {
            fallBack(areaKey, cached, cachedFreshness,
                    new WeatherUnavailableException(
                            WeatherUnavailableException.Reason.MALFORMED, unexpected));
        }
    }

    /**
     * Serves the cached observation when the refresh failed, or reports why nothing can be shown.
     *
     * <p>{@code NO_CACHE} replaces the fetch reason only when the fetch never happened because the
     * device is offline — that combination is the cold offline start, and the screen has a
     * different thing to say about it than about a timeout.
     */
    private void fallBack(String areaKey,
                          WeatherSnapshot cached,
                          WeatherFreshness cachedFreshness,
                          WeatherUnavailableException failure) {
        if (cached != null && cachedFreshness.isPresentable()) {
            succeed(areaKey, new WeatherResult(cached.asCached(), cachedFreshness));
            return;
        }
        if (cached == null
                && failure.getReason() == WeatherUnavailableException.Reason.NO_NETWORK) {
            fail(areaKey, new WeatherUnavailableException(
                    WeatherUnavailableException.Reason.NO_CACHE, failure));
            return;
        }
        fail(areaKey, failure);
    }

    @Override
    public void clearCache(RepositoryCallback<Integer> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int removed = dao.deleteAll();
                    mainExecutor.execute(() -> callback.onSuccess(removed));
                } catch (RuntimeException error) {
                    mainExecutor.execute(() -> callback.onError(error));
                }
            }
        });
    }

    private void succeed(String areaKey, WeatherResult result) {
        for (RepositoryCallback<WeatherResult> callback : drain(areaKey)) {
            mainExecutor.execute(() -> callback.onSuccess(result));
        }
    }

    private void fail(String areaKey, Throwable error) {
        for (RepositoryCallback<WeatherResult> callback : drain(areaKey)) {
            mainExecutor.execute(() -> callback.onError(error));
        }
    }

    /** Removes and returns everyone waiting on this area, so each is answered exactly once. */
    private List<RepositoryCallback<WeatherResult>> drain(String areaKey) {
        synchronized (inFlight) {
            List<RepositoryCallback<WeatherResult>> waiting = inFlight.remove(areaKey);
            return waiting == null ? new ArrayList<>() : waiting;
        }
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
