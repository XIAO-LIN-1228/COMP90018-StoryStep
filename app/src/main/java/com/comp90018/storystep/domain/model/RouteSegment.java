package com.comp90018.storystep.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One unbroken span of a recorded route.
 *
 * <p>A session's trace is not one line. Collection stops when the user pauses, when the app loses
 * permission and when the process dies, and it starts again on the other side — sometimes in a
 * different place entirely. Each span is a segment, and the boundary between two of them is time
 * that was never recorded.
 *
 * <p>This type exists so that boundary survives all the way to the screen. A flat list of points
 * carries the segment number on each point and invites anyone rendering it to simply join them up,
 * which would draw a straight line across the part of the walk the app knows nothing about.
 */
public final class RouteSegment {

    private final int segment;
    private final List<RoutePoint> points;

    public RouteSegment(int segment, List<RoutePoint> points) {
        Objects.requireNonNull(points, "points");
        if (segment < 1) {
            throw new IllegalArgumentException("segment must be at least 1: " + segment);
        }
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a segment must hold at least one point");
        }
        this.segment = segment;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
    }

    public int getSegment() {
        return segment;
    }

    /** The points of this span, oldest first. Never empty. */
    public List<RoutePoint> getPoints() {
        return points;
    }

    public int size() {
        return points.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteSegment)) {
            return false;
        }
        RouteSegment that = (RouteSegment) other;
        return segment == that.segment && points.equals(that.points);
    }

    @Override
    public int hashCode() {
        return Objects.hash(segment, points);
    }

    @Override
    public String toString() {
        return "RouteSegment{segment=" + segment + ", points=" + points.size() + '}';
    }
}
