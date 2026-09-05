package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Flattens a segmented route into normalised drawing coordinates.
 *
 * <p>Lives in {@code domain} rather than in the View so the maths is unit-testable, per the
 * project's rule that computation stays out of Android classes. The View that consumes this does
 * nothing but move a pen.
 *
 * <p>Equirectangular, around the bounding box of the whole route: longitude is scaled by the cosine
 * of the route's middle latitude so a degree east covers the same distance on screen as a degree
 * north. Over a walk this is indistinguishable from a proper projection and needs no library.
 *
 * <p>Both axes are divided by the <b>larger</b> span and the smaller one is then centred, so a
 * route that ran mostly north–south is drawn tall and narrow instead of being stretched to fill a
 * square. A sketch that changes a walk's shape is worse than no sketch.
 */
public final class RouteProjection {

    /** Normalised coordinates for one unbroken span, ready to draw. */
    public static final class Projected {

        private final int segment;
        private final float[] xs;
        private final float[] ys;

        Projected(int segment, float[] xs, float[] ys) {
            this.segment = segment;
            this.xs = xs;
            this.ys = ys;
        }

        public int getSegment() {
            return segment;
        }

        /** Normalised 0..1, left to right. */
        public float[] getXs() {
            return xs;
        }

        /** Normalised 0..1, already flipped so north is up and the View needs no maths. */
        public float[] getYs() {
            return ys;
        }

        public int size() {
            return xs.length;
        }
    }

    /** Below this the span is treated as a single place rather than a line. */
    private static final double DEGENERATE_SPAN = 1e-12;

    private RouteProjection() {
        // Utility class.
    }

    /**
     * Projects every segment into the same normalised frame.
     *
     * <p>One frame for the whole route, not one per segment: the segments are parts of a single
     * walk, and scaling each to its own bounds would place them on top of each other and destroy
     * the relationship between them.
     *
     * <p>A route whose points are all in one place — or a single point — maps to the centre rather
     * than dividing by a zero span.
     *
     * @return one {@link Projected} per input segment, in order; never null
     */
    public static List<Projected> project(List<RouteSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return Collections.emptyList();
        }
        double minLatitude = Double.POSITIVE_INFINITY;
        double maxLatitude = Double.NEGATIVE_INFINITY;
        double minLongitude = Double.POSITIVE_INFINITY;
        double maxLongitude = Double.NEGATIVE_INFINITY;
        for (RouteSegment segment : segments) {
            for (RoutePoint point : segment.getPoints()) {
                GeoPoint position = point.getPosition();
                minLatitude = Math.min(minLatitude, position.getLatitude());
                maxLatitude = Math.max(maxLatitude, position.getLatitude());
                minLongitude = Math.min(minLongitude, position.getLongitude());
                maxLongitude = Math.max(maxLongitude, position.getLongitude());
            }
        }

        double midLatitudeRadians = Math.toRadians((minLatitude + maxLatitude) / 2.0);
        // Equal metres per unit on both axes at this latitude.
        double longitudeScale = Math.cos(midLatitudeRadians);
        double width = (maxLongitude - minLongitude) * longitudeScale;
        double height = maxLatitude - minLatitude;
        double span = Math.max(width, height);

        boolean degenerate = span < DEGENERATE_SPAN;
        // Centres the shorter axis inside the square, preserving the route's real proportions.
        double xOffset = degenerate ? 0.0 : (span - width) / 2.0;
        double yOffset = degenerate ? 0.0 : (span - height) / 2.0;

        List<Projected> projected = new ArrayList<>(segments.size());
        for (RouteSegment segment : segments) {
            List<RoutePoint> points = segment.getPoints();
            float[] xs = new float[points.size()];
            float[] ys = new float[points.size()];
            for (int index = 0; index < points.size(); index++) {
                GeoPoint position = points.get(index).getPosition();
                if (degenerate) {
                    xs[index] = 0.5f;
                    ys[index] = 0.5f;
                    continue;
                }
                double x = ((position.getLongitude() - minLongitude) * longitudeScale + xOffset)
                        / span;
                double y = ((position.getLatitude() - minLatitude) + yOffset) / span;
                xs[index] = (float) x;
                // Flipped here so north is up: screen coordinates grow downwards.
                ys[index] = (float) (1.0 - y);
            }
            projected.add(new Projected(segment.getSegment(), xs, ys));
        }
        return Collections.unmodifiableList(projected);
    }
}
