package com.comp90018.storystep.domain.attraction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

public class AttractionAreaBucketTest {

    private static final GeoPoint PARKVILLE = new GeoPoint(-37.79630, 144.96140);

    @Test
    public void twoNearbyPositionsShareACell() {
        // Otherwise every location fix would be a new cache key and a new request.
        assertEquals(AttractionAreaBucket.of(PARKVILLE).key(),
                AttractionAreaBucket.of(new GeoPoint(-37.79700, 144.96200)).key());
    }

    @Test
    public void positionsAKilometreApartFallInDifferentCells() {
        assertNotEquals(AttractionAreaBucket.of(PARKVILLE).key(),
                AttractionAreaBucket.of(new GeoPoint(-37.81000, 144.98000)).key());
    }

    @Test
    public void theCellCentreIsNotTheQueriedPosition() {
        // The privacy property: what leaves the device is the cell, not where the user stands.
        GeoPoint centre = AttractionAreaBucket.of(PARKVILLE).center();

        assertNotEquals(PARKVILLE.getLatitude(), centre.getLatitude(), 1e-9);
        assertNotEquals(PARKVILLE.getLongitude(), centre.getLongitude(), 1e-9);
    }

    @Test
    public void theCentreIsInsideItsOwnCell() {
        AttractionAreaBucket bucket = AttractionAreaBucket.of(PARKVILLE);

        assertEquals(bucket.key(), AttractionAreaBucket.of(bucket.center()).key());
    }

    @Test
    public void negativeAndPositiveCoordinatesNearZeroAreDifferentCells() {
        // floor rather than truncation: an (int) cast would put both in cell 0 and make the bucket
        // twice as wide across the equator and the meridian.
        assertNotEquals(AttractionAreaBucket.of(new GeoPoint(-0.005, 0.005)).key(),
                AttractionAreaBucket.of(new GeoPoint(0.005, 0.005)).key());
    }

    @Test
    public void theRequestRadiusCoversEveryPositionTheUserCouldBeAtInTheCell() {
        AttractionAreaBucket bucket = AttractionAreaBucket.of(PARKVILLE);
        double nearbyRadius = 3000.0;

        double requestRadius = bucket.requestRadiusMeters(nearbyRadius);

        // A user at the far corner of the cell is halfDiagonal from the centre, so a place at
        // their own radius can be that much further out again. Anything less and places plainly
        // near the user would be missing, and the gap would look like the provider having no data.
        double fromCentreToUser = GeoCalculator.distanceMeters(bucket.center(), PARKVILLE);
        assertTrue(requestRadius >= fromCentreToUser + nearbyRadius);
    }

    @Test
    public void theWideningIsBoundedRatherThanArbitrary() {
        // The grid is fine enough that covering the cell costs about a kilometre, not a city.
        double requestRadius = AttractionAreaBucket.of(PARKVILLE).requestRadiusMeters(3000.0);

        assertTrue("expected a modest widening, got " + requestRadius, requestRadius < 4200.0);
    }

    @Test
    public void equalCellsAreEqualValues() {
        assertEquals(AttractionAreaBucket.of(PARKVILLE),
                AttractionAreaBucket.of(new GeoPoint(-37.79700, 144.96200)));
        assertEquals(AttractionAreaBucket.of(PARKVILLE).hashCode(),
                AttractionAreaBucket.of(new GeoPoint(-37.79700, 144.96200)).hashCode());
    }
}
