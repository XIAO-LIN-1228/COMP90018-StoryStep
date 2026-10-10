package com.comp90018.storystep.domain.guidance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

/**
 * The degradation policy: which inputs may be missing, and what stays true when they are.
 *
 * <p>Positions are built by stepping a known number of degrees from one origin, so the expected
 * bearing follows from the geometry and is not read back out of the calculator under test.
 */
public class DirectionGuidanceTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    /** Small enough that the meridian convergence over the step stays well inside a degree. */
    private static final double STEP = 0.001;

    @Test
    public void aTargetDueNorthBearsNorth() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, north(), null);

        assertTrue(guidance.hasBearing());
        assertEquals(0.0, guidance.getTargetBearingDegrees(), 0.5);
        assertEquals(CardinalDirection.NORTH, guidance.getCardinal());
    }

    @Test
    public void aTargetDueEastBearsEast() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, east(), null);

        assertEquals(90.0, guidance.getTargetBearingDegrees(), 0.5);
        assertEquals(CardinalDirection.EAST, guidance.getCardinal());
    }

    @Test
    public void aTargetToTheNorthEastBearsNorthEast() {
        GeoPoint target = new GeoPoint(
                ORIGIN.getLatitude() + STEP, ORIGIN.getLongitude() + STEP * 1.27);

        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, target, null);

        assertEquals(CardinalDirection.NORTH_EAST, guidance.getCardinal());
    }

    // --- with a heading ------------------------------------------------------------------------

    @Test
    public void facingTheTargetMeansNoTurn() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, north(), 0.0);

        assertTrue(guidance.hasRelativeBearing());
        assertEquals(0.0, guidance.getRelativeBearingDegrees(), 0.5);
    }

    @Test
    public void aTargetToTheRightIsAClockwiseTurn() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, east(), 0.0);

        assertTrue("a positive turn is clockwise, which is what rotates the arrow right",
                guidance.getRelativeBearingDegrees() > 0.0);
        assertEquals(90.0, guidance.getRelativeBearingDegrees(), 0.5);
    }

    @Test
    public void aTargetToTheLeftIsAnAnticlockwiseTurn() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, east(), 180.0);

        assertEquals(-90.0, guidance.getRelativeBearingDegrees(), 0.5);
    }

    @Test
    public void theTurnIsTheShortWayRoundAcrossNorth() {
        // Facing 350, target due north: ten degrees right, not three hundred and fifty left.
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, north(), 350.0);

        assertEquals(10.0, guidance.getRelativeBearingDegrees(), 0.5);
    }

    // --- degradation ---------------------------------------------------------------------------

    @Test
    public void withoutAHeadingTheBearingSurvives() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, east(), null);

        assertFalse("no compass means no arrow", guidance.hasRelativeBearing());
        assertTrue("but the direction to the place is still known", guidance.hasBearing());
        assertEquals(CardinalDirection.EAST, guidance.getCardinal());
    }

    @Test
    public void withoutAPositionNothingIsClaimed() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(null, east(), 0.0);

        assertFalse(guidance.hasBearing());
        assertFalse(guidance.hasRelativeBearing());
        assertNull(guidance.getCardinal());
    }

    @Test
    public void withoutADestinationTheDeviceCompassStillWorks() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, null, 90.0);

        assertFalse(guidance.hasBearing());
        assertNull(guidance.getCardinal());
        assertFalse(guidance.hasRelativeBearing());
        assertTrue(guidance.hasHeading());
        assertEquals(90.0, guidance.getHeadingDegrees(), 0.0);
    }

    @Test
    public void standingOnTheTargetPointsNowhere() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, ORIGIN, 0.0);

        assertFalse("every direction is equally correct, so none may be shown",
                guidance.hasBearing());
        assertFalse(guidance.hasRelativeBearing());
        assertTrue("arrival removes the target arrow, not the device compass", guidance.hasHeading());
    }

    @Test
    public void anUnavailableSnapshotReadsAsZeroWithoutClaimingIt() {
        GuidanceSnapshot guidance = GuidanceSnapshot.unavailable();

        assertFalse(guidance.hasBearing());
        assertFalse(guidance.hasHeading());
        assertEquals("callers that ignore the flag get a harmless value, not a plausible lie",
                0.0, guidance.getRelativeBearingDegrees(), 0.0);
    }

    @Test
    public void headingIsIndependentOfTheTargetBearingAndWrapsAtNorth() {
        GuidanceSnapshot guidance = DirectionGuidance.compute(ORIGIN, north(), 350.0);
        assertEquals(350.0, guidance.getHeadingDegrees(), 0.001);
        assertEquals(10.0, guidance.getRelativeBearingDegrees(), 0.001);
        assertEquals(0.0, guidance.getTargetBearingDegrees(), 0.001);
        assertFalse(DirectionGuidance.compute(null, north(), 350.0).hasHeading());
        assertFalse(DirectionGuidance.compute(ORIGIN, north(), null).hasHeading());
    }

    private static GeoPoint north() {
        return new GeoPoint(ORIGIN.getLatitude() + STEP, ORIGIN.getLongitude());
    }

    private static GeoPoint east() {
        return new GeoPoint(ORIGIN.getLatitude(), ORIGIN.getLongitude() + STEP);
    }
}
