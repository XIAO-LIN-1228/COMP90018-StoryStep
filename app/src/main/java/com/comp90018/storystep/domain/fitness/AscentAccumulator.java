package com.comp90018.storystep.domain.fitness;

/**
 * Metres climbed during a walk, from barometric pressure.
 *
 * <p>Pressure is noisy and drifts with the weather, so raw differences would accumulate metres
 * on a flat street. Readings are smoothed, converted to altitude by the standard atmosphere,
 * and only a rise of at least {@link #MIN_RISE_METRES} above the last counted low is banked -
 * the same hysteresis a sports watch uses. Pure, so the filter is tested without a sensor.
 */
public final class AscentAccumulator {

    /** Sea-level pressure of the standard atmosphere, in hPa. */
    static final double SEA_LEVEL_HPA = 1013.25;

    /** Below this, a rise is noise or weather rather than a hill or a staircase. */
    static final double MIN_RISE_METRES = 3.0;

    /** Weight of each new reading in the smoothed altitude; a few seconds of averaging. */
    private static final double SMOOTHING = 0.15;

    private double smoothed;
    private boolean started;
    private double lowest;
    private double ascent;

    /** Feeds one pressure reading in hPa. */
    public void accept(double hectopascals) {
        if (!(hectopascals > 0) || Double.isNaN(hectopascals)) {
            return;
        }
        double altitude = altitudeMetres(hectopascals);
        if (!started) {
            smoothed = altitude;
            lowest = altitude;
            started = true;
            return;
        }
        smoothed += (altitude - smoothed) * SMOOTHING;
        if (smoothed < lowest) {
            lowest = smoothed;
        } else if (smoothed - lowest >= MIN_RISE_METRES) {
            ascent += smoothed - lowest;
            lowest = smoothed;
        }
    }

    /** Metres climbed since the walk began, counting only sustained rises. */
    public double ascentMetres() {
        return ascent;
    }

    public void reset() {
        started = false;
        smoothed = 0;
        lowest = 0;
        ascent = 0;
    }

    /** The barometric formula of the standard atmosphere. */
    static double altitudeMetres(double hectopascals) {
        return 44330.0 * (1.0 - Math.pow(hectopascals / SEA_LEVEL_HPA, 0.1903));
    }
}
