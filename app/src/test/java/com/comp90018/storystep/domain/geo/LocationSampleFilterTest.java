package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;

import org.junit.Before;
import org.junit.Test;

/**
 * Covers the sample-quality matrix with a fake clock, including the cases that are impractical to
 * reproduce outdoors: an out-of-order delivery, a teleporting fix and receiver drift while standing
 * still.
 */
public class LocationSampleFilterTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);
    private static final double METERS_PER_DEGREE_LATITUDE = 111_194.9;
    private static final long T0 = 10_000L;

    private LocationSampleFilter filter;

    @Before
    public void setUp() {
        filter = new LocationSampleFilter(LocationQualityPolicy.defaultPolicy());
    }

    private static GeoPoint northOfOrigin(double meters) {
        return new GeoPoint(
                ORIGIN.getLatitude() + meters / METERS_PER_DEGREE_LATITUDE,
                ORIGIN.getLongitude());
    }

    private static LocationSample sample(GeoPoint position, float accuracy, long monotonicMillis) {
        return new LocationSample(position, accuracy, 1_700_000_000_000L, monotonicMillis,
                LocationOrigin.DEVICE);
    }

    @Test
    public void theFirstUsableFixIsAccepted() {
        LocationSample first = sample(ORIGIN, 12.0f, T0);

        assertEquals(LocationSampleFilter.Decision.ACCEPTED, filter.offer(first, T0));
        assertSame(first, filter.getLastAccepted());
    }

    @Test
    public void theFirstFixIsNotJudgedOnDisplacementOrSpeed() {
        // There is no previous point, so the rules that need two samples must not reject it.
        LocationSample first = sample(northOfOrigin(5_000.0), 5.0f, T0);

        assertEquals(LocationSampleFilter.Decision.ACCEPTED, filter.offer(first, T0));
    }

    @Test
    public void anInaccurateSampleIsRejected() {
        LocationSample poor = sample(ORIGIN, 45.0f, T0);

        assertEquals(LocationSampleFilter.Decision.POOR_ACCURACY, filter.offer(poor, T0));
        assertNull("a rejected sample must not become the reference", filter.getLastAccepted());
    }

    @Test
    public void accuracyExactlyAtTheLimitIsAccepted() {
        LocationSample atLimit = sample(ORIGIN, 30.0f, T0);

        assertEquals(LocationSampleFilter.Decision.ACCEPTED, filter.offer(atLimit, T0));
    }

    @Test
    public void aStaleSampleIsRejected() {
        LocationSample old = sample(ORIGIN, 10.0f, T0);

        // Observed at T0 but evaluated 20 s later, beyond the 15 s freshness window.
        assertEquals(LocationSampleFilter.Decision.STALE, filter.offer(old, T0 + 20_000L));
    }

    @Test
    public void anAgeExactlyAtTheFreshnessLimitIsAccepted() {
        LocationSample edge = sample(ORIGIN, 10.0f, T0);

        assertEquals(LocationSampleFilter.Decision.ACCEPTED, filter.offer(edge, T0 + 15_000L));
    }

    @Test
    public void anOutOfOrderSampleIsRejected() {
        filter.offer(sample(ORIGIN, 10.0f, T0 + 5_000L), T0 + 5_000L);

        LocationSample earlier = sample(northOfOrigin(50.0), 10.0f, T0 + 1_000L);

        assertEquals(LocationSampleFilter.Decision.OUT_OF_ORDER,
                filter.offer(earlier, T0 + 5_000L));
    }

    @Test
    public void aRepeatOfTheSameTimestampIsRejected() {
        filter.offer(sample(ORIGIN, 10.0f, T0), T0);

        assertEquals(LocationSampleFilter.Decision.OUT_OF_ORDER,
                filter.offer(sample(northOfOrigin(50.0), 10.0f, T0), T0));
    }

    @Test
    public void driftWhileStandingStillIsRejected() {
        filter.offer(sample(ORIGIN, 8.0f, T0), T0);

        // 1 m of movement over 3 s: inside the noise floor, so position is unchanged.
        long later = T0 + 3_000L;
        assertEquals(LocationSampleFilter.Decision.BELOW_MIN_DISPLACEMENT,
                filter.offer(sample(northOfOrigin(1.0), 8.0f, later), later));
    }

    @Test
    public void jitterAroundAFixedPointNeverMovesTheReference() {
        LocationSample anchor = sample(ORIGIN, 8.0f, T0);
        filter.offer(anchor, T0);

        // A stationary receiver wandering roughly a metre either side of the true position.
        for (int step = 1; step <= 20; step++) {
            long time = T0 + step * 3_000L;
            double offset = (step % 2 == 0) ? 1.0 : -1.0;
            assertEquals(LocationSampleFilter.Decision.BELOW_MIN_DISPLACEMENT,
                    filter.offer(sample(northOfOrigin(offset), 8.0f, time), time));
        }

        assertSame("the anchor must still be the reference", anchor, filter.getLastAccepted());
    }

    @Test
    public void slowSteadyMovementIsStillRegistered() {
        filter.offer(sample(ORIGIN, 8.0f, T0), T0);

        // 2 m per step is under the noise floor, so each step is rejected on its own. Because a
        // rejection does not move the reference, the accumulated distance from the anchor keeps
        // growing and the walk is eventually recognised. Advancing the reference on a rejection
        // would make this loop reject for ever and freeze a slowly walking user in place.
        LocationSampleFilter.Decision decision = LocationSampleFilter.Decision.ACCEPTED;
        int step;
        for (step = 1; step <= 5; step++) {
            long time = T0 + step * 4_000L;
            decision = filter.offer(sample(northOfOrigin(step * 2.0), 8.0f, time), time);
            if (decision.isAccepted()) {
                break;
            }
        }

        assertEquals(LocationSampleFilter.Decision.ACCEPTED, decision);
        assertEquals("accepted once the anchor is 4 m behind", 2, step);
    }

    @Test
    public void anImpossiblyFastJumpIsRejected() {
        filter.offer(sample(ORIGIN, 10.0f, T0), T0);

        // 500 m in 2 s is 250 m/s; one of the two fixes has to be wrong.
        long later = T0 + 2_000L;
        assertEquals(LocationSampleFilter.Decision.IMPLAUSIBLE_SPEED,
                filter.offer(sample(northOfOrigin(500.0), 10.0f, later), later));
    }

    @Test
    public void ordinaryWalkingIsAccepted() {
        filter.offer(sample(ORIGIN, 10.0f, T0), T0);

        // 10 m in 8 s is 1.25 m/s.
        long later = T0 + 8_000L;
        assertEquals(LocationSampleFilter.Decision.ACCEPTED,
                filter.offer(sample(northOfOrigin(10.0), 10.0f, later), later));
    }

    @Test
    public void resetClearsTheReferenceSoANewSegmentStartsClean() {
        filter.offer(sample(ORIGIN, 10.0f, T0), T0);

        filter.reset();

        assertNull(filter.getLastAccepted());
        // A jump that would have been rejected as implausible is now simply a first fix.
        long later = T0 + 1_000L;
        assertEquals(LocationSampleFilter.Decision.ACCEPTED,
                filter.offer(sample(northOfOrigin(5_000.0), 10.0f, later), later));
    }
}