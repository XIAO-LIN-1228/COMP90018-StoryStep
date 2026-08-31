package com.comp90018.storystep.domain.time;

/**
 * The two clocks the app is allowed to read.
 *
 * <p>They are separated because they answer different questions and are not interchangeable.
 * {@link #wallClockMillis()} is what a user reads; it can jump backwards when the device syncs
 * time, changes time zone or the user edits the clock. {@link #monotonicMillis()} only ever moves
 * forward while the device is awake, which makes it the only safe basis for measuring how long
 * something took.
 *
 * <p>Never subtract one from the other, and never compare monotonic readings taken across a
 * reboot: the monotonic origin resets. {@code LocationSample} carries both for the same reason.
 *
 * <p>An interface rather than direct platform calls so that duration behaviour — a session paused
 * for an hour, a fix that went stale — can be exercised in JVM tests without waiting.
 */
public interface Clock {

    /** Milliseconds since the epoch. For display and for records the user reads. */
    long wallClockMillis();

    /** Monotonic milliseconds. The only clock valid for measuring an interval. */
    long monotonicMillis();
}
