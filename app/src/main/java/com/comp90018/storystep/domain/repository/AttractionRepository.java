package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.List;

/**
 * The only attraction data contract the UI layer may depend on.
 *
 * <p>Stage 1 is served by an in-memory implementation. A later stage can add a Room-backed cache
 * and a Retrofit-backed remote source behind this same interface without touching the UI.
 */
public interface AttractionRepository {

    /**
     * Loads attractions within {@code radiusMeters} of {@code origin}, nearest first.
     *
     * @param origin       the reference position to measure from
     * @param radiusMeters inclusive search radius; must be positive
     */
    void loadNearbyAttractions(GeoPoint origin,
                               double radiusMeters,
                               RepositoryCallback<List<Attraction>> callback);

    /** Loads a single attraction by its identifier. */
    void loadAttraction(String attractionId, RepositoryCallback<Attraction> callback);
}
