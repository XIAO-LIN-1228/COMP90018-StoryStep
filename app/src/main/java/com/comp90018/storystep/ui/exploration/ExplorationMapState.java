package com.comp90018.storystep.ui.exploration;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the walking map draws: where the user is, where they are going, and the path recorded so
 * far, split into the collection segments the route was sampled in.
 *
 * <p>Segments are kept apart on purpose. The gap between two of them is time the user was not
 * being tracked, and joining them would invent a path across it.
 */
public final class ExplorationMapState {

    @Nullable private final GeoPoint position;
    @Nullable private final GeoPoint destination;
    private final List<List<GeoPoint>> segments;

    private ExplorationMapState(@Nullable GeoPoint position,
                                @Nullable GeoPoint destination,
                                List<List<GeoPoint>> segments) {
        this.position = position;
        this.destination = destination;
        this.segments = segments;
    }

    public static ExplorationMapState from(@Nullable GeoPoint destination, RouteSnapshot route) {
        List<List<GeoPoint>> segments = new ArrayList<>();
        int currentSegment = -1;
        List<GeoPoint> current = null;
        for (RoutePoint point : route.getPoints()) {
            if (point.getSegment() != currentSegment) {
                currentSegment = point.getSegment();
                current = new ArrayList<>();
                segments.add(current);
            }
            current.add(point.getPosition());
        }
        GeoPoint position = route.getLastSample() == null
                ? null
                : route.getLastSample().getPosition();
        return new ExplorationMapState(position, destination,
                Collections.unmodifiableList(segments));
    }

    @Nullable
    public GeoPoint getPosition() {
        return position;
    }

    @Nullable
    public GeoPoint getDestination() {
        return destination;
    }

    public List<List<GeoPoint>> getSegments() {
        return segments;
    }
}
