package com.comp90018.storystep.domain.attraction;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.Locale;
import java.util.Objects;

/**
 * The area a fetched set of attractions is cached against.
 *
 * <p>The same reasoning as {@link com.comp90018.storystep.domain.weather.WeatherAreaBucket}: a
 * walking user produces a different coordinate every few seconds, and keying the cache on the fix
 * itself would mean no cache is ever hit. Requests are keyed by a quantised cell, and the
 * coordinate sent to the provider is the cell's centre rather than where the user is standing.
 *
 * <p>The grid is {@value #GRID_DEGREES} degrees — roughly 1.1 km of latitude — which is five times
 * finer than the weather grid. That is not an inconsistency. Weather is the same across a large
 * area, so a coarse cell costs nothing; attractions are not, and a coarse cell would force the
 * request radius up until every query dragged back half a city. The trade-off is set by
 * {@link #requestRadiusMeters(double)}, not by the cell size alone.
 */
public final class AttractionAreaBucket {

    /** Cell size in degrees. Latitude and longitude use the same value; cells are not square. */
    public static final double GRID_DEGREES = 0.01;

    private final int latitudeIndex;
    private final int longitudeIndex;

    private AttractionAreaBucket(int latitudeIndex, int longitudeIndex) {
        this.latitudeIndex = latitudeIndex;
        this.longitudeIndex = longitudeIndex;
    }

    public static AttractionAreaBucket of(GeoPoint point) {
        Objects.requireNonNull(point, "point");
        // floor, not truncation: -0.005 and 0.005 are different cells, but an (int) cast would put
        // both in cell 0 and make the bucket twice as wide across the equator and the meridian.
        return new AttractionAreaBucket(
                (int) Math.floor(point.getLatitude() / GRID_DEGREES),
                (int) Math.floor(point.getLongitude() / GRID_DEGREES));
    }

    /**
     * Stable identifier for this cell, used as the cache primary key.
     *
     * <p>Built from the integer cell indices rather than from formatted coordinates, so it cannot
     * change with locale or rounding.
     */
    public String key() {
        return String.format(Locale.ROOT, "%d:%d", latitudeIndex, longitudeIndex);
    }

    /** The centre of the cell — the coordinate actually sent to the provider. */
    public GeoPoint center() {
        double latitude = (latitudeIndex + 0.5) * GRID_DEGREES;
        double longitude = (longitudeIndex + 0.5) * GRID_DEGREES;
        return new GeoPoint(
                Math.max(-90.0, Math.min(90.0, latitude)),
                wrapLongitude(longitude));
    }

    /**
     * The radius to request around {@link #center()} so that every point the user could be
     * standing at within this cell is covered out to {@code nearbyRadiusMeters}.
     *
     * <p>This is what makes querying the cell centre correct rather than merely cheaper. The user
     * may be at a corner of the cell, which is the cell's half-diagonal away from the centre, so
     * anything within their own radius could be that much further out again. Requesting only
     * {@code nearbyRadiusMeters} from the centre would silently drop attractions that are plainly
     * near the user, and the omission would look like the provider having no data.
     *
     * <p>The returned set is a superset of what the user should see. Exact filtering stays with
     * {@link com.comp90018.storystep.domain.geo.NearbyAttractions#within}, measured from the real
     * position, so the extra records never reach the screen.
     */
    public double requestRadiusMeters(double nearbyRadiusMeters) {
        if (!(nearbyRadiusMeters > 0.0)) {
            throw new IllegalArgumentException(
                    "nearbyRadiusMeters must be positive: " + nearbyRadiusMeters);
        }
        return nearbyRadiusMeters + halfDiagonalMeters();
    }

    /** Distance from the cell centre to its corner, at this cell's latitude. */
    public double halfDiagonalMeters() {
        GeoPoint center = center();
        double half = GRID_DEGREES / 2.0;
        GeoPoint corner = new GeoPoint(
                Math.max(-90.0, Math.min(90.0, center.getLatitude() + half)),
                wrapLongitude(center.getLongitude() + half));
        return GeoCalculator.distanceMeters(center, corner);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttractionAreaBucket)) {
            return false;
        }
        AttractionAreaBucket that = (AttractionAreaBucket) other;
        return latitudeIndex == that.latitudeIndex && longitudeIndex == that.longitudeIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitudeIndex, longitudeIndex);
    }

    @Override
    public String toString() {
        return "AttractionAreaBucket{" + key() + '}';
    }

    private static double wrapLongitude(double longitude) {
        return ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }
}
