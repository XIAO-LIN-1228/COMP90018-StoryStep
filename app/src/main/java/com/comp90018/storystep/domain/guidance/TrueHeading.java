package com.comp90018.storystep.domain.guidance;

import com.comp90018.storystep.domain.geo.GeoCalculator;

/**
 * Converts a magnetic compass heading into a true-north heading.
 *
 * <p>One line of arithmetic, given its own class because it is the join between two things that are
 * otherwise easy to confuse. Every bearing computed from coordinates is relative to true north,
 * while every heading a magnetometer reports is relative to magnetic north; comparing them without
 * this correction produces an arrow that is confidently wrong by the local declination — about 11
 * degrees in Melbourne, and far more elsewhere.
 */
public final class TrueHeading {

    private TrueHeading() {
    }

    /**
     * @param magneticHeadingDegrees heading clockwise from magnetic north
     * @param declinationDegrees     local declination, positive when magnetic north lies east of
     *                               true north
     * @return heading clockwise from true north, in {@code [0, 360)}
     */
    public static double degrees(double magneticHeadingDegrees, double declinationDegrees) {
        return GeoCalculator.normalizeDegrees(magneticHeadingDegrees + declinationDegrees);
    }
}
