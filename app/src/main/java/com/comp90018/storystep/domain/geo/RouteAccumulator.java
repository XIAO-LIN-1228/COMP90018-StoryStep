package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates travelled distance and a bounded sampled trace from accepted location fixes.
 *
 * <p>This class deliberately performs <b>no</b> quality judgement. Accuracy, staleness, receiver
 * drift and impossible jumps are already decided by {@link LocationSampleFilter} against
 * {@link LocationQualityPolicy}, and duplicating those thresholds here would mean two places to
 * tune and two chances to disagree. Everything offered here is assumed to have passed that filter.
 *
 * <p>Two rules make the total honest:
 *
 * <ul>
 *   <li>Distance is only summed <b>within</b> a segment. A segment changes when the session
 *       resumes, so the distance between the last fix before a pause and the first fix after it is
 *       never counted: the user may have driven across town while the app was not watching.
 *   <li>Points are stored at most once per {@link #MIN_POINT_INTERVAL_MILLIS}. Distance still
 *       accumulates from every accepted fix; only the stored trace is thinned, so a long session
 *       stays bounded without under-reporting how far the user walked.
 * </ul>
 *
 * <p>Pure Java and single-threaded, matching the coordinator that owns it.
 */
public final class RouteAccumulator {

    /** At most one stored point per five seconds, per the project's checkpoint/sampling policy. */
    public static final long MIN_POINT_INTERVAL_MILLIS = 5_000L;

    /** What the accumulator did with an offered sample. */
    public enum Decision {

        /** First fix of a new collection span; recorded, but no distance added. */
        SEGMENT_STARTED,

        /** Distance added and the point stored. */
        ACCUMULATED_AND_STORED,

        /** Distance added; the point fell inside the sampling interval and was not stored. */
        ACCUMULATED
    }

    private final List<RoutePoint> points = new ArrayList<>();

    private double totalDistanceMeters;
    private int segmentCount;
    private int currentSegment;
    private GeoPoint lastPositionInSegment;
    private LocationSample lastSample;
    private long lastStoredMonotonicMillis;

    /**
     * Offers an already-accepted fix.
     *
     * @param sample  a sample that passed the quality filter
     * @param segment the session's current collection span, from {@code SessionSummary#getSegment()}
     */
    public Decision offer(LocationSample sample, int segment) {
        if (sample == null) {
            throw new IllegalArgumentException("sample must not be null");
        }
        if (segment < 1) {
            throw new IllegalArgumentException("segment must be at least 1: " + segment);
        }

        lastSample = sample;

        if (segment != currentSegment) {
            currentSegment = segment;
            segmentCount++;
            lastPositionInSegment = sample.getPosition();
            store(sample, segment);
            return Decision.SEGMENT_STARTED;
        }

        totalDistanceMeters +=
                GeoCalculator.distanceMeters(lastPositionInSegment, sample.getPosition());
        lastPositionInSegment = sample.getPosition();

        if (sample.getMonotonicMillis() - lastStoredMonotonicMillis < MIN_POINT_INTERVAL_MILLIS) {
            return Decision.ACCUMULATED;
        }
        store(sample, segment);
        return Decision.ACCUMULATED_AND_STORED;
    }

    private void store(LocationSample sample, int segment) {
        points.add(new RoutePoint(sample.getPosition(), sample.getAccuracyMeters(),
                sample.getWallClockMillis(), segment));
        lastStoredMonotonicMillis = sample.getMonotonicMillis();
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    /** How many collection spans have contributed at least one fix. */
    public int getSegmentCount() {
        return segmentCount;
    }

    /** An immutable view of the accumulated route, for a screen or a store. */
    public RouteSnapshot snapshot() {
        return new RouteSnapshot(totalDistanceMeters, points, segmentCount, lastSample);
    }

    /** Clears everything. Called when a session ends or is discarded, never on a pause. */
    public void reset() {
        points.clear();
        totalDistanceMeters = 0.0;
        segmentCount = 0;
        currentSegment = 0;
        lastPositionInSegment = null;
        lastSample = null;
        lastStoredMonotonicMillis = 0L;
    }
}
