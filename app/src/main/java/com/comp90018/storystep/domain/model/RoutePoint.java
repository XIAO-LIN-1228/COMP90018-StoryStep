package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * One stored point of a recorded route.
 *
 * <p>Deliberately a stored point rather than a raw fix: the route keeps a sampled trace, not the
 * whole location stream, so an hour of exploring stays bounded in memory and, later, on disk.
 *
 * <p>{@code segment} is the session's collection span the point belongs to. Points from different
 * segments are never joined, because the gap between them is time the user was not tracked and any
 * line drawn across it would be invented travel.
 */
public final class RoutePoint {

    private final GeoPoint position;
    private final float accuracyMeters;
    private final long wallClockMillis;
    private final int segment;

    public RoutePoint(GeoPoint position, float accuracyMeters, long wallClockMillis, int segment) {
        this.position = Objects.requireNonNull(position, "position");
        if (!(accuracyMeters >= 0.0f) || Float.isInfinite(accuracyMeters)) {
            throw new IllegalArgumentException(
                    "accuracyMeters must be finite and not negative: " + accuracyMeters);
        }
        if (segment < 1) {
            throw new IllegalArgumentException("segment must be at least 1: " + segment);
        }
        this.accuracyMeters = accuracyMeters;
        this.wallClockMillis = wallClockMillis;
        this.segment = segment;
    }

    public GeoPoint getPosition() {
        return position;
    }

    public float getAccuracyMeters() {
        return accuracyMeters;
    }

    /** Epoch milliseconds, for display and for later storage; not for measuring intervals. */
    public long getWallClockMillis() {
        return wallClockMillis;
    }

    public int getSegment() {
        return segment;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoutePoint)) {
            return false;
        }
        RoutePoint that = (RoutePoint) other;
        return Float.compare(accuracyMeters, that.accuracyMeters) == 0
                && wallClockMillis == that.wallClockMillis
                && segment == that.segment
                && position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, accuracyMeters, wallClockMillis, segment);
    }

    @Override
    public String toString() {
        return "RoutePoint{" + position + ", +-" + accuracyMeters + "m, segment " + segment + '}';
    }
}
