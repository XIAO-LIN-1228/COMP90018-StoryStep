package com.comp90018.storystep.domain.session;

import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.time.Clock;

/**
 * The exploration session's transitions and duration policy, as a plain object.
 *
 * <p>No Android types and no I/O: it does not register a location source, write a record or touch a
 * lifecycle. It only decides what the session's state should become, so the whole policy —
 * including the transitions that are awkward to reproduce on a device, such as losing permission
 * mid-session — can be exercised in JVM tests with a fake clock.
 *
 * <p>Every command is idempotent. Offering one in a state that does not accept it returns an
 * unchanged {@link SessionTransition} rather than throwing, because most such calls are ordinary:
 * a double tap, or a background event arriving for an already-paused session. Callers must perform
 * side effects only when {@link SessionTransition#isChanged()} is true; that single rule is what
 * keeps two rapid Start presses from opening two location subscriptions.
 *
 * <p>Not thread-safe. The coordinator that owns it is the single caller, on the main thread.
 */
public final class SessionStateMachine {

    /** No active span in progress; monotonic instants are never negative in practice. */
    private static final long NO_ACTIVE_SPAN = -1L;

    private final Clock clock;

    private SessionState state = SessionState.IDLE;
    private String sessionId;
    private PauseReason pauseReason;

    private long startWallClockMillis;

    /** Collecting time from spans that have already ended. Paused time is never added here. */
    private long accumulatedActiveMillis;

    /** Monotonic instant the current collecting span began, or {@link #NO_ACTIVE_SPAN}. */
    private long activeSinceMonotonicMillis = NO_ACTIVE_SPAN;

    private int segment;

    /** True when the final save failed and the user should be offered a retry. */
    private boolean stopSaveFailed;

    public SessionStateMachine(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    // --- commands ----------------------------------------------------------------------------

    /**
     * The user asked to begin exploring.
     *
     * <p>Accepted only with no session in progress. From {@code PAUSED} this is deliberately a
     * no-op: an interrupted session is continued with {@link #resume()}, never replaced, which is
     * how the "at most one unfinished session" rule is upheld here.
     */
    public SessionTransition start() {
        if (state != SessionState.IDLE && state != SessionState.COMPLETED) {
            return unchanged();
        }
        SessionState from = state;
        reset();
        state = SessionState.STARTING;
        return new SessionTransition(from, state);
    }

    /**
     * Access was granted and the session record exists.
     *
     * @param id the persisted session's identifier
     */
    public SessionTransition onSessionCreated(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("id must not be empty");
        }
        if (state != SessionState.STARTING) {
            return unchanged();
        }
        sessionId = id;
        startWallClockMillis = clock.wallClockMillis();
        activeSinceMonotonicMillis = clock.monotonicMillis();
        segment = 1;
        state = SessionState.ACTIVE;
        return new SessionTransition(SessionState.STARTING, state);
    }

    /**
     * The start could not complete: access was denied or cancelled, or the record could not be
     * written. No session is left behind, so nothing can later be mistaken for a real one.
     */
    public SessionTransition onStartFailed() {
        if (state != SessionState.STARTING) {
            return unchanged();
        }
        reset();
        state = SessionState.IDLE;
        return new SessionTransition(SessionState.STARTING, state);
    }

    /**
     * Collection must stop without ending the session.
     *
     * <p>Accepted only from {@code ACTIVE}. An already-paused session keeps its original reason:
     * that reason is what the screen is currently explaining, and overwriting it would change the
     * message for something the user has already been told about.
     *
     * <p>Not accepted from {@code STARTING}. A session that does not exist yet has nothing to
     * pause; the caller is responsible for pausing immediately after
     * {@link #onSessionCreated(String)} if the condition still holds.
     */
    public SessionTransition pause(PauseReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (state != SessionState.ACTIVE) {
            return unchanged();
        }
        closeActiveSpan();
        pauseReason = reason;
        state = SessionState.PAUSED;
        return new SessionTransition(SessionState.ACTIVE, state);
    }

    /**
     * The user explicitly asked to continue. Opens a new segment rather than extending the
     * previous one, so nothing later joins the position before the pause to the position after it.
     */
    public SessionTransition resume() {
        if (state != SessionState.PAUSED) {
            return unchanged();
        }
        pauseReason = null;
        segment++;
        activeSinceMonotonicMillis = clock.monotonicMillis();
        state = SessionState.ACTIVE;
        return new SessionTransition(SessionState.PAUSED, state);
    }

    /**
     * The user asked to finish. Sources are expected to be released immediately; the state stays
     * {@code STOPPING} until the final save is confirmed, so a failed save cannot be presented as
     * a completed session.
     */
    public SessionTransition stop() {
        if (state != SessionState.ACTIVE && state != SessionState.PAUSED) {
            return unchanged();
        }
        SessionState from = state;
        closeActiveSpan();
        pauseReason = null;
        stopSaveFailed = false;
        state = SessionState.STOPPING;
        return new SessionTransition(from, state);
    }

    /** The final save succeeded. */
    public SessionTransition onStopPersisted() {
        if (state != SessionState.STOPPING) {
            return unchanged();
        }
        stopSaveFailed = false;
        state = SessionState.COMPLETED;
        return new SessionTransition(SessionState.STOPPING, state);
    }

    /**
     * The final save failed. The state stays {@code STOPPING} — collection has already ended, but
     * the session is not complete — and {@link #hasStopSaveFailed()} lets the screen offer a retry.
     */
    public SessionTransition onStopFailed() {
        if (state == SessionState.STOPPING) {
            stopSaveFailed = true;
        }
        return unchanged();
    }

    // --- state -------------------------------------------------------------------------------

    public SessionState getState() {
        return state;
    }

    /** Null until a session record exists. */
    public String getSessionId() {
        return sessionId;
    }

    public boolean hasStopSaveFailed() {
        return stopSaveFailed;
    }

    /**
     * Collecting time so far, excluding every paused span. Includes the span in progress when the
     * session is currently active.
     */
    public long activeDurationMillis() {
        if (activeSinceMonotonicMillis == NO_ACTIVE_SPAN) {
            return accumulatedActiveMillis;
        }
        return accumulatedActiveMillis + currentSpanMillis();
    }

    /** An immutable snapshot for a screen or a store. */
    public SessionSummary summary() {
        return new SessionSummary(
                sessionId, state, pauseReason, startWallClockMillis, activeDurationMillis(),
                segment);
    }

    // --- internals ---------------------------------------------------------------------------

    private SessionTransition unchanged() {
        return new SessionTransition(state, state);
    }

    private void closeActiveSpan() {
        if (activeSinceMonotonicMillis != NO_ACTIVE_SPAN) {
            accumulatedActiveMillis += currentSpanMillis();
            activeSinceMonotonicMillis = NO_ACTIVE_SPAN;
        }
    }

    /**
     * Clamped at zero. A monotonic clock should never run backwards, but a reading from a
     * different boot would, and silently subtracting it would corrupt the recorded duration.
     */
    private long currentSpanMillis() {
        return Math.max(0L, clock.monotonicMillis() - activeSinceMonotonicMillis);
    }

    private void reset() {
        sessionId = null;
        pauseReason = null;
        startWallClockMillis = 0L;
        accumulatedActiveMillis = 0L;
        activeSinceMonotonicMillis = NO_ACTIVE_SPAN;
        segment = 0;
        stopSaveFailed = false;
    }
}
