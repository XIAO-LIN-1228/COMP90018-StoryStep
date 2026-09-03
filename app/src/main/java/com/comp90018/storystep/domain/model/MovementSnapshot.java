package com.comp90018.storystep.domain.model;

/**
 * The current movement estimate and how long the session has spent in each state.
 *
 * <p>Durations are accumulated from window timestamps, never from a count of sensor callbacks: the
 * delivery rate is a hint the platform is free to ignore, so counting callbacks would report a
 * different "time walking" on every device.
 */
public final class MovementSnapshot {

    private static final MovementSnapshot UNAVAILABLE =
            new MovementSnapshot(MovementState.UNAVAILABLE, 0L, 0L);

    private final MovementState state;
    private final long stationaryMillis;
    private final long movingMillis;

    public MovementSnapshot(MovementState state, long stationaryMillis, long movingMillis) {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (stationaryMillis < 0L || movingMillis < 0L) {
            throw new IllegalArgumentException("durations must not be negative");
        }
        this.state = state;
        this.stationaryMillis = stationaryMillis;
        this.movingMillis = movingMillis;
    }

    /** The state of a session with no motion sensing at all. */
    public static MovementSnapshot unavailable() {
        return UNAVAILABLE;
    }

    public MovementState getState() {
        return state;
    }

    public long getStationaryMillis() {
        return stationaryMillis;
    }

    public long getMovingMillis() {
        return movingMillis;
    }

    @Override
    public String toString() {
        return "MovementSnapshot{" + state + ", stationary " + stationaryMillis
                + "ms, moving " + movingMillis + "ms}";
    }
}
