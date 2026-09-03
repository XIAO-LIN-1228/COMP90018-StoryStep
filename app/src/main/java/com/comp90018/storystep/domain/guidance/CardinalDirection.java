package com.comp90018.storystep.domain.guidance;

import com.comp90018.storystep.domain.geo.GeoCalculator;

/**
 * An eight-point compass label for a bearing.
 *
 * <p>Eight points, not sixteen: the bearing behind the label comes from a consumer GPS fix and a
 * phone magnetometer, and "north-north-east" would imply a precision neither of them has.
 *
 * <p>This is what keeps guidance usable when the compass is missing. A user who cannot be shown an
 * arrow can still be told the museum is north-east of them, which is a true and useful statement
 * that needs only a position.
 */
public enum CardinalDirection {

    NORTH,
    NORTH_EAST,
    EAST,
    SOUTH_EAST,
    SOUTH,
    SOUTH_WEST,
    WEST,
    NORTH_WEST;

    private static final CardinalDirection[] CLOCKWISE = values();

    /** Half the width of one sector: the offset that puts a sector's centre on its label. */
    private static final double HALF_SECTOR_DEGREES = 22.5;

    /**
     * The label for a bearing in degrees clockwise from north.
     *
     * <p>Sector boundaries fall on odd multiples of 22.5 degrees, and a bearing exactly on a
     * boundary takes the clockwise label. The rule matters only for the test that pins it down;
     * either choice is defensible, an undefined one is not.
     */
    public static CardinalDirection fromBearing(double bearingDegrees) {
        double normalized = GeoCalculator.normalizeDegrees(bearingDegrees);
        int sector = (int) Math.floor((normalized + HALF_SECTOR_DEGREES) / 45.0);
        return CLOCKWISE[sector % CLOCKWISE.length];
    }
}
