package com.comp90018.storystep.domain.model;

/**
 * One accelerometer reading, in metres per second squared on the device's own axes.
 *
 * <p>The values still include gravity: separating the two is the classifier's job, not the
 * adapter's, because how much smoothing that separation needs is a tuning decision that belongs in
 * the domain layer.
 *
 * <p>Non-finite values are rejected at construction. A single {@code NaN} entering an exponential
 * filter poisons its gravity estimate permanently — every later reading would be {@code NaN} too,
 * and the classifier would silently stop working rather than fail visibly.
 *
 * <p>{@code monotonicMillis} comes from the same boot-relative clock as {@code LocationSample}, so
 * motion and location readings can be compared without converting anything.
 */
public final class MotionSample {

    private final float x;
    private final float y;
    private final float z;
    private final long monotonicMillis;

    public MotionSample(float x, float y, float z, long monotonicMillis) {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        this.x = x;
        this.y = y;
        this.z = z;
        this.monotonicMillis = monotonicMillis;
    }

    private static void requireFinite(float value, String name) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    /** Boot-relative milliseconds, matching {@code LocationSample#getMonotonicMillis()}. */
    public long getMonotonicMillis() {
        return monotonicMillis;
    }

    /** Magnitude of the raw vector, gravity included. */
    public double magnitude() {
        return Math.sqrt((double) x * x + (double) y * y + (double) z * z);
    }

    @Override
    public String toString() {
        return "MotionSample{" + x + ", " + y + ", " + z + " @" + monotonicMillis + '}';
    }
}
