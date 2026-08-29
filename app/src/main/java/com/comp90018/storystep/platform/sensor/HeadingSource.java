package com.comp90018.storystep.platform.sensor;

/**
 * Compass heading derived from the accelerometer and magnetometer.
 *
 * <p>Implementations must report {@link #isAvailable()} as false when either sensor is missing so
 * the UI can fall back to distance-only guidance instead of failing. Registration is tied to the
 * visible lifecycle of the screen that needs it.
 *
 * <p>No implementation exists yet; no sensor is registered at launch.
 */
public interface HeadingSource {

    interface Listener {

        /**
         * @param headingDegrees device heading clockwise from true north, in {@code [0, 360)}
         * @param reliable       false while the sensor reports low accuracy
         */
        void onHeadingChanged(float headingDegrees, boolean reliable);
    }

    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
