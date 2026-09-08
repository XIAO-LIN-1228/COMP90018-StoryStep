package com.comp90018.storystep.domain.model;

import com.comp90018.storystep.domain.attraction.AttractionFreshness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A set of attractions together with how current it is.
 *
 * <p>The pair travels together for the same reason {@code WeatherResult} does: a list without its
 * freshness invites a screen to present a month-old cache as a live answer. The nearby screen asks
 * this object both for its rows and for whether it may show them unqualified.
 *
 * <p>Provenance is not a field here. Records from different sources legitimately arrive in one
 * list — the bundled seed set alongside places fetched from a provider — so each
 * {@link Attraction} carries its own, and {@link #provenances()} reports the union for the
 * attribution the licence requires.
 */
public final class AttractionResult {

    private final List<Attraction> attractions;
    private final AttractionFreshness freshness;

    public AttractionResult(List<Attraction> attractions, AttractionFreshness freshness) {
        Objects.requireNonNull(attractions, "attractions");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
        if (freshness == AttractionFreshness.EXPIRED) {
            // An expired set is not a result. Producing one would let a caller display something
            // the policy has already ruled unfit to serve without a refresh.
            throw new IllegalArgumentException("an expired set is not a result");
        }
        this.attractions = Collections.unmodifiableList(new ArrayList<>(attractions));
    }

    /** A live result: fetched during the call that produced it. */
    public static AttractionResult fresh(List<Attraction> attractions) {
        return new AttractionResult(attractions, AttractionFreshness.FRESH);
    }

    public List<Attraction> getAttractions() {
        return attractions;
    }

    public AttractionFreshness getFreshness() {
        return freshness;
    }

    /** True when the caller may present these rows without a cached-content label. */
    public boolean isFresh() {
        return freshness == AttractionFreshness.FRESH;
    }

    public boolean isEmpty() {
        return attractions.isEmpty();
    }

    /**
     * Every source represented in this result.
     *
     * <p>The UI credits each one that {@link AttractionProvenance#requiresAttribution() requires}
     * it. An empty result credits nothing, which is correct: there is nothing displayed to credit.
     */
    public Set<AttractionProvenance> provenances() {
        Set<AttractionProvenance> sources = EnumSet.noneOf(AttractionProvenance.class);
        for (Attraction attraction : attractions) {
            sources.add(attraction.getProvenance());
        }
        return sources;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttractionResult)) {
            return false;
        }
        AttractionResult that = (AttractionResult) other;
        return freshness == that.freshness && attractions.equals(that.attractions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attractions, freshness);
    }

    @Override
    public String toString() {
        return "AttractionResult{" + attractions.size() + " attractions, " + freshness + '}';
    }
}
