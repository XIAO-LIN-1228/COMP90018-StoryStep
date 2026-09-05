package com.comp90018.storystep.domain.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

/**
 * The grid that keeps a walking user from issuing a weather request per location fix.
 *
 * <p>The behaviour worth pinning is that ordinary movement stays in one cell — that is the whole
 * reason the bucket exists — and that the coordinate actually sent to the provider is the cell
 * centre rather than the position asked about.
 */
public class WeatherAreaBucketTest {

    /** Roughly the middle of the demonstration area. */
    private static final GeoPoint MELBOURNE = new GeoPoint(-37.80320, 144.97170);

    @Test
    public void nearbyPositionsShareABucket() {
        // About 300 m apart: two consecutive fixes from a walking user.
        GeoPoint afterAFewMinutes = new GeoPoint(-37.80050, 144.97400);

        assertEquals(WeatherAreaBucket.of(MELBOURNE), WeatherAreaBucket.of(afterAFewMinutes));
        assertEquals(WeatherAreaBucket.of(MELBOURNE).key(),
                WeatherAreaBucket.of(afterAFewMinutes).key());
    }

    @Test
    public void distantPositionsDoNotShareABucket() {
        GeoPoint sydney = new GeoPoint(-33.86880, 151.20930);

        assertNotEquals(WeatherAreaBucket.of(MELBOURNE), WeatherAreaBucket.of(sydney));
    }

    @Test
    public void theCentreIsSentToTheProvider() {
        GeoPoint centre = WeatherAreaBucket.of(MELBOURNE).center();

        // Never the position that was asked about: the request must not carry the user's own fix.
        assertNotEquals(MELBOURNE, centre);
        // But it must describe the same place, so it lies within half a cell on each axis.
        assertTrue(Math.abs(centre.getLatitude() - MELBOURNE.getLatitude())
                <= WeatherAreaBucket.GRID_DEGREES);
        assertTrue(Math.abs(centre.getLongitude() - MELBOURNE.getLongitude())
                <= WeatherAreaBucket.GRID_DEGREES);
    }

    @Test
    public void theCentreOfACellBucketsBackToThatCell() {
        WeatherAreaBucket original = WeatherAreaBucket.of(MELBOURNE);

        assertEquals(original, WeatherAreaBucket.of(original.center()));
    }

    @Test
    public void negativeAndPositiveCoordinatesUseSeparateCells() {
        // Truncation towards zero would put both of these in cell 0 and make the cell straddling
        // the equator twice as tall as every other one.
        WeatherAreaBucket justSouth = WeatherAreaBucket.of(new GeoPoint(-0.01, 0.0));
        WeatherAreaBucket justNorth = WeatherAreaBucket.of(new GeoPoint(0.01, 0.0));

        assertNotEquals(justSouth, justNorth);
    }

    @Test
    public void aCellBoundaryStartsANewCell() {
        WeatherAreaBucket below = WeatherAreaBucket.of(new GeoPoint(-37.80001, 144.97170));
        WeatherAreaBucket atBoundary = WeatherAreaBucket.of(new GeoPoint(-37.79999, 144.97170));

        // -37.8 is an exact multiple of the 0.05 grid, so the two sides are different cells.
        // The cost is one extra request for a user standing on the line, never a wrong answer.
        assertNotEquals(below, atBoundary);
    }

    @Test
    public void extremeCoordinatesStillProduceAValidCentre() {
        // The cell containing a pole extends past it, and the one at the date line wraps. Both
        // centres have to stay inside the coordinate ranges GeoPoint accepts.
        GeoPoint northPole = WeatherAreaBucket.of(new GeoPoint(90.0, 180.0)).center();
        GeoPoint southPole = WeatherAreaBucket.of(new GeoPoint(-90.0, -180.0)).center();

        assertTrue(northPole.getLatitude() <= 90.0);
        assertTrue(southPole.getLatitude() >= -90.0);
        assertTrue(northPole.getLongitude() >= -180.0 && northPole.getLongitude() <= 180.0);
        assertTrue(southPole.getLongitude() >= -180.0 && southPole.getLongitude() <= 180.0);
    }

    @Test
    public void theKeyIsStableAcrossCalls() {
        assertEquals(WeatherAreaBucket.of(MELBOURNE).key(), WeatherAreaBucket.of(MELBOURNE).key());
    }
}
