package com.comp90018.storystep.platform.sensor;

/** The device's barometer, if it has one. Readings are in hectopascals. */
public interface PressureSource {

    interface Listener {
        void onPressure(double hectopascals);
    }

    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
