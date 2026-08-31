package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * An immutable snapshot of one session, safe to hand to a screen or to a store.
 *
 * <p>{@code activeDurationMillis} excludes paused time: a session left in the background for an
 * hour did not record an hour of exploring. It is accumulated from monotonic readings and carried
 * as a total, so it survives a reboot that would invalidate any stored monotonic instant.
 *
 * <p>{@code segment} counts collection spans within the session. It starts at 1 and increases on
 * every Resume, which lets route work attribute points to a span and never draw a line across the
 * gap where the user was not being tracked.
 */
public final class SessionSummary {

    private final String id;
    private final SessionState state;
    private final PauseReason pauseReason;
    private final long startWallClockMillis;
    private final long activeDurationMillis;
    private final int segment;

    public SessionSummary(String id,
                          SessionState state,
                          PauseReason pauseReason,
                          long startWallClockMillis,
                          long activeDurationMillis,
                          int segment) {
        this.state = Objects.requireNonNull(state, "state");
        if (state.hasSession() || state == SessionState.COMPLETED) {
            Objects.requireNonNull(id, "a session in state " + state + " must have an id");
        }
        if (activeDurationMillis < 0L) {
            throw new IllegalArgumentException(
                    "activeDurationMillis must not be negative: " + activeDurationMillis);
        }
        if (segment < 0) {
            throw new IllegalArgumentException("segment must not be negative: " + segment);
        }
        if ((state == SessionState.PAUSED) != (pauseReason != null)) {
            // A paused session without a reason cannot be explained to the user, and a reason
            // attached to a running session would be stale the moment it was set.
            throw new IllegalArgumentException(
                    "pauseReason must be present exactly when PAUSED, was " + pauseReason
                            + " in state " + state);
        }
        this.id = id;
        this.pauseReason = pauseReason;
        this.startWallClockMillis = startWallClockMillis;
        this.activeDurationMillis = activeDurationMillis;
        this.segment = segment;
    }

    /** Null only before a session record exists, that is in {@code IDLE} and {@code STARTING}. */
    public String getId() {
        return id;
    }

    public SessionState getState() {
        return state;
    }

    /** Non-null exactly when the state is {@code PAUSED}. */
    public PauseReason getPauseReason() {
        return pauseReason;
    }

    /** Epoch milliseconds the session began; for display only. */
    public long getStartWallClockMillis() {
        return startWallClockMillis;
    }

    /** Collecting time only, with every paused span excluded. */
    public long getActiveDurationMillis() {
        return activeDurationMillis;
    }

    /** 1 for the first collection span, incremented on each Resume. */
    public int getSegment() {
        return segment;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionSummary)) {
            return false;
        }
        SessionSummary that = (SessionSummary) other;
        return startWallClockMillis == that.startWallClockMillis
                && activeDurationMillis == that.activeDurationMillis
                && segment == that.segment
                && state == that.state
                && pauseReason == that.pauseReason
                && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, state, pauseReason, startWallClockMillis, activeDurationMillis,
                segment);
    }

    @Override
    public String toString() {
        return "SessionSummary{" + id + ", " + state
                + (pauseReason == null ? "" : " (" + pauseReason + ")")
                + ", active=" + activeDurationMillis + "ms, segment=" + segment + '}';
    }
}
