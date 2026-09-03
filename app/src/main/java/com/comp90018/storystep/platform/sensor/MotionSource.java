package com.comp90018.storystep.platform.sensor;

import com.comp90018.storystep.domain.model.MotionSample;

/**
 * Foreground-only access to the accelerometer.
 *
 * <p>Shaped like {@code LocationSource} on purpose: the same single-owner rule applies. Updates
 * flow only between {@link #startUpdates(Listener)} and {@link #stopUpdates()}, both driven by an
 * explicit exploration session, and no screen registers its own.
 *
 * <p>Callbacks are delivered on the main thread. An implementation that has no accelerometer
 * reports {@link #isAvailable()} as false and delivers nothing; it must not throw, because a
 * missing motion sensor has to degrade the dashboard, not end the location session.
 */
public interface MotionSource {

    interface Listener {

        /** A raw reading, gravity included. Filtering and classification are the caller's job. */
        void onMotionSample(MotionSample sample);
    }

    /** True when the device actually has an accelerometer. */
    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
