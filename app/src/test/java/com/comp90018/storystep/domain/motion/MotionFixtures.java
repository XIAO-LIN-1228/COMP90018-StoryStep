package com.comp90018.storystep.domain.motion;

import com.comp90018.storystep.domain.model.MotionSample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic accelerometer traces with documented expected labels.
 *
 * <p>These stand in for FX-MOTION in the verification checklist. They are synthetic and prove only
 * that the classifier behaves as specified on signals of a given shape; they are not evidence about
 * any real device, which is what the physical trials are for.
 *
 * <p>Every generator is seeded, so a failure is reproducible rather than "flaky".
 */
final class MotionFixtures {

    /** Earth gravity, the constant component every trace carries. */
    static final float GRAVITY = 9.81f;

    /** 50 ms between readings, matching the hub's ~20 Hz request. */
    static final long INTERVAL_MILLIS = 50L;

    private MotionFixtures() {
    }

    /**
     * A phone lying still. Only sensor noise, well under the stationary threshold.
     *
     * <p>Expected label: {@code STATIONARY}.
     */
    static List<MotionSample> stationary(long durationMillis) {
        return generate(durationMillis, 0.02, 0.0, 0L, 1);
    }

    /**
     * A phone being carried at walking pace: a roughly 2 Hz step rhythm peaking near 3 m/s squared,
     * which is within the range an ordinary walk produces beyond gravity.
     *
     * <p>Expected label: {@code MOVING}.
     */
    static List<MotionSample> walking(long durationMillis, long startMillis) {
        return generate(durationMillis, 0.05, 3.0, startMillis, 2);
    }

    /**
     * The phone turned in the hand while the user stands still: the gravity vector moves between
     * axes, but its magnitude does not change and nothing oscillates.
     *
     * <p>Expected label: {@code STATIONARY}. This is the fixture that catches a classifier keyed on
     * raw magnitude or on per-axis change rather than on the variation of linear acceleration.
     */
    static List<MotionSample> rotationWithoutWalking(long durationMillis) {
        List<MotionSample> samples = new ArrayList<>();
        int steps = (int) (durationMillis / INTERVAL_MILLIS);
        for (int i = 0; i < steps; i++) {
            // A slow quarter turn over the whole trace, gravity magnitude preserved throughout.
            double angle = Math.PI / 2.0 * i / Math.max(1, steps - 1);
            samples.add(new MotionSample(
                    (float) (GRAVITY * Math.sin(angle)),
                    0.0f,
                    (float) (GRAVITY * Math.cos(angle)),
                    i * INTERVAL_MILLIS));
        }
        return samples;
    }

    /**
     * A weak, ambiguous rhythm — fidgeting, or a phone on a table next to something running.
     *
     * <p>Amplitude chosen to land inside the hysteresis band: too lively to call still, too weak to
     * call walking. The expected behaviour is that the label does not change, whatever it was.
     */
    static List<MotionSample> borderlineNoise(long durationMillis, long startMillis) {
        return generate(durationMillis, 0.05, 1.6, startMillis, 3);
    }

    /**
     * @param noise      standard deviation of the random component, m/s squared
     * @param stridePeak amplitude of the periodic walking component, m/s squared
     */
    private static List<MotionSample> generate(long durationMillis,
                                               double noise,
                                               double stridePeak,
                                               long startMillis,
                                               long seed) {
        Random random = new Random(seed);
        List<MotionSample> samples = new ArrayList<>();
        int steps = (int) (durationMillis / INTERVAL_MILLIS);
        for (int i = 0; i < steps; i++) {
            long timestamp = startMillis + i * INTERVAL_MILLIS;
            double seconds = i * INTERVAL_MILLIS / 1000.0;
            double stride = stridePeak * Math.sin(2.0 * Math.PI * 2.0 * seconds);
            samples.add(new MotionSample(
                    (float) (random.nextGaussian() * noise),
                    (float) (random.nextGaussian() * noise),
                    (float) (GRAVITY + stride + random.nextGaussian() * noise),
                    timestamp));
        }
        return samples;
    }
}
