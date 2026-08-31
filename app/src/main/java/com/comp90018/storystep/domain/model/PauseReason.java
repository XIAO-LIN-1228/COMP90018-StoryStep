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
    PROVIDER_LOST;

    /**
     * True when returning to the app is enough to make Resume possible. The other reasons need the
     * user to restore access first, so offering a plain Resume for them would just fail.
     */
    public boolean isResolvedByReturningToApp() {
        return this == APP_NOT_VISIBLE;
    }
}
