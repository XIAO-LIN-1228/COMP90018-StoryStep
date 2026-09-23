package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.plan.PlannedRoute;

import java.util.List;

/**
 * The one seam onto a routing service, so the plan screen can be tested without a socket and
 * the service can be swapped without touching it.
 */
public interface WalkRouteSource {

    /**
     * The walking route through the points in order, or null when none could be worked out.
     *
     * <p>Null is an ordinary answer, not an error: the plan falls back to straight legs, which
     * is what it showed before routing existed. Blocking; call from a background executor.
     */
    PlannedRoute walkThrough(List<GeoPoint> points);
}
