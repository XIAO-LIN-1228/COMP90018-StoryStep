package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * An unfinished session read back from storage, with everything needed to present it.
 *
 * <p>One object rather than three separate store lookups: the summary, the route and the chosen
 * destination are only meaningful together, and fetching them one at a time would let a screen
 * render a session whose distance had not arrived yet.
 *
 * <p>The destination is a stored <b>snapshot</b>, not a reference resolved against the attraction
 * cache. A place the user was walking towards must still be nameable after the remote record that
 * described it has been refreshed away.
 */
public final class RestoredSession {

    private final SessionSummary summary;
    private final RouteSnapshot route;
    private final Attraction destination;

    /**
     * @param summary     the stored session; never null and never {@code COMPLETED}
     * @param route       the stored trace and totals; use {@link RouteSnapshot#empty()} when the
     *                    session recorded nothing
     * @param destination the chosen attraction, or null when the user never selected one
     */
    public RestoredSession(SessionSummary summary, RouteSnapshot route, Attraction destination) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.route = Objects.requireNonNull(route, "route");
        this.destination = destination;
    }

    public SessionSummary getSummary() {
        return summary;
    }

    public RouteSnapshot getRoute() {
        return route;
    }

    /** The attraction the user was heading towards, or null when none was selected. */
    public Attraction getDestination() {
        return destination;
    }

    public boolean hasDestination() {
        return destination != null;
    }

    @Override
    public String toString() {
        return "RestoredSession{" + summary + ", " + route + ", destination="
                + (destination == null ? "none" : destination.getId()) + '}';
    }
}
