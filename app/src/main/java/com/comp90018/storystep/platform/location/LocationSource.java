package com.comp90018.storystep.platform.location;

import com.comp90018.storystep.domain.model.LocationSample;

/**
 * Foreground-only access to the device position.
 *
 * <p>Updates are delivered only between {@link #startUpdates(Listener)} and
 * {@link #stopUpdates()}, which are driven by an explicit user exploration session. There is no
 * background variant of this interface, and none may be added: continuous background tracking is
 * out of scope for the project.
 *
 * <p>Callbacks are delivered on the main thread, matching {@code RepositoryCallback}. After
 * {@link #stopUpdates()} returns, no further callback is delivered for the listener that was
 * stopped, so a caller does not have to defend against late deliveries.
 */
public interface LocationSource {

    interface Listener {

        /** A new position observation. Quality filtering is the caller's responsibility. */
        void onLocationChanged(LocationSample sample);

        /** Called when updates cannot continue or cannot start. */
        void onLocationUnavailable(Reason reason);
    }

    enum Reason {

        /** No location permission has been granted. */
        PERMISSION_DENIED,

        /**
         * Only {@code ACCESS_COARSE_LOCATION} was granted. Discovery still works, but route
         * precision and automatic arrival must stay disabled.
         */
        APPROXIMATE_ONLY,

        /** Location services are switched off, or no usable provider is enabled. */
        PROVIDER_DISABLED,

        /** Updates are running but nothing usable has arrived yet. */
        NO_RECENT_FIX
    }

    /** True when a location permission has been granted and a provider is enabled. */
    boolean isAvailable();

    /**
     * Begins delivering updates to {@code listener}, replacing any previous listener.
     *
     * <p>Implementations must not throw when permission is missing; they report
     * {@link Reason#PERMISSION_DENIED} instead.
     */
    void startUpdates(Listener listener);

    /** Stops delivery and releases the platform registration. Safe to call when not started. */
    void stopUpdates();
}