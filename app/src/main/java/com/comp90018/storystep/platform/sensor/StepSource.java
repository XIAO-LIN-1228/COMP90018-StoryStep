package com.comp90018.storystep.platform.sensor;

/**
 * The device's cumulative step counter, if it has one.
 *
 * <p>An interface for the same reason the other sensing sources are: the domain counts steps
 * without knowing about {@code SensorManager}, and a test can supply readings directly.
 */
public interface StepSource {

    interface Listener {
        /** One cumulative reading: steps since the device booted. */
        void onStepCount(long sinceBoot);
    }

    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
