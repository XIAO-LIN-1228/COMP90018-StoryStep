package com.comp90018.storystep.domain.model;

import java.util.Collections;
import java.util.List;

/**
 * An immutable view of what a session has travelled so far, safe to hand to a screen.
 *
 * <p>{@code lastSample} may be null: a session that has started but not yet produced an accepted
 * fix has a real total of zero and no known position. A screen must render that as "waiting for a
 * fix", never as a position of (0, 0).
 */
public final class RouteSnapshot {

    private static final RouteSnapshot EMPTY =
            new RouteSnapshot(0.0, Collections.<RoutePoint>emptyList(), 0, null);

    private final double totalDistanceMeters;
    private final List<RoutePoint> points;
    private final int segmentCount;
    private final LocationSample lastSample;

    public RouteSnapshot(double totalDistanceMeters,
                         List<RoutePoint> points,
                         int segmentCount,
                         LocationSample lastSample) {
        if (!(totalDistanceMeters >= 0.0) || Double.isInfinite(totalDistanceMeters)) {
            throw new IllegalArgumentException(
                    "totalDistanceMeters must be finite and not negative: " + totalDistanceMeters);
        }
        if (segmentCount < 0) {
            throw new IllegalArgumentException("segmentCount must not be negative: " + segmentCount);
        }
        this.totalDistanceMeters = totalDistanceMeters;
        this.points = Collections.unmodifiableList(new java.util.ArrayList<>(points));
        this.segmentCount = segmentCount;
        this.lastSample = lastSample;
    }

    /** The state before a session has recorded anything. */
    public static RouteSnapshot empty() {
        return EMPTY;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    /** The sampled trace, oldest first. Never null; unmodifiable. */
    public List<RoutePoint> getPoints() {
        return points;
    }

    /** How many collection spans contributed points; one more than the number of resumes. */
    public int getSegmentCount() {
        return segmentCount;
    }

    /** The most recent accepted fix, or null when none has arrived yet. */
    public LocationSample getLastSample() {
        return lastSample;
    }

    public boolean hasFix() {
        return lastSample != null;
    }

    @Override
    public String toString() {
        return "RouteSnapshot{" + totalDistanceMeters + "m, " + points.size() + " points, "
                + segmentCount + " segments}";
    }
}
