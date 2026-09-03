package com.comp90018.storystep.platform.sensor;

/**
 * How much the platform trusts the current magnetic reading.
 *
 * <p>Mirrors {@code SensorManager.SENSOR_STATUS_ACCURACY_*} without exposing the constants, so the
 * domain layer can reason about trust without importing an Android type.
 *
 * <p>An unreliable heading is still reported rather than dropped. Whether to show it, ask the user
 * to calibrate, or hide the compass entirely is a product decision, and the adapter is the wrong
 * place to make it.
 */
public enum HeadingAccuracy {

    /** The sensor is present but its readings cannot be trusted at all; calibration is needed. */
    UNRELIABLE,

    LOW,

    MEDIUM,

    HIGH;

    /**
     * True when a heading is good enough to point an arrow with.
     *
     * <p>{@code LOW} is deliberately included: a roughly right direction is still useful for
     * "which way do I turn", and demanding {@code HIGH} would leave most indoor users with no
     * guidance at all.
     */
    public boolean isUsable() {
        return this != UNRELIABLE;
    }
}
