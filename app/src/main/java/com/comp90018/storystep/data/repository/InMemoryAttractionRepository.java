package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.geo.NearbyAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic {@link AttractionRepository} backed by a fixed list.
 *
 * <p>It answers synchronously and performs no I/O, which makes it a usable fake in tests and in
 * any assembly that wants a fixed set of places. {@link CachedAttractionRepository} is the runtime
 * source; nothing in the UI layer distinguishes them.
 *
 * <p>Results are reported {@link AttractionFreshness#FRESH}: a fixed list makes no claim about a
 * provider, so there is no provider answer for it to be stale relative to.
 */
public final class InMemoryAttractionRepository implements AttractionRepository {

    private final List<Attraction> attractions;

    public InMemoryAttractionRepository() {
        this(SampleAttractions.get());
    }

    public InMemoryAttractionRepository(List<Attraction> attractions) {
        this.attractions = Collections.unmodifiableList(new ArrayList<>(attractions));
    }

    @Override
    public void loadNearbyAttractions(GeoPoint origin,
                                      double radiusMeters,
                                      RepositoryCallback<AttractionResult> callback) {
        try {
            callback.onSuccess(AttractionResult.fresh(
                    NearbyAttractions.within(attractions, origin, radiusMeters)));
        } catch (RuntimeException error) {
            callback.onError(error);
        }
    }

    @Override
    public void loadAttraction(String attractionId, RepositoryCallback<Attraction> callback) {
        for (Attraction attraction : attractions) {
            if (attraction.getId().equals(attractionId)) {
                callback.onSuccess(attraction);
                return;
            }
        }
        callback.onError(new AttractionNotFoundException(attractionId));
    }
}
