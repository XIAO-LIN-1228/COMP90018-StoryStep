package com.comp90018.storystep.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Coordinate validation.
 *
 * <p>The non-finite cases matter more than they look: {@code NaN} fails every ordering comparison,
 * so a plain range check would let it through and every distance and bearing downstream would
 * silently become {@code NaN}.
 */
public class GeoPointTest {

    @Test(expected = IllegalArgumentException.class)
    public void aNotANumberLatitudeIsRejected() {
        new GeoPoint(Double.NaN, 144.96140);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aNotANumberLongitudeIsRejected() {
        new GeoPoint(-37.79630, Double.NaN);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anInfiniteLatitudeIsRejected() {
        new GeoPoint(Double.POSITIVE_INFINITY, 144.96140);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anInfiniteLongitudeIsRejected() {
        new GeoPoint(-37.79630, Double.NEGATIVE_INFINITY);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aLatitudeBeyondThePoleIsRejected() {
        new GeoPoint(90.1, 0.0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aLongitudeBeyondTheAntimeridianIsRejected() {
        new GeoPoint(0.0, 180.1);
    }

    @Test
    public void theCoordinateExtremesAreValid() {
        assertEquals(90.0, new GeoPoint(90.0, 180.0).getLatitude(), 0.0);
        assertEquals(-180.0, new GeoPoint(-90.0, -180.0).getLongitude(), 0.0);
    }

    @Test
    public void equalityIsByValue() {
        assertEquals(new GeoPoint(-37.79630, 144.96140), new GeoPoint(-37.79630, 144.96140));
        assertNotEquals(new GeoPoint(-37.79630, 144.96140), new GeoPoint(-37.79631, 144.96140));
    }
}