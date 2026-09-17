package com.comp90018.storystep.domain.attraction;

import com.comp90018.storystep.domain.model.IndoorClassification;

import java.util.Locale;
import java.util.Objects;

/**
 * The nearby list's filter: a venue choice, an unvisited-only switch and a free-text query.
 *
 * <p>Pure and immutable so the chips and the search field can be tested without a screen. The
 * venue filter is strict about the tri-state classification: {@link IndoorClassification#UNKNOWN}
 * matches neither Indoor nor Outdoor, because a place the provider never described is not a place
 * that was described as either, and a filter is read as a statement about the place.
 */
public final class AttractionFilter {

    public enum Venue { ALL, INDOOR, OUTDOOR }

    public static final AttractionFilter NONE = new AttractionFilter(Venue.ALL, false, "");

    private final Venue venue;
    private final boolean unvisitedOnly;
    private final String query;

    public AttractionFilter(Venue venue, boolean unvisitedOnly, String query) {
        this.venue = Objects.requireNonNull(venue, "venue");
        this.unvisitedOnly = unvisitedOnly;
        this.query = query == null ? "" : query.trim();
    }

    public AttractionFilter withVenue(Venue newVenue) {
        return new AttractionFilter(newVenue, unvisitedOnly, query);
    }

    public AttractionFilter withUnvisitedOnly(boolean only) {
        return new AttractionFilter(venue, only, query);
    }

    public AttractionFilter withQuery(String newQuery) {
        return new AttractionFilter(venue, unvisitedOnly, newQuery);
    }

    public Venue getVenue() {
        return venue;
    }

    public boolean isUnvisitedOnly() {
        return unvisitedOnly;
    }

    public String getQuery() {
        return query;
    }

    public boolean isActive() {
        return venue != Venue.ALL || unvisitedOnly || !query.isEmpty();
    }

    /** Whether a place with these facts passes the filter. */
    public boolean matches(String name, IndoorClassification indoor, boolean visited) {
        if (venue == Venue.INDOOR && indoor != IndoorClassification.INDOOR) {
            return false;
        }
        if (venue == Venue.OUTDOOR && indoor != IndoorClassification.OUTDOOR) {
            return false;
        }
        if (unvisitedOnly && visited) {
            return false;
        }
        if (!query.isEmpty()) {
            String haystack = name == null ? "" : name.toLowerCase(Locale.ROOT);
            return haystack.contains(query.toLowerCase(Locale.ROOT));
        }
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttractionFilter)) {
            return false;
        }
        AttractionFilter that = (AttractionFilter) other;
        return venue == that.venue
                && unvisitedOnly == that.unvisitedOnly
                && query.equals(that.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(venue, unvisitedOnly, query);
    }
}
