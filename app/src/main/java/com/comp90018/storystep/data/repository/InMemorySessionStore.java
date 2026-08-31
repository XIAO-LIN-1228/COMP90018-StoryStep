package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.time.Clock;

/**
 * Process-lifetime {@link SessionStore}.
 *
 * <p>Deliberately not durable: nothing here survives the process, so a session interrupted by
 * process death is lost. That is the honest state of the app until the Room-backed store arrives
 * with REQ-15; this implementation exists so the session lifecycle can be built and tested against
 * the real contract, including its failure paths, without waiting for a schema.
 *
 * <p>Answers synchronously, like {@link InMemoryAttractionRepository}, which keeps coordinator and
 * ViewModel tests deterministic.
 */
public final class InMemorySessionStore implements SessionStore {

    private final Clock clock;

    /** The one session this store knows about. Replaced only once the previous one is finished. */
    private SessionSummary current;

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
        callback.onSuccess(null);
    }

    @Override
    public void findUnfinished(RepositoryCallback<SessionSummary> callback) {
        boolean unfinished = current != null && current.getState() != SessionState.COMPLETED;
        callback.onSuccess(unfinished ? current : null);
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
