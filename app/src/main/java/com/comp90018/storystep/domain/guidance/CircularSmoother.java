package com.comp90018.storystep.domain.guidance;

import com.comp90018.storystep.domain.geo.GeoCalculator;

/**
 * Exponential smoothing for an angle.
 *
 * <p>Smoothing is applied to the angle's unit vector, not to the number of degrees. Averaging
 * degrees is wrong at the wrap: the mean of 359 and 1 is 180, which points due south for a needle
 * that never left north. Averaging the sine and cosine and converting back has no such seam.
 *
 * <p>Pure Java, so the wraparound behaviour is a unit test rather than something to be discovered
 * while walking around holding a phone.
 */
public final class CircularSmoother {

    /**
     * How much of each new reading is taken. Low enough to steady a jittery compass, high enough
     * that a deliberate turn is followed within a second or so at typical sensor rates.
     */
    private static final double DEFAULT_ALPHA = 0.2;

    private final double alpha;

    private double sine;
    private double cosine;
    private boolean hasValue;

    public CircularSmoother() {
        this(DEFAULT_ALPHA);
    }

    public CircularSmoother(double alpha) {
        if (!(alpha > 0.0) || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in (0, 1]: " + alpha);
        }
        this.alpha = alpha;
    }

    /**
     * Adds a reading and returns the smoothed angle in {@code [0, 360)}.
     *
     * <p>The first reading is adopted whole: starting from an arbitrary angle and easing towards
     * the truth would show the user a direction the device never reported.
     */
    public double offer(double degrees) {
        double radians = Math.toRadians(GeoCalculator.normalizeDegrees(degrees));
        double newSine = Math.sin(radians);
        double newCosine = Math.cos(radians);

        if (!hasValue) {
            sine = newSine;
            cosine = newCosine;
            hasValue = true;
        } else {
            sine += alpha * (newSine - sine);
            cosine += alpha * (newCosine - cosine);
        }
        return current();
    }

    /** The smoothed angle, or {@code null} when nothing has been offered yet. */
    public Double value() {
        return hasValue ? current() : null;
    }

    public boolean hasValue() {
        return hasValue;
    }

    private double current() {
        return GeoCalculator.normalizeDegrees(Math.toDegrees(Math.atan2(sine, cosine)));
    }

    /** Forgets the history. Used when a session resumes, so the old direction does not bleed in. */
    public void reset() {
        hasValue = false;
        sine = 0.0;
        cosine = 0.0;
    }
}
