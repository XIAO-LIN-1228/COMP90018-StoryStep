package com.comp90018.storystep.domain.guidance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Sector centres, sector boundaries and the wrap at north. */
public class CardinalDirectionTest {

    @Test
    public void everySectorCentreGetsItsOwnLabel() {
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(0.0));
        assertEquals(CardinalDirection.NORTH_EAST, CardinalDirection.fromBearing(45.0));
        assertEquals(CardinalDirection.EAST, CardinalDirection.fromBearing(90.0));
        assertEquals(CardinalDirection.SOUTH_EAST, CardinalDirection.fromBearing(135.0));
        assertEquals(CardinalDirection.SOUTH, CardinalDirection.fromBearing(180.0));
        assertEquals(CardinalDirection.SOUTH_WEST, CardinalDirection.fromBearing(225.0));
        assertEquals(CardinalDirection.WEST, CardinalDirection.fromBearing(270.0));
        assertEquals(CardinalDirection.NORTH_WEST, CardinalDirection.fromBearing(315.0));
    }

    @Test
    public void northSpansTheWrap() {
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(350.0));
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(10.0));
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(359.9));
    }

    @Test
    public void aBearingOnABoundaryTakesTheClockwiseLabel() {
        assertEquals(CardinalDirection.NORTH_EAST, CardinalDirection.fromBearing(22.5));
        assertEquals(CardinalDirection.EAST, CardinalDirection.fromBearing(67.5));
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(337.5));
    }

    @Test
    public void justInsideABoundaryStaysInTheLowerSector() {
        assertEquals(CardinalDirection.NORTH, CardinalDirection.fromBearing(22.4));
        assertEquals(CardinalDirection.NORTH_EAST, CardinalDirection.fromBearing(67.4));
    }

    @Test
    public void bearingsOutsideTheNormalRangeAreAccepted() {
        assertEquals(CardinalDirection.EAST, CardinalDirection.fromBearing(450.0));
        assertEquals(CardinalDirection.WEST, CardinalDirection.fromBearing(-90.0));
    }
}
