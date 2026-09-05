package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link WeatherSource} the test drives by hand.
 *
 * <p>It records every coordinate it was asked about, which is how the caching tests prove a
 * request was <em>not</em> made — a fresh cache hit and a coalesced second caller are
 * indistinguishable from the outside except in this list.
 */
public final class FakeWeatherSource implements WeatherSource {

    /** One coordinate per call, in order. Its size is the request count. */
    public final List<GeoPoint> requested = new ArrayList<>();

    /** Thrown instead of answering, standing in for any transport or payload failure. */
    public WeatherUnavailableException failure;

    /** Condition served on success. */
    public WeatherCondition condition = WeatherCondition.CLEAR;

    /** Temperature served on success. */
    public double temperatureCelsius = 14.0;

    @Override
    public WeatherSnapshot fetch(GeoPoint center, String areaKey, long fetchedAtMillis)
            throws WeatherUnavailableException {
        requested.add(center);
        if (failure != null) {
            throw failure;
        }
        return new WeatherSnapshot(areaKey, condition, temperatureCelsius,
                fetchedAtMillis, fetchedAtMillis, WeatherSnapshot.Provenance.NETWORK);
    }

    public int requestCount() {
        return requested.size();
    }
}
