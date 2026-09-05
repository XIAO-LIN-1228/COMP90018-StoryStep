package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a recorded trace into the unbroken spans it was actually collected in.
 *
 * <p>The segment number on each point is the only record of when collection stopped and started
 * again. Grouping by it — rather than by, say, the gap between timestamps — means the split matches
 * exactly what the session knew about itself, with no threshold to tune and no chance of inventing
 * a gap where the user simply stood still for a while.
 */
public final class RouteSegments {

    private RouteSegments() {
        // Utility class.
    }

    /**
     * Groups {@code orderedPoints} into runs of equal segment number, preserving order.
     *
     * <p>Runs, not buckets: two points with the same segment number that are separated by a point
     * from another segment stay in separate spans. That cannot happen in a trace the accumulator
     * produced, and if it ever did, merging them would bridge the very gap this class exists to
     * preserve.
     *
     * @param orderedPoints the trace, oldest first; may be empty
     * @return one {@link RouteSegment} per unbroken run, in order; never null
     */
    public static List<RouteSegment> split(List<RoutePoint> orderedPoints) {
        if (orderedPoints == null || orderedPoints.isEmpty()) {
            return Collections.emptyList();
        }
        List<RouteSegment> segments = new ArrayList<>();
        List<RoutePoint> current = new ArrayList<>();
        int currentSegment = orderedPoints.get(0).getSegment();
        for (RoutePoint point : orderedPoints) {
            if (point.getSegment() != currentSegment && !current.isEmpty()) {
                segments.add(new RouteSegment(currentSegment, current));
                current = new ArrayList<>();
            }
            currentSegment = point.getSegment();
            current.add(point);
        }
        if (!current.isEmpty()) {
            segments.add(new RouteSegment(currentSegment, current));
        }
        return Collections.unmodifiableList(segments);
    }
}
