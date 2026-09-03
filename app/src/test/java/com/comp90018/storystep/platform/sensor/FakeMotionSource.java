package com.comp90018.storystep.platform.sensor;

import com.comp90018.storystep.domain.model.MotionSample;

import java.util.List;

/**
 * A {@link MotionSource} the test drives, shaped like {@code FakeLocationSource} and for the same
 * reason: the rule that a session registers a sensor once and releases it can only be asserted by
 * counting registrations, never from published state.
 *
 * <p>{@link #setAvailable(boolean)} with {@code false} reproduces a device with no accelerometer,
 * which is the configuration most likely to be forgotten and most likely to crash.
 */
public final class FakeMotionSource implements MotionSource {

    private Listener listener;
    private Listener retiredListener;
    private int registrationCount;
    private boolean available = true;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void startUpdates(Listener newListener) {
        if (newListener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        if (listener != null) {
            retiredListener = listener;
        }
        listener = newListener;
        registrationCount++;
    }

    @Override
    public void stopUpdates() {
        if (listener != null) {
            retiredListener = listener;
        }
        listener = null;
    }

    // --- test control --------------------------------------------------------------------------

    public void setAvailable(boolean value) {
        available = value;
    }

    public int registrationCount() {
        return registrationCount;
    }

    public boolean isRegistered() {
        return listener != null;
    }

    public void emit(MotionSample sample) {
        requireRegistered().onMotionSample(sample);
    }

    public void emitAll(List<MotionSample> samples) {
        for (MotionSample sample : samples) {
            emit(sample);
        }
    }

    /** Delivers through a subscription that has already been replaced or stopped. */
    public void emitToRetiredListener(MotionSample sample) {
        if (retiredListener == null) {
            throw new IllegalStateException("no retired listener to deliver to");
        }
        retiredListener.onMotionSample(sample);
    }

    private Listener requireRegistered() {
        if (listener == null) {
            throw new IllegalStateException("no listener is registered");
        }
        return listener;
    }
}
