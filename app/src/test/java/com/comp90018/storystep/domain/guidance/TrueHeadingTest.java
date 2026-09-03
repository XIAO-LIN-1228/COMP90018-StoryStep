package com.comp90018.storystep.domain.guidance;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * The one place magnetic north becomes true north.
 *
 * <p>The first case is the project's documented acceptance example. Getting the sign backwards is
 * the classic failure here, and it is invisible on a device — the arrow simply points slightly
 * wrong — so the sign is pinned down in both directions.
 */
public class TrueHeadingTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    public void theAcceptanceExampleHolds() {
        assertEquals("magnetic 350 with 15 degrees east declination is true 5",
                5.0, TrueHeading.degrees(350.0, 15.0), TOLERANCE);
    }

    @Test
    public void easterlyDeclinationAddsToTheHeading() {
        assertEquals(101.0, TrueHeading.degrees(90.0, 11.0), TOLERANCE);
    }

    @Test
    public void westerlyDeclinationSubtractsFromTheHeading() {
        assertEquals(79.0, TrueHeading.degrees(90.0, -11.0), TOLERANCE);
    }

    @Test
    public void theResultWrapsBelowZero() {
        assertEquals(355.0, TrueHeading.degrees(5.0, -10.0), TOLERANCE);
    }

    @Test
    public void theResultWrapsAboveThreeSixty() {
        assertEquals(10.0, TrueHeading.degrees(355.0, 15.0), TOLERANCE);
    }

    @Test
    public void zeroDeclinationChangesNothing() {
        assertEquals(123.4, TrueHeading.degrees(123.4, 0.0), TOLERANCE);
    }

    @Test
    public void threeSixtyIsReportedAsZero() {
        assertEquals(0.0, TrueHeading.degrees(350.0, 10.0), TOLERANCE);
    }
}
