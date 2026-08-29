package com.comp90018.storystep.platform.location;

import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Foreground-only access to the device position.
 *
 * <p>Updates are delivered only between {@link #startUpdates(Listener)} and
 * {@link #stopUpdates()}, which are driven by an explicit user exploration session. There is no
 * background variant of this interface, and none may be added: continuous background tracking is
 * out of scope for the project.
 *
 * <p>No implementation exists yet, so the application requests no location permission and
 * registers no listener at launch.
 */
public interface LocationSource {

    interface Listener {

        void onLocationChanged(GeoPoint location, float accuracyMeters);

        /** Called when updates cannot continue, for example after a permission is revoked. */
        void onLocationUnavailable(Reason reason);
    }

    enum Reason {
        PERMISSION_DENIED,
        PROVIDER_DISABLED,
        NO_RECENT_FIX
    }

    /** True when the location permission has been granted and a provider is enabled. */
    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
