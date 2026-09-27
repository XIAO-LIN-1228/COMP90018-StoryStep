package com.comp90018.storystep.ui.exploration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ArrivalFeedbackGateTest {

    @Test
    public void repeatedRenderingOfOneVisitProducesOneCue() {
        ArrivalFeedbackGate gate = new ArrivalFeedbackGate();

        assertTrue(gate.claim("session-1", "old-arts"));
        assertFalse(gate.claim("session-1", "old-arts"));
        assertFalse(gate.claim("session-1", "old-arts"));
    }

    @Test
    public void anotherVisitCanProduceAnotherCue() {
        ArrivalFeedbackGate gate = new ArrivalFeedbackGate();

        assertTrue(gate.claim("session-1", "old-arts"));
        assertTrue(gate.claim("session-1", "union-house"));
        assertTrue(gate.claim("session-2", "old-arts"));
    }

    @Test
    public void incompleteVisitIdentityNeverProducesACue() {
        ArrivalFeedbackGate gate = new ArrivalFeedbackGate();

        assertFalse(gate.claim(null, "old-arts"));
        assertFalse(gate.claim("session-1", null));
        assertFalse(gate.claim("", "old-arts"));
        assertFalse(gate.claim("session-1", ""));
    }
}
