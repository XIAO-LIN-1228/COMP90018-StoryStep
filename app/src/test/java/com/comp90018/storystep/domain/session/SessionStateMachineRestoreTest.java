package com.comp90018.storystep.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;

import org.junit.Before;
import org.junit.Test;

/**
 * Adoption of a session read back after the process ended.
 *
 * <p>The rule the whole feature rests on is here: a restored session never collects. Opening the
 * app is not the user asking to continue, and a machine that came back {@code ACTIVE} would have
 * the coordinator register a location source on launch.
 */
public class SessionStateMachineRestoreTest {

    private FakeClock clock;
    private SessionStateMachine machine;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(10_000L);
        machine = new SessionStateMachine(clock);
    }

    @Test
    public void aRestoredSessionComesBackPausedAndInterrupted() {
        machine.restore(stored(SessionState.ACTIVE, null, 45_000L, 2));

        assertEquals(SessionState.PAUSED, machine.getState());
        assertEquals(PauseReason.PROCESS_INTERRUPTED, machine.summary().getPauseReason());
        assertFalse("a restored session must not be collecting",
                machine.getState().isCollecting());
    }

    @Test
    public void theStoredIdentityAndTotalsAreKept() {
        machine.restore(stored(SessionState.ACTIVE, null, 45_000L, 2));

        SessionSummary summary = machine.summary();
        assertEquals("session-7", summary.getId());
        assertEquals(1_000L, summary.getStartWallClockMillis());
        assertEquals(45_000L, summary.getActiveDurationMillis());
        assertEquals(2, summary.getSegment());
    }

    @Test
    public void timeSpentWithTheAppDeadIsNotCountedAsCollecting() {
        machine.restore(stored(SessionState.ACTIVE, null, 45_000L, 2));

        // Hours pass with the session merely restored and untouched.
        clock.advance(3_600_000L);

        assertEquals("a paused session accumulates nothing",
                45_000L, machine.activeDurationMillis());
    }

    @Test
    public void resumingARestoredSessionOpensANewSegmentAndResumesCounting() {
        machine.restore(stored(SessionState.ACTIVE, null, 45_000L, 2));

        assertTrue(machine.resume().isChanged());
        clock.advance(10_000L);

        assertEquals(SessionState.ACTIVE, machine.getState());
        // A new segment, so the fix before the interruption is never joined to the one after it.
        assertEquals(3, machine.summary().getSegment());
        assertEquals(55_000L, machine.activeDurationMillis());
    }

    @Test
    public void aRestoredSessionCanBeFinishedWithoutEverResuming() {
        machine.restore(stored(SessionState.PAUSED, PauseReason.APP_NOT_VISIBLE, 45_000L, 2));

        assertTrue(machine.stop().isChanged());
        assertTrue(machine.onStopPersisted().isChanged());

        assertEquals(SessionState.COMPLETED, machine.getState());
        assertEquals(45_000L, machine.activeDurationMillis());
    }

    @Test
    public void restoringIsIgnoredOnceASessionIsInProgress() {
        machine.start();
        machine.onSessionCreated("session-live");

        assertFalse(machine.restore(stored(SessionState.ACTIVE, null, 45_000L, 2)).isChanged());

        // The live session is the real one; a stale record must not replace it.
        assertEquals("session-live", machine.getSessionId());
        assertEquals(SessionState.ACTIVE, machine.getState());
    }

    @Test
    public void aCompletedRecordIsNotRestorable() {
        try {
            machine.restore(new SessionSummary("session-done", SessionState.COMPLETED, null,
                    1_000L, 45_000L, 2));
            fail("a completed session is history, not unfinished work");
        } catch (IllegalArgumentException expected) {
            // Expected: restoring history would resurrect a journey the user already finished.
        }
    }

    private static SessionSummary stored(SessionState state,
                                         PauseReason reason,
                                         long activeMillis,
                                         int segment) {
        return new SessionSummary("session-7", state, reason, 1_000L, activeMillis, segment);
    }
}
