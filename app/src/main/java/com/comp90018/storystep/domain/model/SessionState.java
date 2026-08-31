package com.comp90018.storystep.domain.model;

/**
 * The lifecycle states of one exploration session.
 *
 * <p>{@code STARTING} and {@code STOPPING} exist because both transitions involve persistence that
 * can fail or be slow. Without them a second Start press during a slow write would open a second
 * session, and a failed final save would look like a successful one.
 */
public enum SessionState {

    /** No session. Browsing is allowed; nothing is being collected. */
    IDLE,

    /** Start requested; access is being resolved and the session record created. */
    STARTING,

    /** Collecting. Exactly one location subscription is registered. */
    ACTIVE,

    /** Session exists but nothing is being collected. Only an explicit Resume leaves this state. */
    PAUSED,

    /** Stop requested; sources are already released and the final save is in flight. */
    STOPPING,

    /** Finished and persisted. */
    COMPLETED;

    /** True when a session exists, whether or not it is currently collecting. */
    public boolean hasSession() {
        return this == ACTIVE || this == PAUSED || this == STOPPING;
    }

    /** True only while location updates should be registered. */
    public boolean isCollecting() {
        return this == ACTIVE;
    }

    /**
     * True while a transition is in flight. Duplicate commands are rejected here rather than
     * queued, which is what makes rapid double taps harmless.
     */
    public boolean isTransitional() {
        return this == STARTING || this == STOPPING;
    }
}
