package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * An immutable WGS-84 coordinate. Plain Java: no Android framework dependency, so it can be
 * used from JVM unit tests.
 */
public final class GeoPoint {

    private final double latitude;
    private final double longitude;

    public GeoPoint(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude out of range: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude out of range: " + longitude);
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoPoint)) {
            return false;
        }
        GeoPoint that = (GeoPoint) other;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return "GeoPoint{" + latitude + ", " + longitude + '}';
    }
}
