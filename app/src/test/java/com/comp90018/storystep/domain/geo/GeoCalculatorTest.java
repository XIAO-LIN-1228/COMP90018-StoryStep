package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

/** JVM unit tests for the geographic calculations; no Android framework is involved. */
public class GeoCalculatorTest {

    private static final GeoPoint OLD_ARTS = new GeoPoint(-37.79810, 144.95910);
    private static final GeoPoint MELBOURNE_MUSEUM = new GeoPoint(-37.80330, 144.97170);

    @Test
    public void distance_isZeroForIdenticalCoordinates() {
        assertEquals(0.0, GeoCalculator.distanceMeters(OLD_ARTS, OLD_ARTS), 1e-9);
    }

    @Test
    public void distance_matchesKnownSeparation() {
        // Melbourne (-37.8136, 144.9631) to Sydney (-33.8688, 151.2093) is about 713.4 km.
        double distance = GeoCalculator.distanceMeters(
                -37.8136, 144.9631, -33.8688, 151.2093);
        assertEquals(713_400.0, distance, 2_000.0);
    }

    @Test
    public void distance_matchesShortCampusSeparation() {
        // Roughly 1.28 km across the Parkville campus to Carlton Gardens.
        double distance = GeoCalculator.distanceMeters(OLD_ARTS, MELBOURNE_MUSEUM);
        assertEquals(1_280.0, distance, 60.0);
    }

    @Test
    public void distance_isSymmetric() {
        assertEquals(
                GeoCalculator.distanceMeters(OLD_ARTS, MELBOURNE_MUSEUM),
                GeoCalculator.distanceMeters(MELBOURNE_MUSEUM, OLD_ARTS),
                1e-6);
    }

    @Test
    public void distance_handlesAntimeridianCrossing() {
        // 0.02 degrees of longitude either side of the 180th meridian, near the equator.
        double distance = GeoCalculator.distanceMeters(0.0, 179.99, 0.0, -179.99);
        assertEquals(2_225.0, distance, 50.0);
    }

    @Test
    public void bearing_isNorthWhenTargetIsDueNorth() {
        assertEquals(0.0, GeoCalculator.initialBearingDegrees(0.0, 0.0, 1.0, 0.0), 1e-6);
    }

    @Test
    public void bearing_isEastWhenTargetIsDueEast() {
        assertEquals(90.0, GeoCalculator.initialBearingDegrees(0.0, 0.0, 0.0, 1.0), 1e-6);
    }

    @Test
    public void bearing_isSouthWhenTargetIsDueSouth() {
        assertEquals(180.0, GeoCalculator.initialBearingDegrees(1.0, 0.0, 0.0, 0.0), 1e-6);
    }

    @Test
    public void bearing_isWestWhenTargetIsDueWest() {
        assertEquals(270.0, GeoCalculator.initialBearingDegrees(0.0, 1.0, 0.0, 0.0), 1e-6);
    }

    @Test
    public void bearing_staysInRangeWhenCrossingTheAntimeridian() {
        double bearing = GeoCalculator.initialBearingDegrees(0.0, 179.99, 0.0, -179.99);
        assertEquals(90.0, bearing, 1e-3);
    }

    @Test
    public void bearing_wrapsJustBelowNorthInsteadOfGoingNegative() {
        // A target slightly west of due north must read close to 360, never a negative value.
        double bearing = GeoCalculator.initialBearingDegrees(0.0, 0.0, 10.0, -0.0001);
        assertTrue("expected a bearing just below 360 but was " + bearing,
                bearing > 359.0 && bearing < 360.0);
    }

    @Test
    public void bearing_isZeroForIdenticalCoordinates() {
        assertEquals(0.0, GeoCalculator.initialBearingDegrees(OLD_ARTS, OLD_ARTS), 0.0);
    }

    @Test
    public void normalize_keepsValuesAlreadyInRange() {
        assertEquals(0.0, GeoCalculator.normalizeDegrees(0.0), 0.0);
        assertEquals(359.9, GeoCalculator.normalizeDegrees(359.9), 1e-9);
    }

    @Test
    public void normalize_mapsFullTurnToZero() {
        assertEquals(0.0, GeoCalculator.normalizeDegrees(360.0), 0.0);
        assertEquals(0.0, GeoCalculator.normalizeDegrees(720.0), 0.0);
        assertEquals(0.0, GeoCalculator.normalizeDegrees(-360.0), 0.0);
    }

    @Test
    public void normalize_wrapsAroundTheZeroBoundary() {
        assertEquals(359.0, GeoCalculator.normalizeDegrees(-1.0), 1e-9);
        assertEquals(1.0, GeoCalculator.normalizeDegrees(361.0), 1e-9);
        assertEquals(350.0, GeoCalculator.normalizeDegrees(-730.0), 1e-9);
    }

    @Test
    public void normalize_neverReturnsNegativeZeroOrThreeSixty() {
        double normalized = GeoCalculator.normalizeDegrees(-1e-15);
        assertTrue("expected [0, 360) but was " + normalized,
                normalized >= 0.0 && normalized < 360.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void normalize_rejectsNaN() {
        GeoCalculator.normalizeDegrees(Double.NaN);
    }

    @Test
    public void relativeBearing_takesTheShortWayAroundTheBoundary() {
        assertEquals(20.0, GeoCalculator.relativeBearingDegrees(350.0, 10.0), 1e-9);
        assertEquals(-20.0, GeoCalculator.relativeBearingDegrees(10.0, 350.0), 1e-9);
        assertEquals(0.0, GeoCalculator.relativeBearingDegrees(45.0, 45.0), 1e-9);
    }

    @Test
    public void relativeBearing_resolvesTheOppositeHeadingToPositiveHalfTurn() {
        assertEquals(180.0, GeoCalculator.relativeBearingDegrees(0.0, 180.0), 1e-9);
    }
}
