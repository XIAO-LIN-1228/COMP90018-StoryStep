package com.comp90018.storystep.platform.sensor;

/**
 * Compass heading derived from the accelerometer and magnetometer.
 *
 * <p>The reported heading is <b>magnetic</b>, not true north. The platform sensors cannot give
 * true north on their own: the correction is the local magnetic declination, which depends on
 * where and when the reading was taken. Converting it is therefore done in {@code domain.guidance}
 * with a declination supplied from the session's current position, which also makes that
 * conversion testable on the JVM instead of only observable on a device.
 *
 * <p>Implementations report {@link #isAvailable()} as false when either sensor is missing, so the
 * UI can fall back to distance-and-bearing guidance rather than failing. Registration is owned by
 * the exploration session, like every other sensing source.
 */
public interface HeadingSource {

    interface Listener {

        /**
         * @param magneticHeadingDegrees device heading clockwise from <b>magnetic</b> north, in
         *                               {@code [0, 360)}
         * @param accuracy               how far the reading can be trusted; never null
         */
        void onHeadingChanged(float magneticHeadingDegrees, HeadingAccuracy accuracy);
    }

    /** True when the device has both the accelerometer and the magnetometer a heading needs. */
    boolean isAvailable();

    void startUpdates(Listener listener);

    void stopUpdates();
}
