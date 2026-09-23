package com.comp90018.storystep.domain.fitness;

/**
 * Steps taken during a walk, from the platform's cumulative step counter.
 *
 * <p>{@code TYPE_STEP_COUNTER} counts since the device booted and is shared by every app, so a
 * walk's steps are the difference from the first reading it saw. A reboot resets the counter,
 * which shows up as a reading below the last one; the run is rebased there rather than counted
 * backwards. Pure, so the arithmetic is tested without a sensor.
 */
public final class StepCounter {

    private long baseline = -1;
    private long last = -1;
    private long steps;

    /** Feeds one cumulative reading. Readings may arrive in any order of magnitude; see above. */
    public void accept(long sinceBoot) {
        if (sinceBoot < 0) {
            return;
        }
        if (baseline < 0) {
            baseline = sinceBoot;
            last = sinceBoot;
            return;
        }
        if (sinceBoot < last) {
            // The device rebooted, or the counter wrapped: bank what we have and start again.
            steps += Math.max(0, last - baseline);
            baseline = sinceBoot;
        }
        last = sinceBoot;
    }

    /** Steps taken since the walk began. */
    public long steps() {
        return baseline < 0 ? 0 : steps + Math.max(0, last - baseline);
    }

    /** Forgets the walk, keeping nothing. */
    public void reset() {
        baseline = -1;
        last = -1;
        steps = 0;
    }
}
