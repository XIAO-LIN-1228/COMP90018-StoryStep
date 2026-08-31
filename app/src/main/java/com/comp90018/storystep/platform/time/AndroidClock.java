package com.comp90018.storystep.platform.time;

import android.os.SystemClock;

import com.comp90018.storystep.domain.time.Clock;

/**
 * The device's clocks.
 *
 * <p>{@code elapsedRealtime} is used for the monotonic reading because it keeps counting while the
 * device sleeps; {@code uptimeMillis} would stop, and a session paused overnight would come back
 * claiming almost no elapsed time. It is also the clock behind
 * {@code Location.getElapsedRealtimeNanos}, so sample ages and session durations are measured
 * against the same origin.
 */
public final class AndroidClock implements Clock {

    @Override
    public long wallClockMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public long monotonicMillis() {
        return SystemClock.elapsedRealtime();
    }
}
