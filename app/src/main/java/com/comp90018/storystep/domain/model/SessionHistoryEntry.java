package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * One finished exploration session, as the history list presents it.
 *
 * <p>Carries real instants rather than a formatted date. Ordering and relative-time labels are
 * both derived from the timestamp, and a pre-formatted string can support neither — it can only be
 * displayed, and only in the locale it happened to be built in.
 *
 * <p>{@code interrupted} and {@code segmentCount} are the two honesty labels the history screen
 * owes the user: the first says the walk was cut short by the app dying, the second says the route
 * has gaps in it. Neither can be recovered from the totals.
 */
public final class SessionHistoryEntry {

    private final String id;
    private final long startWallClockMillis;
    private final long endWallClockMillis;
    private final long activeDurationMillis;
    private final double distanceMeters;
    private final int visitCount;
    private final int segmentCount;
    private final boolean interrupted;
    private final String destinationName;

    /**
     * @param destinationName the place the user was heading towards, or null when none was chosen
     */
    public SessionHistoryEntry(String id,
                               long startWallClockMillis,
                               long endWallClockMillis,
                               long activeDurationMillis,
                               double distanceMeters,
                               int visitCount,
                               int segmentCount,
                               boolean interrupted,
                               String destinationName) {
        this.id = Objects.requireNonNull(id, "id");
        if (startWallClockMillis <= 0L) {
            throw new IllegalArgumentException(
                    "startWallClockMillis must be a real instant: " + startWallClockMillis);
        }
        if (activeDurationMillis < 0L) {
            throw new IllegalArgumentException(
                    "activeDurationMillis must not be negative: " + activeDurationMillis);
        }
        // Not finite, or negative, would survive every later comparison and render as "NaN m".
        if (Double.isNaN(distanceMeters) || Double.isInfinite(distanceMeters)
                || distanceMeters < 0.0) {
            throw new IllegalArgumentException(
                    "distanceMeters must be finite and not negative: " + distanceMeters);
        }
        if (visitCount < 0) {
            throw new IllegalArgumentException("visitCount must not be negative: " + visitCount);
        }
        this.startWallClockMillis = startWallClockMillis;
        this.endWallClockMillis = endWallClockMillis;
        this.activeDurationMillis = activeDurationMillis;
        this.distanceMeters = distanceMeters;
        this.visitCount = visitCount;
        // A session that recorded no points has no segments; anything else has at least one.
        this.segmentCount = Math.max(0, segmentCount);
        this.interrupted = interrupted;
        this.destinationName = destinationName;
    }

    public String getId() {
        return id;
    }

    public long getStartWallClockMillis() {
        return startWallClockMillis;
    }

    /** When the session was finished. Zero for a record written before it ended. */
    public long getEndWallClockMillis() {
        return endWallClockMillis;
    }

    /** Collecting time only; paused and process-dead spans were never counted. */
    public long getActiveDurationMillis() {
        return activeDurationMillis;
    }

    public double getDistanceMeters() {
        return distanceMeters;
    }

    public int getVisitCount() {
        return visitCount;
    }

    /** How many collection spans contributed route points. More than one means the route has gaps. */
    public int getSegmentCount() {
        return segmentCount;
    }

    /** True when the session was interrupted by the process dying at some point. */
    public boolean isInterrupted() {
        return interrupted;
    }

    /** True when the route was recorded across more than one span, so it has gaps in it. */
    public boolean hasRouteGaps() {
        return segmentCount > 1;
    }

    /** The place the user was heading towards, or null when none was chosen. */
    public String getDestinationName() {
        return destinationName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionHistoryEntry)) {
            return false;
        }
        SessionHistoryEntry that = (SessionHistoryEntry) other;
        return startWallClockMillis == that.startWallClockMillis
                && endWallClockMillis == that.endWallClockMillis
                && activeDurationMillis == that.activeDurationMillis
                && Double.compare(distanceMeters, that.distanceMeters) == 0
                && visitCount == that.visitCount
                && segmentCount == that.segmentCount
                && interrupted == that.interrupted
                && id.equals(that.id)
                && Objects.equals(destinationName, that.destinationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, startWallClockMillis, endWallClockMillis, activeDurationMillis,
                distanceMeters, visitCount, segmentCount, interrupted, destinationName);
    }

    @Override
    public String toString() {
        return "SessionHistoryEntry{id=" + id
                + ", start=" + startWallClockMillis
                + ", end=" + endWallClockMillis
                + ", active=" + activeDurationMillis
                + ", distance=" + distanceMeters
                + ", visits=" + visitCount
                + ", segments=" + segmentCount
                + ", interrupted=" + interrupted
                + '}';
    }
}
