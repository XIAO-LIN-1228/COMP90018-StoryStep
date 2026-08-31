package com.comp90018.storystep.domain.session;

import com.comp90018.storystep.domain.time.Clock;

/**
 * A clock the test drives by hand.
 *
 * <p>Both readings advance together by default, which matches an undisturbed device. They can also
 * be moved independently, which is how the tests reproduce a wall-clock jump — a time-zone change
 * or an NTP correction — without waiting for one.
 */
public final class FakeClock implements Clock {

    private long wallClockMillis;
    private long monotonicMillis;

    public FakeClock(long wallClockMillis, long monotonicMillis) {
        this.wallClockMillis = wallClockMillis;
        this.monotonicMillis = monotonicMillis;
    }

    public static FakeClock startingAt(long millis) {
        return new FakeClock(millis, millis);
    }

    /** Moves both clocks forward, as ordinary elapsed time does. */
    public FakeClock advance(long millis) {
        wallClockMillis += millis;
        monotonicMillis += millis;
        return this;
    }

    /** Moves only the wall clock, including backwards. The monotonic clock is unaffected. */
    public FakeClock shiftWallClock(long millis) {
        wallClockMillis += millis;
        return this;
    }

    @Override
    public long wallClockMillis() {
        return wallClockMillis;
    }

    @Override
    public long monotonicMillis() {
        return monotonicMillis;
    }
}
