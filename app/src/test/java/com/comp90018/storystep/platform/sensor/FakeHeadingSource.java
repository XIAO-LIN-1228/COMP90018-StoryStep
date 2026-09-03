package com.comp90018.storystep.platform.sensor;

/**
 * A {@link HeadingSource} the test drives.
 *
 * <p>Emits <b>magnetic</b> headings, matching the real contract. A fake that emitted true headings
 * would quietly skip the declination correction, which is the part most likely to be wrong.
 */
public final class FakeHeadingSource implements HeadingSource {

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

    /** False reproduces a device with no magnetometer. */
    public void setAvailable(boolean value) {
        available = value;
    }

    public int registrationCount() {
        return registrationCount;
    }

    public boolean isRegistered() {
        return listener != null;
    }

    /** A trustworthy reading. */
    public void emit(float magneticHeadingDegrees) {
        emit(magneticHeadingDegrees, HeadingAccuracy.HIGH);
    }

    public void emit(float magneticHeadingDegrees, HeadingAccuracy accuracy) {
        requireRegistered().onHeadingChanged(magneticHeadingDegrees, accuracy);
    }

    public void emitToRetiredListener(float magneticHeadingDegrees) {
        if (retiredListener == null) {
            throw new IllegalStateException("no retired listener to deliver to");
        }
        retiredListener.onHeadingChanged(magneticHeadingDegrees, HeadingAccuracy.HIGH);
    }

    private Listener requireRegistered() {
        if (listener == null) {
            throw new IllegalStateException("no listener is registered");
        }
        return listener;
    }
}
