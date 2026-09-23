package com.comp90018.storystep.domain.plan;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The walk between a plan's stops as it will actually be walked: along streets and paths, with
 * the distance and time that follow from them.
 *
 * <p>A line and two numbers. It carries no instructions and never will - StoryStep points the
 * way and says how far, and turn-by-turn navigation is out of scope.
 *
 * <p>{@link #straight} is what a plan has before a router answers, or when none can: the legs
 * as straight lines, which is what every distance in this app has always been.
 */
public final class PlannedRoute {

    private final List<GeoPoint> path;
    private final double distanceMeters;
    private final long durationMillis;
    private final boolean followsStreets;

    private PlannedRoute(List<GeoPoint> path, double distanceMeters, long durationMillis,
                         boolean followsStreets) {
        this.path = Collections.unmodifiableList(new ArrayList<>(path));
        this.distanceMeters = distanceMeters;
        this.durationMillis = durationMillis;
        this.followsStreets = followsStreets;
    }

    /** A route a router worked out: the real path, distance and walking time. */
    public static PlannedRoute walked(List<GeoPoint> path, double distanceMeters,
                                      long durationMillis) {
        return new PlannedRoute(path, distanceMeters, durationMillis, true);
    }

    /** The plan's own straight legs and estimate, before or instead of a router's answer. */
    public static PlannedRoute straight(WalkPlan plan, GeoPoint origin) {
        return new PlannedRoute(plan.path(origin), plan.distanceMeters(origin),
                plan.durationMillis(origin), false);
    }

    /** The line to draw. */
    public List<GeoPoint> getPath() {
        return path;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    /** Walking time, with a stay at each stop added by the caller's plan. */
    public long getDurationMillis() {
        return durationMillis;
    }

    /** Whether the path follows streets, or is the straight-line fallback. */
    public boolean followsStreets() {
        return followsStreets;
    }
}
