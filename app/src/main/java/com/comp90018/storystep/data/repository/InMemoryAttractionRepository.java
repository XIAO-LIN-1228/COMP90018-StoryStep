package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.geo.NearbyAttractions;
import com.comp90018.storystep.domain.model.Attraction;
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
 * <p>It answers synchronously and performs no I/O, which makes it the runtime source for
 * Stage 1 and a usable fake in tests. The Room and Retrofit sources replace it behind the same
 * interface; nothing in the UI layer changes when they do.
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
                                      RepositoryCallback<List<Attraction>> callback) {
        try {
            callback.onSuccess(NearbyAttractions.within(attractions, origin, radiusMeters));
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
