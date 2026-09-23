package com.comp90018.storystep.domain.fitness;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AscentAccumulatorTest {

    /** Feeds a reading often enough for the smoothing to settle on it. */
    private static void hold(AscentAccumulator ascent, double hectopascals, int times) {
        for (int i = 0; i < times; i++) {
            ascent.accept(hectopascals);
        }
    }

    @Test
    public void flatWalkingAndWeatherNoiseCountNothing() {
        AscentAccumulator ascent = new AscentAccumulator();
        for (int i = 0; i < 200; i++) {
            // A tenth of a hectopascal is under a metre: noise, not a hill.
            ascent.accept(1013.25 + (i % 2 == 0 ? 0.1 : -0.1));
        }
        assertEquals(0.0, ascent.ascentMetres(), 0.001);
    }

    @Test
    public void aSustainedClimbIsCountedOnceAndComingBackDownIsNot() {
        AscentAccumulator ascent = new AscentAccumulator();
        hold(ascent, 1013.25, 60);
        // Around 12 hPa lower: roughly a hundred metres up.
        hold(ascent, 1001.0, 120);
        double climbed = ascent.ascentMetres();
        assertTrue("expected roughly 100 m, got " + climbed, climbed > 80 && climbed < 120);
        hold(ascent, 1013.25, 120);
        assertEquals("descending adds nothing", climbed, ascent.ascentMetres(), 0.5);
        hold(ascent, 1001.0, 120);
        assertTrue("a second climb counts again", ascent.ascentMetres() > climbed * 1.8);
    }

    @Test
    public void theStandardAtmosphereIsTheConversion() {
        assertEquals(0.0, AscentAccumulator.altitudeMetres(1013.25), 0.5);
        assertEquals(111.0, AscentAccumulator.altitudeMetres(1000.0), 3.0);
    }

    @Test
    public void resetForgetsTheWalk() {
        AscentAccumulator ascent = new AscentAccumulator();
        hold(ascent, 1013.25, 40);
        hold(ascent, 1000.0, 120);
        assertTrue(ascent.ascentMetres() > 50);
        ascent.reset();
        assertEquals(0.0, ascent.ascentMetres(), 0.001);
    }
}
