package com.comp90018.storystep.domain.model;

/**
 * Why a session stopped collecting without the user pressing Stop.
 *
 * <p>The reason is kept rather than reduced to a single "paused" flag because each one needs a
 * different message and a different recovery: returning to the app is enough for one, and the
 * other two cannot be resumed until the user restores something outside the app.
 */
public enum PauseReason {

    /** The app went to the background or the screen locked. Foreground-only collection ends here. */
    APP_NOT_VISIBLE,

    /** Location permission was revoked or downgraded while the session was running. */
    PERMISSION_LOST,

    /** Location services were switched off, or every usable provider disappeared. */
    PROVIDER_LOST,

    /**
     * The process ended while the session was unfinished, and the session was read back from
     * storage on the next launch.
     *
     * <p>Distinct from {@link #APP_NOT_VISIBLE} because it is what the history screen must label
     * as interrupted, and because nothing collected during the gap exists at all — there was no
     * app to collect it. Resume is possible in the ordinary way once the user asks for it.
     */
    PROCESS_INTERRUPTED;

    /**
     * True when returning to the app is enough to make Resume possible. The other reasons need the
     * user to restore access first, so offering a plain Resume for them would just fail.
     */
    public boolean isResolvedByReturningToApp() {
        return this == APP_NOT_VISIBLE || this == PROCESS_INTERRUPTED;
    }
}
