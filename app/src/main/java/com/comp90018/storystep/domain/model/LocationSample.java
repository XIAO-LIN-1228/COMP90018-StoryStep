package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * One immutable, timestamped position observation.
 *
 * <p>Two clocks are carried deliberately. {@code wallClockMillis} is for display and for records
 * the user reads; {@code monotonicMillis} is the only clock used to measure elapsed time, because
 * wall-clock time can jump backwards when the device syncs or the user changes the time zone. The
 * two must never be mixed in one subtraction, and monotonic values from different boots must never
 * be compared.
 *
 * <p>Plain Java: no {@code android.location.Location} leaks past the platform adapter.
 */
public final class LocationSample {

    private final GeoPoint position;
    private final float accuracyMeters;
    private final long wallClockMillis;
    private final long monotonicMillis;
    private final LocationOrigin origin;

    /**
     * @param position       validated coordinate; must not be null
     * @param accuracyMeters horizontal accuracy radius; must be finite and non-negative
     * @param wallClockMillis observation time for display, in epoch milliseconds
     * @param monotonicMillis observation time on a monotonic clock, for elapsed-time maths
     * @param origin         whether this came from the device or from a fixture
     */
    public LocationSample(GeoPoint position,
                          float accuracyMeters,
                          long wallClockMillis,
                          long monotonicMillis,
                          LocationOrigin origin) {
        this.position = Objects.requireNonNull(position, "position");
        this.origin = Objects.requireNonNull(origin, "origin");
        if (Float.isNaN(accuracyMeters) || Float.isInfinite(accuracyMeters)) {
            throw new IllegalArgumentException("accuracyMeters must be finite: " + accuracyMeters);
        }
        if (accuracyMeters < 0.0f) {
            throw new IllegalArgumentException("accuracyMeters must not be negative: "
                    + accuracyMeters);
        }
        this.accuracyMeters = accuracyMeters;
        this.wallClockMillis = wallClockMillis;
        this.monotonicMillis = monotonicMillis;
    }

    public GeoPoint getPosition() {
        return position;
    }

    /** Horizontal accuracy radius in metres; larger means less certain. */
    public float getAccuracyMeters() {
        return accuracyMeters;
    }

    /** Epoch milliseconds, for display only. */
    public long getWallClockMillis() {
        return wallClockMillis;
    }

    /** Monotonic milliseconds; the only clock valid for measuring an interval. */
    public long getMonotonicMillis() {
        return monotonicMillis;
    }

    public LocationOrigin getOrigin() {
        return origin;
    }

    /**
     * Age of this sample against a monotonic reading from the same boot.
     *
     * @return a non-negative age; {@code 0} when the reference precedes the sample
     */
    public long ageMillis(long nowMonotonicMillis) {
        return Math.max(0L, nowMonotonicMillis - monotonicMillis);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LocationSample)) {
            return false;
        }
        LocationSample that = (LocationSample) other;
        return Float.compare(accuracyMeters, that.accuracyMeters) == 0
                && wallClockMillis == that.wallClockMillis
                && monotonicMillis == that.monotonicMillis
                && position.equals(that.position)
                && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, accuracyMeters, wallClockMillis, monotonicMillis, origin);
    }

    @Override
    public String toString() {
        return "LocationSample{" + position
                + ", accuracy=" + accuracyMeters
                + "m, origin=" + origin + '}';
    }
}
