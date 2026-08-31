package com.comp90018.storystep.domain.session;

import com.comp90018.storystep.domain.model.SessionState;

import java.util.Objects;

/**
 * The result of offering a command to {@link SessionStateMachine}.
 *
 * <p>Commands return this instead of throwing on an unexpected state, because most rejected
 * commands are normal: a second Stop tap, a background event for a session that is already paused.
 * The caller checks {@link #isChanged()} to decide whether a side effect — registering a location
 * source, writing a checkpoint — is actually due. Performing side effects on an unchanged
 * transition is what produces duplicate subscriptions.
 */
public final class SessionTransition {

    private final SessionState from;
    private final SessionState to;

    SessionTransition(SessionState from, SessionState to) {
        this.from = Objects.requireNonNull(from, "from");
        this.to = Objects.requireNonNull(to, "to");
    }

    public SessionState getFrom() {
        return from;
    }

    public SessionState getTo() {
        return to;
    }

    /** False when the command was a no-op in the current state, so no side effect is due. */
    public boolean isChanged() {
        return from != to;
    }

    @Override
    public String toString() {
        return isChanged() ? from + " -> " + to : from + " (unchanged)";
    }
}
