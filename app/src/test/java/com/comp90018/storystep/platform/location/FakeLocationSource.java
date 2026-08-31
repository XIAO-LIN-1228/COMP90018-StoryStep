package com.comp90018.storystep.platform.location;

import com.comp90018.storystep.domain.model.LocationSample;

/**
 * A {@link LocationSource} the test drives, which also records how it was used.
 *
 * <p>{@code registrationCount} is the point of it: the rule that at most one subscription exists
 * for one session cannot be asserted from state alone, only from how many times the source was
 * registered. {@code isRegistered} catches the opposite failure, a subscription left running after
 * the session that owned it ended.
 *
 * <p>{@link #emitToRetiredListener} exists to reproduce the callback that was already queued when
 * the subscription was torn down — the case a real provider produces on a busy main looper and that
 * nothing else can provoke deterministically.
 */
public final class FakeLocationSource implements LocationSource {

    private Listener listener;

    /** The listener from the previous registration, kept so a late callback can be simulated. */
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

    /** Number of times updates have been started. Two for one session means a duplicate. */
    public int registrationCount() {
        return registrationCount;
    }

    public boolean isRegistered() {
        return listener != null;
    }

    public void emit(LocationSample sample) {
        requireRegistered().onLocationChanged(sample);
    }

    public void emitUnavailable(Reason reason) {
        requireRegistered().onLocationUnavailable(reason);
    }

    /** Delivers a sample through a subscription that has already been replaced or stopped. */
    public void emitToRetiredListener(LocationSample sample) {
        if (retiredListener == null) {
            throw new IllegalStateException("no retired listener to deliver to");
        }
        retiredListener.onLocationChanged(sample);
    }

    private Listener requireRegistered() {
        if (listener == null) {
            throw new IllegalStateException("no listener is registered");
        }
        return listener;
    }
}
