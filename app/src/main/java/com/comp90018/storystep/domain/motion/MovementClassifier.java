package com.comp90018.storystep.domain.motion;

import com.comp90018.storystep.domain.model.MotionSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;

/**
 * Estimates whether the user is walking, from accelerometer readings alone.
 *
 * <p>The method, in order:
 *
 * <ol>
 *   <li><b>Remove gravity.</b> A low-pass filter tracks the constant downward component; what is
 *       left is the acceleration the user caused. The filter coefficient is derived from the actual
 *       interval between readings rather than fixed, because the delivery rate is a hint the
 *       platform may ignore and a fixed coefficient would smooth differently on every device.
 *   <li><b>Measure activity over a window.</b> Within each {@link #WINDOW_MILLIS} window, the
 *       standard deviation of the linear-acceleration magnitude is the activity measure. Standard
 *       deviation rather than mean: a phone held at a constant tilt has a non-zero mean forever,
 *       but only real movement makes the magnitude vary.
 *   <li><b>Decide with hysteresis.</b> Two thresholds with a gap between them; inside the gap the
 *       previous label stands. A single threshold would flip the label back and forth for any user
 *       walking near it.
 *   <li><b>Wait for agreement.</b> A new label is adopted only after {@link #DWELL_WINDOWS}
 *       consecutive windows agree, which is what stops a single door slam from reading as walking.
 * </ol>
 *
 * <p>Thresholds are the project's proposed starting values under decision D-07. They are to be
 * tuned against recorded stationary and walking trials, and they are not an accuracy guarantee.
 * This is not activity recognition and makes no claim beyond moving or not.
 *
 * <p>Pure Java and single-threaded, matching the coordinator that owns it.
 */
public final class MovementClassifier {

    /** Below this standard deviation, in m/s squared, the device is treated as still. */
    static final double STATIONARY_THRESHOLD = 0.35;

    /** Above this standard deviation the device is treated as travelling. */
    static final double MOVING_THRESHOLD = 0.7;

    /** Windows are closed by elapsed time, not by a number of samples. */
    static final long WINDOW_MILLIS = 2_000L;

    /** How many consecutive windows must agree before the reported label changes. */
    static final int DWELL_WINDOWS = 2;

    /** Time constant of the gravity filter. Half a second follows tilt without following steps. */
    private static final double GRAVITY_TIME_CONSTANT_MILLIS = 500.0;

    /** A gap longer than this means the readings either side are unrelated; the window restarts. */
    private static final long MAX_GAP_MILLIS = 5_000L;

    private double gravityX;
    private double gravityY;
    private double gravityZ;
    private boolean hasGravity;

    private long lastSampleMillis;
    private long windowStartMillis;
    private int windowSampleCount;
    private double windowSum;
    private double windowSumOfSquares;

    private MovementState reported = MovementState.UNKNOWN;
    private MovementState candidate;
    private int candidateWindows;

    private long stationaryMillis;
    private long movingMillis;

    /**
     * Offers one reading and returns the current estimate.
     *
     * <p>The returned snapshot is safe to publish directly: it is immutable and independent of the
     * classifier's internal state.
     */
    public MovementSnapshot offer(MotionSample sample) {
        if (sample == null) {
            throw new IllegalArgumentException("sample must not be null");
        }
        long now = sample.getMonotonicMillis();

        if (!hasGravity || now - lastSampleMillis > MAX_GAP_MILLIS || now < lastSampleMillis) {
            // First reading, a long gap, or a clock that went backwards. Any of them makes the
            // accumulated window meaningless, so start over rather than mix unrelated readings.
            startFresh(sample, now);
            return snapshot();
        }

        double intervalMillis = Math.max(1.0, now - lastSampleMillis);
        lastSampleMillis = now;
        updateGravity(sample, intervalMillis);

        double linearMagnitude = linearMagnitudeOf(sample);
        windowSampleCount++;
        windowSum += linearMagnitude;
        windowSumOfSquares += linearMagnitude * linearMagnitude;

        if (now - windowStartMillis >= WINDOW_MILLIS) {
            closeWindow(now);
        }
        return snapshot();
    }

    private void startFresh(MotionSample sample, long now) {
        gravityX = sample.getX();
        gravityY = sample.getY();
        gravityZ = sample.getZ();
        hasGravity = true;
        lastSampleMillis = now;
        windowStartMillis = now;
        windowSampleCount = 0;
        windowSum = 0.0;
        windowSumOfSquares = 0.0;
    }

    private void updateGravity(MotionSample sample, double intervalMillis) {
        // alpha = dt / (tau + dt): a longer gap between readings lets the estimate move further,
        // which is what keeps the filter behaving the same at 10 Hz and at 50 Hz.
        double alpha = intervalMillis / (GRAVITY_TIME_CONSTANT_MILLIS + intervalMillis);
        gravityX += alpha * (sample.getX() - gravityX);
        gravityY += alpha * (sample.getY() - gravityY);
        gravityZ += alpha * (sample.getZ() - gravityZ);
    }

    private double linearMagnitudeOf(MotionSample sample) {
        double x = sample.getX() - gravityX;
        double y = sample.getY() - gravityY;
        double z = sample.getZ() - gravityZ;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void closeWindow(long now) {
        long elapsed = now - windowStartMillis;
        MovementState observed = classifyWindow();

        if (observed != null) {
            if (observed == candidate) {
                candidateWindows++;
            } else {
                candidate = observed;
                candidateWindows = 1;
            }
            if (candidateWindows >= DWELL_WINDOWS || !reported.isKnown()) {
                // The very first decision is adopted immediately: making the user stare at
                // "working it out" for four seconds is worse than a label that may correct itself.
                reported = observed;
            }
        }

        if (reported == MovementState.STATIONARY) {
            stationaryMillis += elapsed;
        } else if (reported == MovementState.MOVING) {
            movingMillis += elapsed;
        }

        windowStartMillis = now;
        windowSampleCount = 0;
        windowSum = 0.0;
        windowSumOfSquares = 0.0;
    }

    /** The window's verdict, or null when it held too few readings to judge. */
    private MovementState classifyWindow() {
        if (windowSampleCount < 2) {
            return null;
        }
        double mean = windowSum / windowSampleCount;
        double variance = windowSumOfSquares / windowSampleCount - mean * mean;
        double deviation = Math.sqrt(Math.max(0.0, variance));

        if (deviation < STATIONARY_THRESHOLD) {
            return MovementState.STATIONARY;
        }
        if (deviation > MOVING_THRESHOLD) {
            return MovementState.MOVING;
        }
        // Inside the hysteresis band: not enough evidence to overturn the current label.
        return reported.isKnown() ? reported : null;
    }

    public MovementSnapshot snapshot() {
        return new MovementSnapshot(reported, stationaryMillis, movingMillis);
    }

    /**
     * Clears the windows and the gravity estimate, keeping the accumulated durations.
     *
     * <p>Called when a session pauses and resumes: the readings from before the pause describe a
     * different walk, but the minutes already spent walking still happened.
     */
    public void reset() {
        hasGravity = false;
        windowSampleCount = 0;
        windowSum = 0.0;
        windowSumOfSquares = 0.0;
        reported = MovementState.UNKNOWN;
        candidate = null;
        candidateWindows = 0;
    }

    /** Clears everything, for a new session. */
    public void clear() {
        reset();
        stationaryMillis = 0L;
        movingMillis = 0L;
    }
}
