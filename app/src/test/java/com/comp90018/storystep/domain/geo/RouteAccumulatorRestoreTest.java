package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Reloading a stored route into a fresh accumulator.
 *
 * <p>The point of these cases is what must <b>not</b> come back: the last position. Distance
 * across the interruption is travel nobody observed, and counting it would inflate the total by
 * however far the user happened to move while the app was closed.
 */
public class RouteAccumulatorRestoreTest {

    private RouteAccumulator route;

    @Before
    public void setUp() {
        route = new RouteAccumulator();
    }

    @Test
    public void theStoredTraceAndTotalsComeBack() {
        route.restore(Arrays.asList(point(-37.7980, 144.9591, 1), point(-37.7981, 144.9591, 1)),
                123.5, 2);

        RouteSnapshot snapshot = route.snapshot();
        assertEquals(2, snapshot.getPoints().size());
        assertEquals(123.5, snapshot.getTotalDistanceMeters(), 0.001);
        assertEquals(2, snapshot.getSegmentCount());
    }

    @Test
    public void aRestoredRouteHasNoCurrentPosition() {
        route.restore(Collections.singletonList(point(-37.7980, 144.9591, 1)), 50.0, 1);

        assertNull("a stored point is not a live fix", route.snapshot().getLastSample());
    }

    @Test
    public void theGapAcrossTheInterruptionAddsNoDistance() {
        route.restore(Collections.singletonList(point(-37.7980, 144.9591, 1)), 50.0, 1);

        // Resuming opens segment 2; the first fix lands roughly a kilometre from the stored point.
        route.offer(sample(-37.8070, 144.9591, 1_000L), 2);

        assertEquals("only the stored total, with nothing invented for the gap",
                50.0, route.snapshot().getTotalDistanceMeters(), 0.001);
    }

    @Test
    public void travelAfterTheResumeAccumulatesFromTheFirstNewFix() {
        route.restore(Collections.singletonList(point(-37.7980, 144.9591, 1)), 50.0, 1);
        route.offer(sample(-37.7980, 144.9591, 1_000L), 2);

        // Roughly 11.1 m north: one ten-thousandth of a degree of latitude.
        route.offer(sample(-37.7979, 144.9591, 7_000L), 2);

        assertEquals(61.1, route.snapshot().getTotalDistanceMeters(), 0.5);
        assertEquals(2, route.snapshot().getSegmentCount());
    }

    @Test
    public void restoringAnEmptyRouteLeavesTheAccumulatorAtZero() {
        route.restore(Collections.<RoutePoint>emptyList(), 0.0, 0);

        assertEquals(0.0, route.snapshot().getTotalDistanceMeters(), 0.001);
        assertEquals(0, route.snapshot().getSegmentCount());
        assertNull(route.snapshot().getLastSample());
    }

    @Test
    public void restoringReplacesAnythingTheAccumulatorAlreadyHeld() {
        route.offer(sample(-37.7980, 144.9591, 1_000L), 1);

        route.restore(Collections.<RoutePoint>emptyList(), 0.0, 0);

        assertEquals(0, route.snapshot().getPoints().size());
        assertNull(route.snapshot().getLastSample());
    }

    private static RoutePoint point(double latitude, double longitude, int segment) {
        return new RoutePoint(new GeoPoint(latitude, longitude), 8.0f, 1_000L, segment);
    }

    private static LocationSample sample(double latitude, double longitude, long monotonicMillis) {
        return new LocationSample(new GeoPoint(latitude, longitude), 8.0f, monotonicMillis,
                monotonicMillis, LocationOrigin.DEVICE);
    }
}
