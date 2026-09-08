package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * The only attraction data contract the UI layer may depend on.
 *
 * <p>Implementations range from a synchronous in-memory fixture to a Room cache in front of a real
 * provider. Callers see no difference: results arrive through {@link RepositoryCallback} on the
 * main thread, and the freshness of what came back is carried by {@link AttractionResult} rather
 * than being something a screen has to infer.
 */
public interface AttractionRepository {

    /**
     * Loads attractions within {@code radiusMeters} of {@code origin}, nearest first.
     *
     * <p>The nearest-first ordering is part of the contract and stays that way. Contextual ranking
     * belongs in a separate layer above this one, so that a screen can always fall back to a
     * predictable order.
     *
     * @param origin       the reference position to measure from
     * @param radiusMeters inclusive search radius; must be positive
     */
    void loadNearbyAttractions(GeoPoint origin,
                               double radiusMeters,
                               RepositoryCallback<AttractionResult> callback);

    /** Loads a single attraction by its identifier, from whatever is already stored. */
    void loadAttraction(String attractionId, RepositoryCallback<Attraction> callback);
}
