package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Spherical-earth geographic calculations.
 *
 * <p>This class is deliberately free of Android framework types so that distance and bearing
 * behaviour can be verified by fast JVM unit tests. UI classes must never re-implement these
 * calculations inline.
 */
public final class GeoCalculator {

    /** Mean earth radius in metres (IUGG). */
    public static final double EARTH_RADIUS_METERS = 6371008.8;

    private GeoCalculator() {
        // Utility class.
    }

    /**
     * Great-circle distance between two points, in metres, using the haversine formula.
     *
     * @return a non-negative distance; {@code 0} for identical coordinates
     */
    public static double distanceMeters(GeoPoint from, GeoPoint to) {
        return distanceMeters(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude());
    }

    /** Great-circle distance between two coordinates, in metres. */
    public static double distanceMeters(double fromLatitude, double fromLongitude,
                                        double toLatitude, double toLongitude) {
        double fromLatRad = Math.toRadians(fromLatitude);
        double toLatRad = Math.toRadians(toLatitude);
        double deltaLatRad = Math.toRadians(toLatitude - fromLatitude);
        double deltaLonRad = Math.toRadians(toLongitude - fromLongitude);

        double sinLat = Math.sin(deltaLatRad / 2.0);
        double sinLon = Math.sin(deltaLonRad / 2.0);
        double a = sinLat * sinLat
                + Math.cos(fromLatRad) * Math.cos(toLatRad) * sinLon * sinLon;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(Math.max(0.0, 1.0 - a)));
        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Initial bearing (forward azimuth) from one point to another, in degrees clockwise from
     * true north, normalized to {@code [0, 360)}.
     *
     * <p>For identical coordinates the bearing is undefined; {@code 0} is returned so callers
     * do not have to special-case it.
     */
    public static double initialBearingDegrees(GeoPoint from, GeoPoint to) {
        return initialBearingDegrees(
                from.getLatitude(), from.getLongitude(),
                to.getLatitude(), to.getLongitude());
    }

    /** Initial bearing between two coordinates, in degrees clockwise from true north. */
    public static double initialBearingDegrees(double fromLatitude, double fromLongitude,
                                               double toLatitude, double toLongitude) {
        if (fromLatitude == toLatitude && fromLongitude == toLongitude) {
            return 0.0;
        }
        double fromLatRad = Math.toRadians(fromLatitude);
        double toLatRad = Math.toRadians(toLatitude);
        double deltaLonRad = Math.toRadians(toLongitude - fromLongitude);

        double y = Math.sin(deltaLonRad) * Math.cos(toLatRad);
        double x = Math.cos(fromLatRad) * Math.sin(toLatRad)
                - Math.sin(fromLatRad) * Math.cos(toLatRad) * Math.cos(deltaLonRad);
        return normalizeDegrees(Math.toDegrees(Math.atan2(y, x)));
    }

    /**
     * Normalizes any angle in degrees into the range {@code [0, 360)}.
     *
     * @return {@code 0} for inputs equivalent to 360 degrees; never negative
     */
    public static double normalizeDegrees(double degrees) {
        if (Double.isNaN(degrees) || Double.isInfinite(degrees)) {
            throw new IllegalArgumentException("degrees must be finite: " + degrees);
        }
        double normalized = degrees % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        // A tiny negative input such as -1e-15 can round back up to exactly 360.0.
        return normalized == 360.0 ? 0.0 : normalized;
    }

    /**
     * Smallest signed difference between two headings, in degrees, within {@code (-180, 180]}.
     * Positive means {@code target} is clockwise from {@code current}.
     */
    public static double relativeBearingDegrees(double currentHeading, double targetBearing) {
        double difference = normalizeDegrees(targetBearing) - normalizeDegrees(currentHeading);
        if (difference > 180.0) {
            difference -= 360.0;
        } else if (difference <= -180.0) {
            difference += 360.0;
        }
        return difference;
    }
}
