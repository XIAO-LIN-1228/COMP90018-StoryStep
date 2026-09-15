package com.comp90018.storystep.domain.geo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

public class BoundingBoxTest {

    private static final GeoPoint MELBOURNE = new GeoPoint(-37.7950, 144.9650);

    @Test
    public void everyCornerOfTheBoxIsInsideTheCircle() {
        // The property the caller depends on: it deletes what is in this box and re-inserts what
        // the provider returned for the circle, so nothing may be removed that the request did not
        // cover.
        double radius = 3000.0;
        BoundingBox box = BoundingBox.inscribedIn(MELBOURNE, radius);
        assertNotNull(box);

        double[][] corners = {
                {box.getMinLatitude(), box.getMinLongitude()},
                {box.getMinLatitude(), box.getMaxLongitude()},
                {box.getMaxLatitude(), box.getMinLongitude()},
                {box.getMaxLatitude(), box.getMaxLongitude()},
        };
        for (double[] corner : corners) {
            double metres = GeoCalculator.distanceMeters(
                    MELBOURNE, new GeoPoint(corner[0], corner[1]));
            assertTrue("corner at " + metres + "m must lie within " + radius + "m",
                    metres <= radius);
        }
    }

    @Test
    public void theBoxIsNotSoSmallAsToBeUseless() {
        // Inscribed, not degenerate: it should still cover most of what the circle does.
        BoundingBox box = BoundingBox.inscribedIn(MELBOURNE, 3000.0);

        double halfSide = GeoCalculator.distanceMeters(MELBOURNE,
                new GeoPoint(box.getMaxLatitude(), MELBOURNE.getLongitude()));
        assertTrue("expected roughly radius/sqrt(2), got " + halfSide,
                halfSide > 2000.0 && halfSide < 2200.0);
    }

    @Test
    public void theCentreIsContained() {
        assertTrue(BoundingBox.inscribedIn(MELBOURNE, 1000.0).contains(MELBOURNE));
    }

    @Test
    public void aPointWellOutsideIsNotContained() {
        assertFalse(BoundingBox.inscribedIn(MELBOURNE, 1000.0)
                .contains(new GeoPoint(-37.9000, 145.1000)));
    }

    @Test
    public void aBoxThatWouldCrossTheAntimeridianIsRefusedRatherThanWrapped() {
        // A wrapped box's BETWEEN comparison means the opposite of what it says, so there is no
        // honest box to return here and the caller is expected to skip its delete.
        assertNull(BoundingBox.inscribedIn(new GeoPoint(0.0, 179.999), 50_000.0));
    }

    @Test
    public void aBoxAtAPoleIsRefused() {
        assertNull(BoundingBox.inscribedIn(new GeoPoint(89.999, 0.0), 50_000.0));
    }

    @Test
    public void aNonPositiveRadiusIsRejected() {
        try {
            BoundingBox.inscribedIn(MELBOURNE, 0.0);
            fail("expected rejection of a zero radius");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
