package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Selects and orders the attractions within a radius of an origin.
 *
 * <p>Extracted so the in-memory and Room-backed repositories cannot drift apart. The radius
 * boundary is inclusive and the ordering is nearest-first with a stable tie-break — behaviour the
 * nearby list asserts against — and having two copies of it would be two chances for one of them
 * to quietly stop matching the tests.
 *
 * <p>Pure Java: it decides nothing about where the candidates came from.
 */
public final class NearbyAttractions {

    private NearbyAttractions() {
        // Utility class.
    }

    /**
     * @param candidates   every attraction to consider
     * @param origin       the reference position to measure from
     * @param radiusMeters inclusive search radius; must be positive
     * @return an unmodifiable nearest-first list
     */
    public static List<Attraction> within(List<Attraction> candidates,
                                          GeoPoint origin,
                                          double radiusMeters) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("origin must not be null");
        }
        if (radiusMeters <= 0.0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }
        List<Attraction> nearby = new ArrayList<>();
        for (Attraction attraction : candidates) {
            if (GeoCalculator.distanceMeters(origin, attraction.getLocation()) <= radiusMeters) {
                nearby.add(attraction);
            }
        }
        Collections.sort(nearby, byDistanceFrom(origin));
        return Collections.unmodifiableList(nearby);
    }

    /** Nearest first, falling back to the stable id so equally close places keep one order. */
    public static Comparator<Attraction> byDistanceFrom(GeoPoint origin) {
        return new Comparator<Attraction>() {
            @Override
            public int compare(Attraction first, Attraction second) {
                double firstDistance = GeoCalculator.distanceMeters(origin, first.getLocation());
                double secondDistance = GeoCalculator.distanceMeters(origin, second.getLocation());
                int byDistance = Double.compare(firstDistance, secondDistance);
                return byDistance != 0 ? byDistance : first.getId().compareTo(second.getId());
            }
        };
    }
}
