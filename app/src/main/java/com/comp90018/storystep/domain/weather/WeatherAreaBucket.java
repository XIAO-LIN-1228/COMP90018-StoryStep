package com.comp90018.storystep.domain.weather;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.Locale;
import java.util.Objects;

/**
 * The area a weather observation is cached against.
 *
 * <p>Location updates arrive every few seconds and every one of them is a different coordinate.
 * Weather does not change over the metres between two consecutive fixes, so requests are keyed by
 * a quantised area rather than by the fix itself. Without this, a walking user would issue a fresh
 * request per sample, and no cache would ever be hit.
 *
 * <p>The grid is {@value #GRID_DEGREES} degrees, roughly five kilometres of latitude. That is
 * coarse enough that an hour of walking usually stays in one bucket and fine enough that the
 * observation still describes where the user is. It also limits what the request discloses: the
 * coordinates sent to the provider are the centre of a grid cell, not the user's actual position.
 *
 * <p>Two coordinates either side of a cell boundary land in different buckets even when they are
 * metres apart. That is accepted: the cost is one extra request, not a wrong answer.
 */
public final class WeatherAreaBucket {

    /** Cell size in degrees. Latitude and longitude use the same value; cells are not square. */
    public static final double GRID_DEGREES = 0.05;

    private final int latitudeIndex;
    private final int longitudeIndex;

    private WeatherAreaBucket(int latitudeIndex, int longitudeIndex) {
        this.latitudeIndex = latitudeIndex;
        this.longitudeIndex = longitudeIndex;
    }

    public static WeatherAreaBucket of(GeoPoint point) {
        Objects.requireNonNull(point, "point");
        // floor, not truncation: -0.03 and 0.03 are different cells, but (int) casting would put
        // both in cell 0 and make the bucket twice as wide across the equator and the meridian.
        return new WeatherAreaBucket(
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

    /**
     * The centre of the cell — the coordinate actually sent to the weather provider.
     *
     * <p>Clamped to the valid latitude range, because the cell containing a pole extends past it.
     */
    public GeoPoint center() {
        double latitude = (latitudeIndex + 0.5) * GRID_DEGREES;
        double longitude = (longitudeIndex + 0.5) * GRID_DEGREES;
        return new GeoPoint(
                Math.max(-90.0, Math.min(90.0, latitude)),
                wrapLongitude(longitude));
    }

    private static double wrapLongitude(double longitude) {
        double wrapped = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        return wrapped;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WeatherAreaBucket)) {
            return false;
        }
        WeatherAreaBucket that = (WeatherAreaBucket) other;
        return latitudeIndex == that.latitudeIndex && longitudeIndex == that.longitudeIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitudeIndex, longitudeIndex);
    }

    @Override
    public String toString() {
        return "WeatherAreaBucket{" + key() + '}';
    }
}
