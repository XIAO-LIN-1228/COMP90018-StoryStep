package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
            if (origin == null) {
                throw new IllegalArgumentException("origin must not be null");
            }
            if (radiusMeters <= 0.0) {
                throw new IllegalArgumentException("radiusMeters must be positive");
            }
            List<Attraction> nearby = new ArrayList<>();
            for (Attraction attraction : attractions) {
                if (GeoCalculator.distanceMeters(origin, attraction.getLocation())
                        <= radiusMeters) {
                    nearby.add(attraction);
                }
            }
            Collections.sort(nearby, byDistanceFrom(origin));
            callback.onSuccess(Collections.unmodifiableList(nearby));
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

    private static Comparator<Attraction> byDistanceFrom(GeoPoint origin) {
        return new Comparator<Attraction>() {
            @Override
            public int compare(Attraction first, Attraction second) {
                double firstDistance = GeoCalculator.distanceMeters(origin, first.getLocation());
                double secondDistance = GeoCalculator.distanceMeters(origin, second.getLocation());
                int byDistance = Double.compare(firstDistance, secondDistance);
                // Stable, reproducible order when two attractions are equally close.
                return byDistance != 0 ? byDistance : first.getId().compareTo(second.getId());
            }
        };
    }
}
