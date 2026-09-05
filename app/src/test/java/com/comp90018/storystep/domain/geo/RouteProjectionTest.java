package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSegment;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the two ways a route sketch can lie: by changing the shape of the walk, and by joining up
 * two spans that were never connected.
 *
 * <p>Expected values come from the geometry rather than from the class under test. At Melbourne's
 * latitude a degree of longitude covers about {@code cos(37.8 degrees) = 0.79} of what a degree of
 * latitude does, which is what the scaling assertions below are checking for.
 */
public class RouteProjectionTest {

    private static final double LATITUDE = -37.79630;
    private static final double LONGITUDE = 144.96140;
    private static final float TOLERANCE = 0.001f;

    private static RoutePoint point(double latitude, double longitude, int segment) {
        return new RoutePoint(new GeoPoint(latitude, longitude), 8.0f, 1_000L, segment);
    }

    private static RouteSegment segment(int number, RoutePoint... points) {
        return new RouteSegment(number, Arrays.asList(points));
    }

    @Test
    public void anEmptyRouteProjectsToNothing() {
        assertTrue(RouteProjection.project(Collections.<RouteSegment>emptyList()).isEmpty());
        assertTrue("a null route is treated as an empty one, not as a failure",
                RouteProjection.project(null).isEmpty());
    }

    @Test
    public void aSingleFixMapsToTheCentreRatherThanDividingByAZeroSpan() {
        List<RouteProjection.Projected> projected = RouteProjection.project(Collections.singletonList(
                segment(1, point(LATITUDE, LONGITUDE, 1))));

        assertEquals(1, projected.size());
        assertEquals(0.5f, projected.get(0).getXs()[0], TOLERANCE);
        assertEquals(0.5f, projected.get(0).getYs()[0], TOLERANCE);
    }

    @Test
    public void aRouteThatNeverMovedMapsToTheCentre() {
        List<RouteProjection.Projected> projected = RouteProjection.project(Collections.singletonList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE, LONGITUDE, 1))));

        for (int index = 0; index < 3; index++) {
            assertEquals("identical fixes must not blow up the normalisation",
                    0.5f, projected.get(0).getXs()[index], TOLERANCE);
            assertEquals(0.5f, projected.get(0).getYs()[index], TOLERANCE);
        }
    }

    @Test
    public void northIsUp() {
        List<RouteProjection.Projected> projected = RouteProjection.project(Collections.singletonList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE + 0.002, LONGITUDE, 1))));

        float[] ys = projected.get(0).getYs();
        assertTrue("the more northerly fix must be nearer the top of the canvas", ys[1] < ys[0]);
        assertEquals("the southernmost point sits on the bottom edge", 1.0f, ys[0], TOLERANCE);
        assertEquals("the northernmost point sits on the top edge", 0.0f, ys[1], TOLERANCE);
    }

    @Test
    public void aNorthSouthWalkIsDrawnTallAndNarrowRatherThanStretchedToASquare() {
        // Two degrees-of-latitude of height and none of width: every x must land on the centre.
        List<RouteProjection.Projected> projected = RouteProjection.project(Collections.singletonList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE + 0.002, LONGITUDE, 1))));

        float[] xs = projected.get(0).getXs();
        assertEquals("a walk with no east-west extent must not be stretched sideways",
                0.5f, xs[0], TOLERANCE);
        assertEquals(0.5f, xs[1], TOLERANCE);
    }

    @Test
    public void longitudeIsScaledByTheCosineOfTheLatitudeSoTheAspectIsHonest() {
        // Equal degree spans on both axes. On the ground the east-west leg is shorter by
        // cos(latitude), so it must occupy less of the canvas than the north-south one.
        List<RouteProjection.Projected> projected = RouteProjection.project(Collections.singletonList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE + 0.002, LONGITUDE + 0.002, 1))));

        float[] xs = projected.get(0).getXs();
        float[] ys = projected.get(0).getYs();
        float width = Math.abs(xs[1] - xs[0]);
        float height = Math.abs(ys[1] - ys[0]);
        assertTrue("equal degree spans are not equal distances away from the equator",
                width < height);
        double expectedRatio = Math.cos(Math.toRadians(LATITUDE + 0.001));
        assertEquals("the ratio must be the cosine of the route's middle latitude",
                expectedRatio, width / height, 0.01);
    }

    @Test
    public void everySegmentSharesOneFrameSoTheirRelativePositionsSurvive() {
        // The second span happened well north-east of the first. Projected separately, each would
        // fill the canvas and the two would land on top of each other.
        List<RouteProjection.Projected> projected = RouteProjection.project(Arrays.asList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE + 0.0002, LONGITUDE, 1)),
                segment(2,
                        point(LATITUDE + 0.004, LONGITUDE, 2),
                        point(LATITUDE + 0.0042, LONGITUDE, 2))));

        assertEquals(2, projected.size());
        float firstSpanTop = projected.get(0).getYs()[1];
        float secondSpanBottom = projected.get(1).getYs()[0];
        assertTrue("the later, more northerly span must sit above the earlier one",
                secondSpanBottom < firstSpanTop);
    }

    @Test
    public void segmentsStaySeparateArraysSoNothingCanBridgeTheGap() {
        List<RouteProjection.Projected> projected = RouteProjection.project(Arrays.asList(
                segment(1, point(LATITUDE, LONGITUDE, 1), point(LATITUDE + 0.001, LONGITUDE, 1)),
                segment(3, point(LATITUDE + 0.004, LONGITUDE, 3))));

        assertEquals("one projected span per recorded span", 2, projected.size());
        assertEquals("the recorded segment number is carried through", 1,
                projected.get(0).getSegment());
        assertEquals(3, projected.get(1).getSegment());
        assertEquals(2, projected.get(0).size());
        assertEquals("a single-fix span stays a span of one, not a point appended to the previous",
                1, projected.get(1).size());
    }

    @Test
    public void everyCoordinateStaysInsideTheUnitSquare() {
        List<RouteProjection.Projected> projected = RouteProjection.project(Arrays.asList(
                segment(1,
                        point(LATITUDE, LONGITUDE, 1),
                        point(LATITUDE + 0.003, LONGITUDE - 0.002, 1)),
                segment(2,
                        point(LATITUDE + 0.001, LONGITUDE + 0.004, 2))));

        for (RouteProjection.Projected span : projected) {
            for (int index = 0; index < span.size(); index++) {
                float x = span.getXs()[index];
                float y = span.getYs()[index];
                assertTrue("x out of range: " + x, x >= 0.0f && x <= 1.0f);
                assertTrue("y out of range: " + y, y >= 0.0f && y <= 1.0f);
            }
        }
    }
}
