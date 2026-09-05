package com.comp90018.storystep.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Everything history knows about one finished session.
 *
 * <p>Self-contained by design. The visits carry their own snapshotted attraction name and story
 * text, and the weather and movement were copied onto the session while it ran, so opening a
 * journey from history needs neither the attraction cache nor the network. That is what makes the
 * offline relaunch case work rather than degrade.
 */
public final class SessionHistoryDetail {

    private final SessionHistoryEntry summary;
    private final List<Visit> visits;
    private final List<RouteSegment> segments;
    private final MovementSnapshot movement;
    private final WeatherSnapshot weather;

    /**
     * @param movement the movement totals recorded for the session; {@code UNAVAILABLE} when the
     *                 device had no motion sensor, which is not the same as a motionless walk
     * @param weather  the observation recorded while the session ran, or null when none was
     */
    public SessionHistoryDetail(SessionHistoryEntry summary,
                                List<Visit> visits,
                                List<RouteSegment> segments,
                                MovementSnapshot movement,
                                WeatherSnapshot weather) {
        this.summary = Objects.requireNonNull(summary, "summary");
        this.visits = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(visits, "visits")));
        this.segments = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(segments, "segments")));
        this.movement = Objects.requireNonNull(movement, "movement");
        this.weather = weather;
    }

    public SessionHistoryEntry getSummary() {
        return summary;
    }

    /** The places visited during the session, earliest arrival first. */
    public List<Visit> getVisits() {
        return visits;
    }

    /** The recorded route, split into unbroken spans. Empty when nothing was recorded. */
    public List<RouteSegment> getSegments() {
        return segments;
    }

    public MovementSnapshot getMovement() {
        return movement;
    }

    /** The weather recorded during the session, or null when none was. */
    public WeatherSnapshot getWeather() {
        return weather;
    }

    public boolean hasWeather() {
        return weather != null;
    }

    public boolean hasRoute() {
        return !segments.isEmpty();
    }

    @Override
    public String toString() {
        return "SessionHistoryDetail{session=" + summary.getId()
                + ", visits=" + visits.size()
                + ", segments=" + segments.size()
                + ", weather=" + (weather == null ? "none" : weather.getAreaKey())
                + '}';
    }
}
