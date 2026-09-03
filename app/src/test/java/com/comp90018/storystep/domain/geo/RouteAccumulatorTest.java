package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.RoutePoint;

import org.junit.Before;
import org.junit.Test;

/**
 * Covers what makes an accumulated total honest: distance is summed only inside a collection span,
 * and the stored trace is thinned without under-counting the walking it represents.
 *
 * <p>Expected distances are derived from the geometry rather than from
 * {@link GeoCalculator} itself. One degree of latitude is very close to 111,195 m at this radius,
 * so a 0.001-degree step north is about 111 m; the tolerances below are wide enough for that
 * approximation and tight enough to catch a missing or double-counted segment.
 */
public class RouteAccumulatorTest {

    private static final double START_LATITUDE = -37.79630;
    private static final double LONGITUDE = 144.96140;

    /** Metres per 0.001 degrees of latitude, independent of the calculator under test. */
    private static final double STEP_METERS = 111.195;

    private RouteAccumulator accumulator;

    @Before
    public void setUp() {
        accumulator = new RouteAccumulator();
    }

    @Test
    public void aFreshAccumulatorHasNothing() {
        assertEquals(0.0, accumulator.getTotalDistanceMeters(), 0.0);
        assertEquals(0, accumulator.getSegmentCount());
        assertTrue(accumulator.snapshot().getPoints().isEmpty());
    }

    @Test
    public void theFirstFixOfASegmentAddsNoDistance() {
        accumulator.offer(northOf(0, 0L), 1);

        assertEquals("one point cannot describe travel",
                0.0, accumulator.getTotalDistanceMeters(), 0.0);
        assertEquals(1, accumulator.getSegmentCount());
        assertEquals(1, accumulator.snapshot().getPoints().size());
    }

    @Test
    public void twoStepsNorthSumToTheKnownDistance() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(1, 5_000L), 1);
        accumulator.offer(northOf(2, 10_000L), 1);

        assertEquals(2 * STEP_METERS, accumulator.getTotalDistanceMeters(), 1.0);
    }

    @Test
    public void repeatedFixesAtTheSamePlaceAddNothing() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(0, 5_000L), 1);
        accumulator.offer(northOf(0, 10_000L), 1);

        assertEquals("a stationary device must not inflate the route",
                0.0, accumulator.getTotalDistanceMeters(), 0.001);
    }

    @Test
    public void aNewSegmentDoesNotBridgeTheGapWhereTrackingStopped() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(1, 5_000L), 1);

        // The user resumed a long way away; the app was not watching in between.
        accumulator.offer(northOf(50, 600_000L), 2);
        accumulator.offer(northOf(51, 605_000L), 2);

        assertEquals("untracked travel must never be counted as walked",
                2 * STEP_METERS, accumulator.getTotalDistanceMeters(), 1.0);
        assertEquals(2, accumulator.getSegmentCount());
    }

    @Test
    public void aSegmentBoundaryIsRecordedOnItsOwnPoint() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(50, 600_000L), 2);

        assertEquals(RouteAccumulator.Decision.SEGMENT_STARTED,
                accumulator.offer(northOf(51, 605_000L), 3));
        assertEquals(3, accumulator.getSegmentCount());
    }

    @Test
    public void pointsAreStoredAtMostOncePerSamplingInterval() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(1, 1_000L), 1);
        accumulator.offer(northOf(2, 2_000L), 1);
        accumulator.offer(northOf(3, 3_000L), 1);

        assertEquals("four fixes inside five seconds must not become four stored points",
                1, accumulator.snapshot().getPoints().size());
    }

    @Test
    public void thinningTheTraceDoesNotThinTheDistance() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(1, 1_000L), 1);
        accumulator.offer(northOf(2, 2_000L), 1);
        accumulator.offer(northOf(3, 6_000L), 1);

        assertEquals(2, accumulator.snapshot().getPoints().size());
        assertEquals("every accepted fix contributes its metres, stored or not",
                3 * STEP_METERS, accumulator.getTotalDistanceMeters(), 1.5);
    }

    @Test
    public void storedPointsCarryTheirSegment() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(50, 600_000L), 2);

        RoutePoint second = accumulator.snapshot().getPoints().get(1);
        assertEquals(2, second.getSegment());
    }

    @Test
    public void theSnapshotExposesTheLatestFix() {
        LocationSample latest = northOf(2, 10_000L);
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(latest, 1);

        assertEquals(latest, accumulator.snapshot().getLastSample());
        assertTrue(accumulator.snapshot().hasFix());
    }

    @Test
    public void resetClearsEverything() {
        accumulator.offer(northOf(0, 0L), 1);
        accumulator.offer(northOf(1, 5_000L), 1);

        accumulator.reset();

        assertEquals(0.0, accumulator.getTotalDistanceMeters(), 0.0);
        assertEquals(0, accumulator.getSegmentCount());
        assertTrue(accumulator.snapshot().getPoints().isEmpty());
        assertEquals(null, accumulator.snapshot().getLastSample());
    }

    @Test
    public void aSnapshotIsNotAffectedByLaterFixes() {
        accumulator.offer(northOf(0, 0L), 1);
        double takenEarly = accumulator.snapshot().getTotalDistanceMeters();

        accumulator.offer(northOf(1, 5_000L), 1);

        assertEquals(0.0, takenEarly, 0.0);
    }

    /** A fix {@code steps} times 0.001 degrees north of the start, with good accuracy. */
    private static LocationSample northOf(int steps, long monotonicMillis) {
        return new LocationSample(
                new GeoPoint(START_LATITUDE + steps * 0.001, LONGITUDE),
                5.0f,
                1_700_000_000_000L + monotonicMillis,
                monotonicMillis,
                LocationOrigin.DEVICE);
    }
}
