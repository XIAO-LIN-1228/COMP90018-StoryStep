package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An {@link AttractionSource} the test drives by hand.
 *
 * <p>It records every coordinate it was asked about, which is how the caching tests prove a
 * request was <em>not</em> made — a fresh cache hit and a coalesced second caller are
 * indistinguishable from the outside except in this list.
 */
public final class FakeAttractionSource implements AttractionSource {

    /** One coordinate per call, in order. Its size is the request count. */
    public final List<GeoPoint> requested = new ArrayList<>();

    /** The radius asked for on each call, in order. */
    public final List<Double> requestedRadii = new ArrayList<>();

    /** Thrown instead of answering, standing in for any transport or payload failure. */
    public AttractionUnavailableException failure;

    /** Served on success. Empty is a valid answer meaning the provider found nothing here. */
    public List<Attraction> results = Collections.emptyList();

    @Override
    public List<Attraction> fetchNearby(GeoPoint center, double radiusMeters, int limit)
            throws AttractionUnavailableException {
        requested.add(center);
        requestedRadii.add(radiusMeters);
        if (failure != null) {
            throw failure;
        }
        return new ArrayList<>(results);
    }

    public int requestCount() {
        return requested.size();
    }
}
