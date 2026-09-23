package com.comp90018.storystep.domain.fitness;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StepCounterTest {

    @Test
    public void countsFromTheFirstReadingItSaw() {
        StepCounter counter = new StepCounter();
        assertEquals(0, counter.steps());
        counter.accept(12_000);
        assertEquals(0, counter.steps());
        counter.accept(12_350);
        assertEquals(350, counter.steps());
    }

    @Test
    public void aRebootRebasesRatherThanCountingBackwards() {
        StepCounter counter = new StepCounter();
        counter.accept(12_000);
        counter.accept(12_400);
        // The device restarted: the shared counter begins again from a small number.
        counter.accept(15);
        assertEquals(400, counter.steps());
        counter.accept(115);
        assertEquals(500, counter.steps());
    }

    @Test
    public void resetForgetsTheWalk() {
        StepCounter counter = new StepCounter();
        counter.accept(100);
        counter.accept(600);
        counter.reset();
        assertEquals(0, counter.steps());
        counter.accept(9_000);
        counter.accept(9_100);
        assertEquals(100, counter.steps());
    }
}
