package com.comp90018.storystep.domain.plan;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A walk planned before it is taken: the places to visit, in the order they will be visited.
 *
 * <p>Immutable and pure, so the arithmetic and the reordering are tested without a screen. The
 * plan is a list of stops and nothing else; what it can say about itself is how far the walk is
 * and roughly how long it will take, both measured along the stops in order from wherever the
 * walker starts.
 *
 * <p>This is a plan, not a route: the distance is the sum of straight lines between consecutive
 * stops, which is what the app's distances have always been. StoryStep does not do turn-by-turn
 * navigation and this class must not grow into it - no street geometry, no instructions, no
 * recalculation when the walker strays.
 */
public final class WalkPlan {

    /** An unhurried walking pace, in metres per second: 4.3 km/h, with looking around. */
    public static final double WALKING_SPEED_MPS = 1.2;

    /** Roughly how long a visitor spends at a stop before moving on. */
    public static final long DWELL_MILLIS_PER_STOP = 5 * 60 * 1000L;

    /** How many stops one walk may hold, so a plan stays a walk rather than an itinerary. */
    public static final int MAX_STOPS = 8;

    public static final WalkPlan EMPTY = new WalkPlan(Collections.<Attraction>emptyList());

    private final List<Attraction> stops;

    private WalkPlan(List<Attraction> stops) {
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
    }

    public static WalkPlan of(List<Attraction> stops) {
        return stops == null || stops.isEmpty() ? EMPTY : new WalkPlan(stops);
    }

    /** The stops, in the order they will be visited. */
    public List<Attraction> getStops() {
        return stops;
    }

    public boolean isEmpty() {
        return stops.isEmpty();
    }

    public int size() {
        return stops.size();
    }

    public boolean isFull() {
        return stops.size() >= MAX_STOPS;
    }

    public boolean contains(String attractionId) {
        return indexOf(attractionId) >= 0;
    }

    public int indexOf(String attractionId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getId().equals(attractionId)) {
                return i;
            }
        }
        return -1;
    }

    /** The first stop, or null for an empty plan: where the walk sets off towards. */
    public Attraction first() {
        return stops.isEmpty() ? null : stops.get(0);
    }

    /** The stop after the given one, or null when it was the last or is not in the plan. */
    public Attraction after(String attractionId) {
        int at = indexOf(attractionId);
        return at < 0 || at + 1 >= stops.size() ? null : stops.get(at + 1);
    }

    /** The same plan with the place appended; unchanged when it is already in or the plan is full. */
    public WalkPlan with(Attraction attraction) {
        Objects.requireNonNull(attraction, "attraction");
        if (isFull() || contains(attraction.getId())) {
            return this;
        }
        List<Attraction> next = new ArrayList<>(stops);
        next.add(attraction);
        return new WalkPlan(next);
    }

    /** The same plan without that place; unchanged when it was not in it. */
    public WalkPlan without(String attractionId) {
        int at = indexOf(attractionId);
        if (at < 0) {
            return this;
        }
        List<Attraction> next = new ArrayList<>(stops);
        next.remove(at);
        return new WalkPlan(next);
    }

    /** The same plan with that stop moved one place earlier or later; unchanged at either end. */
    public WalkPlan moved(String attractionId, int by) {
        int at = indexOf(attractionId);
        int to = at + by;
        if (at < 0 || to < 0 || to >= stops.size() || by == 0) {
            return this;
        }
        List<Attraction> next = new ArrayList<>(stops);
        next.add(to, next.remove(at));
        return new WalkPlan(next);
    }

    /**
     * The stops reordered as a nearest-neighbour walk from the origin: at each step, whichever
     * remaining stop is closest. Not the shortest possible order - that is a travelling-salesman
     * problem and no walker would notice the difference between it and this - but reliably
     * better than the order the stops happened to be chosen in.
     */
    public WalkPlan orderedFrom(GeoPoint origin) {
        if (origin == null || stops.size() < 3) {
            return this;
        }
        List<Attraction> remaining = new ArrayList<>(stops);
        List<Attraction> ordered = new ArrayList<>(stops.size());
        GeoPoint at = origin;
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int i = 0; i < remaining.size(); i++) {
                double distance = GeoCalculator.distanceMeters(at, remaining.get(i).getLocation());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
            Attraction next = remaining.remove(best);
            ordered.add(next);
            at = next.getLocation();
        }
        return new WalkPlan(ordered);
    }

    /**
     * How far the walk is, in metres, from the origin through every stop in order. Straight
     * lines, so the real walk is longer; the figure is labelled as approximate wherever shown.
     */
    public double distanceMeters(GeoPoint origin) {
        double total = 0;
        GeoPoint at = origin;
        for (Attraction stop : stops) {
            if (at != null) {
                total += GeoCalculator.distanceMeters(at, stop.getLocation());
            }
            at = stop.getLocation();
        }
        return total;
    }

    /** Roughly how long the walk takes: the walking at {@link #WALKING_SPEED_MPS}, plus a stay at each stop. */
    public long durationMillis(GeoPoint origin) {
        if (stops.isEmpty()) {
            return 0;
        }
        return walkingMillis(origin) + DWELL_MILLIS_PER_STOP * stops.size();
    }

    /**
     * The walking alone, at {@link #WALKING_SPEED_MPS} over the straight legs, with no stay. For
     * callers that add their own stay time, so the default one is not counted twice.
     */
    public long walkingMillis(GeoPoint origin) {
        if (stops.isEmpty()) {
            return 0;
        }
        return Math.round(distanceMeters(origin) / WALKING_SPEED_MPS * 1000.0);
    }

    /** The stops' positions, in order: what the map draws a line through. */
    public List<GeoPoint> path(GeoPoint origin) {
        List<GeoPoint> path = new ArrayList<>(stops.size() + 1);
        if (origin != null) {
            path.add(origin);
        }
        for (Attraction stop : stops) {
            path.add(stop.getLocation());
        }
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WalkPlan)) {
            return false;
        }
        return stops.equals(((WalkPlan) other).stops);
    }

    @Override
    public int hashCode() {
        return stops.hashCode();
    }
}
