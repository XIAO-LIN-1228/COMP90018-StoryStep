package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.time.Clock;

import java.util.ArrayList;
import java.util.List;

/**
 * Process-lifetime {@link SessionStore}.
 *
 * <p>Deliberately not durable: nothing here survives the process, so a session interrupted by
 * process death is lost. The Room-backed {@link RoomSessionStore} is what the application runs on;
 * this implementation stays because it exercises the same contract, including its failure paths,
 * with no database and no threading, which is what keeps coordinator and ViewModel tests
 * deterministic.
 *
 * <p>Answers synchronously, like {@link InMemoryAttractionRepository}.
 */
public final class InMemorySessionStore implements SessionStore {

    private final Clock clock;

    /** The one session this store knows about. Replaced only once the previous one is finished. */
    private SessionSummary current;

    /** The current session's stored trace, mirroring what the Room store keeps in its own table. */
    private final List<RoutePoint> routePoints = new ArrayList<>();

    private double totalDistanceMeters;
    private int segmentCount;
    private Attraction destination;

    private int nextId = 1;

    public InMemorySessionStore(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    @Override
    public void create(RepositoryCallback<SessionSummary> callback) {
        if (current != null && current.getState() != SessionState.COMPLETED) {
            callback.onError(new IllegalStateException(
                    "an unfinished session already exists: " + current.getId()));
            return;
        }
        SessionSummary created = new SessionSummary(
                "session-" + nextId++,
                SessionState.ACTIVE,
                null,
                clock.wallClockMillis(),
                0L,
                1);
        current = created;
        clearRoute();
        destination = null;
        callback.onSuccess(created);
    }

    @Override
    public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
        if (!matchesCurrent(summary)) {
            callback.onError(unknownSession(summary));
            return;
        }
        current = summary;
        callback.onSuccess(null);
    }

    @Override
    public void appendRoute(String sessionId,
                            int firstSequence,
                            List<RoutePoint> newPoints,
                            double totalDistanceMeters,
                            int segmentCount,
                            RepositoryCallback<Void> callback) {
        if (newPoints == null) {
            callback.onError(new IllegalArgumentException("newPoints must not be null"));
            return;
        }
        if (firstSequence < 0) {
            callback.onError(new IllegalArgumentException(
                    "firstSequence must not be negative: " + firstSequence));
            return;
        }
        if (current == null || sessionId == null || !sessionId.equals(current.getId())) {
            callback.onError(new IllegalArgumentException("unknown session: " + sessionId));
            return;
        }
        // Positional, like the unique (session, sequence) index of the durable store: re-offering
        // points that are already held overwrites them in place rather than appending twice.
        for (int offset = 0; offset < newPoints.size(); offset++) {
            int sequence = firstSequence + offset;
            RoutePoint point = newPoints.get(offset);
            if (sequence < routePoints.size()) {
                routePoints.set(sequence, point);
            } else if (sequence == routePoints.size()) {
                routePoints.add(point);
            } else {
                callback.onError(new IllegalArgumentException(
                        "route points must be appended without gaps: " + sequence));
                return;
            }
        }
        this.totalDistanceMeters = totalDistanceMeters;
        this.segmentCount = segmentCount;
        callback.onSuccess(null);
    }

    @Override
    public void saveDestination(String sessionId,
                                Attraction destination,
                                RepositoryCallback<Void> callback) {
        if (current == null || sessionId == null || !sessionId.equals(current.getId())) {
            callback.onError(new IllegalArgumentException("unknown session: " + sessionId));
            return;
        }
        this.destination = destination;
        callback.onSuccess(null);
    }

    @Override
    public void finish(SessionSummary summary, RepositoryCallback<Void> callback) {
        if (current != null && current.getState() == SessionState.COMPLETED
                && matchesCurrent(summary)) {
            // Idempotent: a retried finish after a successful save must not fail or duplicate.
            callback.onSuccess(null);
            return;
        }
        if (!matchesCurrent(summary)) {
            callback.onError(unknownSession(summary));
            return;
        }
        current = new SessionSummary(
                summary.getId(),
                SessionState.COMPLETED,
                null,
                summary.getStartWallClockMillis(),
                summary.getActiveDurationMillis(),
                summary.getSegment());
        callback.onSuccess(null);
    }

    @Override
    public void discard(String sessionId, RepositoryCallback<Void> callback) {
        if (current == null) {
            callback.onSuccess(null);
            return;
        }
        if (sessionId == null || !sessionId.equals(current.getId())) {
            callback.onError(new IllegalArgumentException("unknown session: " + sessionId));
            return;
        }
        current = null;
        clearRoute();
        destination = null;
        callback.onSuccess(null);
    }

    @Override
    public void findUnfinished(RepositoryCallback<RestoredSession> callback) {
        boolean unfinished = current != null && current.getState() != SessionState.COMPLETED;
        if (!unfinished) {
            callback.onSuccess(null);
            return;
        }
        callback.onSuccess(new RestoredSession(current, storedRoute(), destination));
    }

    /** The trace as a restored session would see it: no last fix, because none was replayed. */
    private RouteSnapshot storedRoute() {
        if (routePoints.isEmpty() && totalDistanceMeters == 0.0 && segmentCount == 0) {
            return RouteSnapshot.empty();
        }
        return new RouteSnapshot(totalDistanceMeters, routePoints, segmentCount, null);
    }

    private void clearRoute() {
        routePoints.clear();
        totalDistanceMeters = 0.0;
        segmentCount = 0;
    }

    private boolean matchesCurrent(SessionSummary summary) {
        return summary != null
                && summary.getId() != null
                && current != null
                && summary.getId().equals(current.getId());
    }

    private static IllegalArgumentException unknownSession(SessionSummary summary) {
        return new IllegalArgumentException("unknown session: "
                + (summary == null ? "null" : summary.getId()));
    }
}
