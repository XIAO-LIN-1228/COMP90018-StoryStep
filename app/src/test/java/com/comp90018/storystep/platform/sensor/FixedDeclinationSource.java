package com.comp90018.storystep.platform.sensor;

import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * A declination that does not depend on where you are, so a test can state the expected true
 * heading as an arithmetic fact instead of trusting the platform's magnetic model.
 */
public final class FixedDeclinationSource implements DeclinationSource {

    /** Close to the real value for Melbourne, where the project's fixtures are located. */
    public static final float MELBOURNE_DEGREES = 11.0f;

    private final float degrees;

    public FixedDeclinationSource(float degrees) {
        this.degrees = degrees;
    }

    public static FixedDeclinationSource melbourne() {
        return new FixedDeclinationSource(MELBOURNE_DEGREES);
    }

    /** Declination is genuinely near zero in some places; useful for isolating other behaviour. */
    public static FixedDeclinationSource zero() {
        return new FixedDeclinationSource(0.0f);
    }

    @Override
    public float declinationDegrees(GeoPoint at, long wallClockMillis) {
        return degrees;
    }
}
