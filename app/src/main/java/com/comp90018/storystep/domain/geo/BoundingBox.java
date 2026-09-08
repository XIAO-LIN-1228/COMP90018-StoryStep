package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.Objects;

/**
 * An axis-aligned latitude/longitude rectangle.
 *
 * <p>Exists because SQLite can compare columns but cannot compute a great-circle distance. Where a
 * query needs a coarse geographic bound — deleting the stored rows a refresh is about to replace,
 * for instance — a box is what the database can express, and the exact circular test stays in
 * {@link NearbyAttractions} where it belongs.
 *
 * <p>Deliberately not a general-purpose geometry type. It does not wrap across the antimeridian and
 * it does not contain a pole; {@link #inscribedIn(GeoPoint, double)} refuses to build one that
 * would, rather than returning a box whose comparisons silently mean the opposite of what they say.
 */
public final class BoundingBox {

    private static final double METERS_PER_DEGREE_LATITUDE = 111_320.0;

    private final double minLatitude;
    private final double maxLatitude;
    private final double minLongitude;
    private final double maxLongitude;

    private BoundingBox(double minLatitude, double maxLatitude,
                        double minLongitude, double maxLongitude) {
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
    }

    /**
     * The largest box that fits entirely inside the circle of {@code radiusMeters} around
     * {@code center}.
     *
     * <p>Inscribed rather than enclosing, and that is the whole point of the method. The caller
     * deletes what is inside this box and then re-inserts what the provider returned for the
     * circle. Every point in an inscribed box is inside the circle, so anything deleted was
     * certainly asked for; an enclosing box would reach past the circle's edge and delete records
     * the request never covered, leaving holes at the boundary of every refresh.
     *
     * <p>The longitude extent is widened by the cosine of the latitude, since degrees of longitude
     * shorten towards the poles. Where that correction stops being meaningful — very high
     * latitudes — or where the result would cross the antimeridian, this returns {@code null} and
     * the caller is expected to fall back to a write that deletes nothing.
     *
     * @return the box, or {@code null} when no honest box can be built at this position
     */
    public static BoundingBox inscribedIn(GeoPoint center, double radiusMeters) {
        Objects.requireNonNull(center, "center");
        if (!(radiusMeters > 0.0) || Double.isInfinite(radiusMeters)) {
            throw new IllegalArgumentException("radiusMeters must be positive: " + radiusMeters);
        }
        // Half-side of the square inscribed in a circle of this radius.
        double halfSideMeters = radiusMeters / Math.sqrt(2.0);
        double latitudeDelta = halfSideMeters / METERS_PER_DEGREE_LATITUDE;

        double cosine = Math.cos(Math.toRadians(center.getLatitude()));
        if (cosine < 0.01) {
            return null;
        }
        double longitudeDelta = latitudeDelta / cosine;

        double minLatitude = center.getLatitude() - latitudeDelta;
        double maxLatitude = center.getLatitude() + latitudeDelta;
        double minLongitude = center.getLongitude() - longitudeDelta;
        double maxLongitude = center.getLongitude() + longitudeDelta;
        if (minLatitude < -90.0 || maxLatitude > 90.0
                || minLongitude < -180.0 || maxLongitude > 180.0) {
            return null;
        }
        return new BoundingBox(minLatitude, maxLatitude, minLongitude, maxLongitude);
    }

    public double getMinLatitude() {
        return minLatitude;
    }

    public double getMaxLatitude() {
        return maxLatitude;
    }

    public double getMinLongitude() {
        return minLongitude;
    }

    public double getMaxLongitude() {
        return maxLongitude;
    }

    public boolean contains(GeoPoint point) {
        return point.getLatitude() >= minLatitude
                && point.getLatitude() <= maxLatitude
                && point.getLongitude() >= minLongitude
                && point.getLongitude() <= maxLongitude;
    }

    @Override
    public String toString() {
        return "BoundingBox{" + minLatitude + ".." + maxLatitude
                + ", " + minLongitude + ".." + maxLongitude + '}';
    }
}
