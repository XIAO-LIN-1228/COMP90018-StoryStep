package com.comp90018.storystep.domain.model;

/**
 * A coarse estimate of whether the user is moving.
 *
 * <p>Deliberately coarse. This is a walking-or-not signal derived from one accelerometer; it is not
 * activity recognition and must never be presented as a claim about what the user is doing.
 *
 * <p>{@link #UNKNOWN} and {@link #UNAVAILABLE} are separate on purpose, and neither may be
 * collapsed into {@link #STATIONARY}. "The sensor has not seen enough yet", "this device has no
 * accelerometer" and "the user is standing still" are three different facts, and only the last one
 * says anything about the user.
 */
public enum MovementState {

    /** Enough readings, and they say the device is not travelling. */
    STATIONARY,

    /** Enough readings, and they say the device is travelling. */
    MOVING,

    /** The sensor is working but has not yet produced a full window to judge. */
    UNKNOWN,

    /** The device has no accelerometer, or motion collection is not running. */
    UNAVAILABLE;

    /** True when the estimate reflects actual readings rather than their absence. */
    public boolean isKnown() {
        return this == STATIONARY || this == MOVING;
    }
}
