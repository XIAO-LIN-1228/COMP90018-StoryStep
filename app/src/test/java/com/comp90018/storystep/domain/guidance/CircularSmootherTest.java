package com.comp90018.storystep.domain.guidance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.geo.GeoCalculator;

import org.junit.Test;

/**
 * The wraparound is the whole point of this class, so it is what the tests are mostly about.
 *
 * <p>A smoother that averaged degrees would pass a test that only fed it angles near 180 and fail
 * silently for every user facing north — which is precisely the bug this class exists to prevent.
 */
public class CircularSmootherTest {

    @Test
    public void nothingIsReportedBeforeTheFirstReading() {
        CircularSmoother smoother = new CircularSmoother();

        assertFalse(smoother.hasValue());
        assertNull(smoother.value());
    }

    @Test
    public void theFirstReadingIsAdoptedWhole() {
        CircularSmoother smoother = new CircularSmoother(0.2);

        assertEquals("easing in from an arbitrary start would show a direction never reported",
                42.0, smoother.offer(42.0), 1e-9);
    }

    @Test
    public void smoothingAcrossNorthStaysNearNorth() {
        CircularSmoother smoother = new CircularSmoother(0.5);
        smoother.offer(359.0);

        double result = smoother.offer(1.0);

        double distanceFromNorth = Math.abs(GeoCalculator.relativeBearingDegrees(0.0, result));
        assertTrue("averaging 359 and 1 as numbers gives 180, pointing due south; was " + result,
                distanceFromNorth < 5.0);
    }

    @Test
    public void repeatedReadingsConvergeOnTheirValue() {
        CircularSmoother smoother = new CircularSmoother(0.5);
        smoother.offer(0.0);

        double result = 0.0;
        for (int i = 0; i < 40; i++) {
            result = smoother.offer(90.0);
        }

        assertEquals(90.0, result, 0.5);
    }

    @Test
    public void aSteadyReadingIsReportedUnchanged() {
        CircularSmoother smoother = new CircularSmoother(0.2);
        smoother.offer(270.0);

        assertEquals(270.0, smoother.offer(270.0), 1e-9);
    }

    @Test
    public void anAlphaOfOneFollowsEveryReadingExactly() {
        CircularSmoother smoother = new CircularSmoother(1.0);
        smoother.offer(10.0);

        assertEquals(200.0, smoother.offer(200.0), 1e-9);
    }

    @Test
    public void inputIsNormalizedBeforeSmoothing() {
        CircularSmoother smoother = new CircularSmoother(1.0);

        assertEquals(90.0, smoother.offer(450.0), 1e-9);
        assertEquals(270.0, smoother.offer(-90.0), 1e-9);
    }

    @Test
    public void resetForgetsTheHistory() {
        CircularSmoother smoother = new CircularSmoother(0.2);
        smoother.offer(10.0);

        smoother.reset();

        assertFalse(smoother.hasValue());
        assertEquals("after a reset the next reading is a first reading again",
                200.0, smoother.offer(200.0), 1e-9);
    }

    @Test
    public void theSmoothedValueIsAlwaysNormalized() {
        CircularSmoother smoother = new CircularSmoother(0.3);
        smoother.offer(350.0);

        for (int i = 0; i < 20; i++) {
            double value = smoother.offer(10.0);
            assertTrue("value out of range: " + value, value >= 0.0 && value < 360.0);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void anAlphaOutsideTheUnitRangeIsRejected() {
        new CircularSmoother(0.0);
    }
}
