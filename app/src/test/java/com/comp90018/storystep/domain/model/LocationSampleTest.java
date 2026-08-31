package com.comp90018.storystep.domain.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Validation and clock behaviour of a single position observation. */
public class LocationSampleTest {

    private static final GeoPoint POSITION = new GeoPoint(-37.79630, 144.96140);

    private static LocationSample sample(float accuracyMeters) {
        return new LocationSample(POSITION, accuracyMeters, 1_700_000_000_000L, 5_000L,
                LocationOrigin.DEVICE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNotANumberAccuracyIsRejected() {
        sample(Float.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anInfiniteAccuracyIsRejected() {
        sample(Float.POSITIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNegativeAccuracyIsRejected() {
        sample(-1.0f);
    }

    @Test(expected = NullPointerException.class)
    public void aMissingPositionIsRejected() {
        new LocationSample(null, 5.0f, 0L, 0L, LocationOrigin.DEVICE);
    }

    @Test
    public void ageIsMeasuredOnTheMonotonicClock() {
        LocationSample observed = sample(5.0f);

        assertEquals(3_000L, observed.ageMillis(8_000L));
    }

    @Test
    public void aReferenceEarlierThanTheSampleGivesZeroAgeRatherThanANegativeOne() {
        // Clocks can be read slightly out of order across threads; age must never go negative.
        LocationSample observed = sample(5.0f);

        assertEquals(0L, observed.ageMillis(4_000L));
    }

    @Test
    public void injectedSamplesAreDistinguishableFromDeviceFixes() {
        LocationSample injected = new LocationSample(POSITION, 5.0f, 0L, 0L,
                LocationOrigin.INJECTED);

        assertEquals(LocationOrigin.INJECTED, injected.getOrigin());
        assertEquals(LocationOrigin.DEVICE, sample(5.0f).getOrigin());
    }
}