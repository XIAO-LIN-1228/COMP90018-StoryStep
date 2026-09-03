package com.comp90018.storystep.domain.guidance;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Turns a position, a destination and an optional heading into what the dashboard shows.
 *
 * <p>The angular arithmetic itself is {@link GeoCalculator}'s, which already has the bearing and
 * relative-bearing formulas and their tests. What lives here is the degradation policy: which of
 * the three inputs may be missing, and what remains true when it is.
 */
public final class DirectionGuidance {

    private DirectionGuidance() {
    }

    /**
     * @param from         the user's current position, or null when there is no usable fix
     * @param target       the selected destination, or null when none is chosen
     * @param trueHeading  device heading clockwise from true north, or null when the compass is
     *                     missing, unreliable or stale
     */
    public static GuidanceSnapshot compute(GeoPoint from, GeoPoint target, Double trueHeading) {
        if (from == null || target == null) {
            return GuidanceSnapshot.unavailable();
        }
        if (from.equals(target)) {
            // Standing on the destination: every direction is equally correct, so pointing anywhere
            // would be arbitrary. The distance readout already tells the user they have arrived.
            return GuidanceSnapshot.unavailable();
        }
        double bearing = GeoCalculator.initialBearingDegrees(from, target);
        if (trueHeading == null) {
            return GuidanceSnapshot.bearingOnly(bearing);
        }
        return GuidanceSnapshot.withHeading(bearing,
                GeoCalculator.relativeBearingDegrees(trueHeading, bearing));
    }
}
