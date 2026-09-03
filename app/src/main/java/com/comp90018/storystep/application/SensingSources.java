package com.comp90018.storystep.application;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.platform.sensor.DeclinationSource;
import com.comp90018.storystep.platform.sensor.HeadingSource;
import com.comp90018.storystep.platform.sensor.MotionSource;

/**
 * The device sensing collaborators an exploration session uses besides location.
 *
 * <p>Bundled rather than passed one by one so the coordinator's constructor stays readable as more
 * sensing arrives. They belong together for a real reason: all three are started and stopped by the
 * same session, and none of them may be registered by anything else.
 *
 * <p>Every one is optional. {@link #none()} is a complete, working configuration — a session with
 * no motion and no compass still records a route, and that is exactly what a device without those
 * sensors must get.
 */
public final class SensingSources {

    private static final SensingSources NONE = new SensingSources(null, null, null);

    private final MotionSource motionSource;
    private final HeadingSource headingSource;
    private final DeclinationSource declinationSource;

    public SensingSources(MotionSource motionSource,
                          HeadingSource headingSource,
                          DeclinationSource declinationSource) {
        this.motionSource = motionSource;
        this.headingSource = headingSource;
        this.declinationSource = declinationSource;
    }

    /** Location only: what a device with no usable sensors, and most JVM tests, get. */
    public static SensingSources none() {
        return NONE;
    }

    /** True when the device can actually produce motion readings. */
    public boolean hasMotion() {
        return motionSource != null && motionSource.isAvailable();
    }

    /** True when the device can produce a heading and that heading can be corrected to true north. */
    public boolean hasHeading() {
        return headingSource != null && headingSource.isAvailable() && declinationSource != null;
    }

    public MotionSource getMotionSource() {
        return motionSource;
    }

    public HeadingSource getHeadingSource() {
        return headingSource;
    }

    /**
     * Declination at a place and time, or {@code null} when it cannot be determined.
     *
     * <p>Null rather than zero: zero is a real declination that happens to be correct in a few
     * places, and returning it for "unknown" would silently present magnetic north as true north.
     */
    public Float declinationAt(GeoPoint position, long wallClockMillis) {
        if (declinationSource == null || position == null) {
            return null;
        }
        return declinationSource.declinationDegrees(position, wallClockMillis);
    }
}
