package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSegment;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the one thing this split has to get right: the boundary between two collection spans is
 * time that was never recorded, and nothing may merge across it.
 */
public class RouteSegmentsTest {

    private static final double LATITUDE = -37.79630;
    private static final double LONGITUDE = 144.96140;

    private static RoutePoint point(int segment, long millis) {
        return new RoutePoint(new GeoPoint(LATITUDE, LONGITUDE), 8.0f, millis, segment);
    }

    @Test
    public void anEmptyTraceProducesNoSegments() {
        assertTrue("nothing recorded means nothing to draw",
                RouteSegments.split(Collections.<RoutePoint>emptyList()).isEmpty());
        assertTrue("a null trace is treated as an empty one, not as a failure",
                RouteSegments.split(null).isEmpty());
    }

    @Test
    public void anUninterruptedWalkIsOneSegment() {
        List<RouteSegment> segments = RouteSegments.split(Arrays.asList(
                point(1, 1_000L), point(1, 2_000L), point(1, 3_000L)));

        assertEquals("one collection span is one segment", 1, segments.size());
        assertEquals(1, segments.get(0).getSegment());
        assertEquals(3, segments.get(0).size());
    }

    @Test
    public void aPauseAndResumeSplitsTheTraceAtTheSegmentChange() {
        List<RouteSegment> segments = RouteSegments.split(Arrays.asList(
                point(1, 1_000L), point(1, 2_000L),
                point(2, 9_000L), point(2, 10_000L), point(2, 11_000L)));

        assertEquals("a resumed session records a second span", 2, segments.size());
        assertEquals(1, segments.get(0).getSegment());
        assertEquals(2, segments.get(0).size());
        assertEquals(2, segments.get(1).getSegment());
        assertEquals(3, segments.get(1).size());
    }

    @Test
    public void aSegmentThatRecordedOnlyOnePointSurvives() {
        List<RouteSegment> segments = RouteSegments.split(Arrays.asList(
                point(1, 1_000L), point(2, 5_000L), point(3, 9_000L), point(3, 10_000L)));

        assertEquals(3, segments.size());
        assertEquals("a span with a single fix is still a span, not noise to drop",
                1, segments.get(1).size());
        assertEquals(2, segments.get(1).getSegment());
    }

    @Test
    public void segmentNumbersNeedNotBeContiguous() {
        // A span that produced no accepted fixes leaves a hole in the numbering.
        List<RouteSegment> segments = RouteSegments.split(Arrays.asList(
                point(1, 1_000L), point(4, 20_000L), point(4, 21_000L)));

        assertEquals(2, segments.size());
        assertEquals("the recorded segment number is preserved, not renumbered",
                4, segments.get(1).getSegment());
    }

    @Test
    public void pointsOfTheSameSegmentSeparatedByAnotherAreNotMerged() {
        // Cannot arise from the accumulator, but merging them would bridge exactly the gap this
        // split exists to preserve, so the run-based grouping is asserted directly.
        List<RouteSegment> segments = RouteSegments.split(Arrays.asList(
                point(1, 1_000L), point(2, 2_000L), point(1, 3_000L)));

        assertEquals("runs, not buckets", 3, segments.size());
        assertEquals(1, segments.get(0).getSegment());
        assertEquals(2, segments.get(1).getSegment());
        assertEquals(1, segments.get(2).getSegment());
    }

    @Test
    public void theOrderOfPointsInsideASegmentIsPreserved() {
        List<RoutePoint> trace = new ArrayList<>();
        for (long millis = 1_000L; millis <= 5_000L; millis += 1_000L) {
            trace.add(point(1, millis));
        }

        List<RoutePoint> points = RouteSegments.split(trace).get(0).getPoints();

        for (int index = 0; index < points.size(); index++) {
            assertEquals("the trace must stay in the order it was walked",
                    1_000L * (index + 1), points.get(index).getWallClockMillis());
        }
    }
}
