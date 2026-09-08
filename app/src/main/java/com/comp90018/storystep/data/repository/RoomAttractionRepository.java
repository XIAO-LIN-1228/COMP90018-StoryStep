package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.geo.NearbyAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * A local-only {@link AttractionRepository}: whatever is stored, and nothing from the network.
 *
 * <p>Queries run on the I/O executor and answer on the main executor, matching the
 * {@code RepositoryCallback} contract. Filtering and ordering are delegated to
 * {@link NearbyAttractions} rather than expressed in SQL: the radius test is a great-circle
 * distance, which SQLite cannot compute, and approximating it with a bounding box would change
 * which places appear at the boundary the nearby tests pin down.
 *
 * <p>Results are always reported {@link AttractionFreshness#FRESH}. That is honest for what this
 * class is — it makes no claim about a provider, so there is no provider answer for it to be
 * stale relative to. The production wiring is {@link CachedAttractionRepository}, which does make
 * that claim and labels it; this one remains for tests and for an assembly that deliberately wants
 * no network.
 */
public final class RoomAttractionRepository implements AttractionRepository {

    private final AttractionCache cache;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    public RoomAttractionRepository(AttractionCache cache,
                                    Executor ioExecutor,
                                    Executor mainExecutor) {
        this.cache = requireNonNull(cache, "cache");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void loadNearbyAttractions(GeoPoint origin,
                                      double radiusMeters,
                                      RepositoryCallback<AttractionResult> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(() -> {
            try {
                List<Attraction> nearby =
                        NearbyAttractions.within(cache.readAll(), origin, radiusMeters);
                succeed(callback, AttractionResult.fresh(nearby));
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
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
                    fail(callback, new AttractionNotFoundException(attractionId));
                    return;
                }
                succeed(callback, attraction);
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    private <T> void succeed(RepositoryCallback<T> callback, T result) {
        mainExecutor.execute(() -> callback.onSuccess(result));
    }

    private <T> void fail(RepositoryCallback<T> callback, Throwable error) {
        mainExecutor.execute(() -> callback.onError(error));
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
