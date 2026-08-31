package com.comp90018.storystep.domain.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;

import org.junit.Before;
import org.junit.Test;

/**
 * Covers the session transition table. Every case runs on a hand-driven clock, so the durations
 * asserted here are exact rather than approximate.
 */
public class SessionStateMachineTest {

    private FakeClock clock;
    private SessionStateMachine machine;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        machine = new SessionStateMachine(clock);
    }

    // --- start ---------------------------------------------------------------------------------

    @Test
    public void aNewMachineIsIdleWithNoSession() {
        assertEquals(SessionState.IDLE, machine.getState());
        assertNull(machine.getSessionId());
        assertEquals(0L, machine.activeDurationMillis());
    }

    @Test
    public void startMovesToStartingWithoutCreatingASession() {
        assertTrue(machine.start().isChanged());

        assertEquals(SessionState.STARTING, machine.getState());
        // No id yet: nothing may present this as a session that is recording.
        assertNull(machine.getSessionId());
    }

    @Test
    public void aSecondStartWhileStartingIsIgnored() {
        machine.start();

        assertFalse(machine.start().isChanged());
        assertEquals(SessionState.STARTING, machine.getState());
    }

    @Test
    public void aStartWhileActiveIsIgnored() {
        activeSession();

        assertFalse(machine.start().isChanged());
        assertEquals("session-1", machine.getSessionId());
    }

    @Test
    public void creatingTheRecordActivatesTheSessionOnItsFirstSegment() {
        machine.start();

        assertTrue(machine.onSessionCreated("session-1").isChanged());

        assertEquals(SessionState.ACTIVE, machine.getState());
        assertEquals("session-1", machine.getSessionId());
        assertEquals(1, machine.summary().getSegment());
        assertEquals(1_000L, machine.summary().getStartWallClockMillis());
    }

    @Test
    public void aFailedStartLeavesNoSessionBehind() {
        machine.start();

        assertTrue(machine.onStartFailed().isChanged());

        assertEquals(SessionState.IDLE, machine.getState());
        assertNull(machine.getSessionId());
    }

    @Test
    public void sessionCreationIsIgnoredUnlessAStartIsInFlight() {
        assertFalse(machine.onSessionCreated("session-1").isChanged());
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    public void anEmptySessionIdIsRejected() {
        machine.start();
        try {
            machine.onSessionCreated("");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("id"));
        }
    }

    // --- pause and resume ----------------------------------------------------------------------

    @Test
    public void pausingRecordsTheReasonAndStopsCollecting() {
        activeSession();

        assertTrue(machine.pause(PauseReason.APP_NOT_VISIBLE).isChanged());

        assertEquals(SessionState.PAUSED, machine.getState());
        assertEquals(PauseReason.APP_NOT_VISIBLE, machine.summary().getPauseReason());
        assertFalse(machine.getState().isCollecting());
    }

    @Test
    public void aSecondPauseKeepsTheOriginalReason() {
        activeSession();
        machine.pause(PauseReason.APP_NOT_VISIBLE);

        assertFalse(machine.pause(PauseReason.PERMISSION_LOST).isChanged());

        // The screen is already explaining why it paused; changing the reason underneath would
        // rewrite a message the user has read.
        assertEquals(PauseReason.APP_NOT_VISIBLE, machine.summary().getPauseReason());
    }

    @Test
    public void pauseIsIgnoredWhileStartingBecauseThereIsNoSessionYet() {
        machine.start();

        assertFalse(machine.pause(PauseReason.APP_NOT_VISIBLE).isChanged());
        assertEquals(SessionState.STARTING, machine.getState());
    }

    @Test
    public void resumeOpensANewSegmentUnderTheSameSession() {
        activeSession();
        machine.pause(PauseReason.APP_NOT_VISIBLE);

        assertTrue(machine.resume().isChanged());

        assertEquals(SessionState.ACTIVE, machine.getState());
        assertEquals("session-1", machine.getSessionId());
        // A new segment, so nothing later joins the position before the pause to the one after it.
        assertEquals(2, machine.summary().getSegment());
        assertNull(machine.summary().getPauseReason());
    }

    @Test
    public void resumeIsIgnoredWhenNotPaused() {
        activeSession();

        assertFalse(machine.resume().isChanged());
        assertEquals(1, machine.summary().getSegment());
    }

    @Test
    public void startCannotReplaceAPausedSession() {
        activeSession();
        machine.pause(PauseReason.APP_NOT_VISIBLE);

        assertFalse(machine.start().isChanged());

        // At most one unfinished session: an interrupted one is resumed, never replaced.
        assertEquals(SessionState.PAUSED, machine.getState());
        assertEquals("session-1", machine.getSessionId());
    }

    // --- duration ------------------------------------------------------------------------------

    @Test
    public void activeDurationExcludesPausedTime() {
        activeSession();
        clock.advance(30_000L);

        machine.pause(PauseReason.APP_NOT_VISIBLE);
        clock.advance(3_600_000L);

        // An hour in the background is not an hour of exploring.
        assertEquals(30_000L, machine.activeDurationMillis());

        machine.resume();
        clock.advance(10_000L);

        assertEquals(40_000L, machine.activeDurationMillis());
    }

    @Test
    public void durationIsUnaffectedByAWallClockJump() {
        activeSession();
        clock.advance(20_000L);

        // The device syncs time and jumps an hour backwards mid-session.
        clock.shiftWallClock(-3_600_000L);

        assertEquals(20_000L, machine.activeDurationMillis());
    }

    @Test
    public void durationKeepsRunningWhileActive() {
        activeSession();

        assertEquals(0L, machine.activeDurationMillis());
        clock.advance(5_000L);
        assertEquals(5_000L, machine.activeDurationMillis());
    }

    @Test
    public void aRestartedSessionBeginsFromZero() {
        activeSession();
        clock.advance(60_000L);
        machine.stop();
        machine.onStopPersisted();

        machine.start();
        machine.onSessionCreated("session-2");

        assertEquals(0L, machine.activeDurationMillis());
        assertEquals(1, machine.summary().getSegment());
    }

    // --- stop ----------------------------------------------------------------------------------

    @Test
    public void stopFreezesTheDurationBeforeTheSaveCompletes() {
        activeSession();
        clock.advance(15_000L);

        assertTrue(machine.stop().isChanged());
        clock.advance(9_000L);

        assertEquals(SessionState.STOPPING, machine.getState());
        assertEquals(15_000L, machine.activeDurationMillis());
    }

    @Test
    public void stopWorksFromPaused() {
        activeSession();
        machine.pause(PauseReason.PERMISSION_LOST);

        assertTrue(machine.stop().isChanged());
        assertEquals(SessionState.STOPPING, machine.getState());
    }

    @Test
    public void aSecondStopIsANoOp() {
        activeSession();
        machine.stop();

        assertFalse(machine.stop().isChanged());
        assertEquals(SessionState.STOPPING, machine.getState());
    }

    @Test
    public void stopIsIgnoredWhenThereIsNoSession() {
        assertFalse(machine.stop().isChanged());
        assertEquals(SessionState.IDLE, machine.getState());
    }

    @Test
    public void theSessionCompletesOnlyAfterItIsPersisted() {
        activeSession();
        machine.stop();

        assertEquals(SessionState.STOPPING, machine.getState());
        assertTrue(machine.onStopPersisted().isChanged());
        assertEquals(SessionState.COMPLETED, machine.getState());
    }

    @Test
    public void aFailedSaveKeepsTheSessionStoppingSoItCanBeRetried() {
        activeSession();
        machine.stop();

        machine.onStopFailed();

        // Not COMPLETED: a session that was never written must not look like a finished one.
        assertEquals(SessionState.STOPPING, machine.getState());
        assertTrue(machine.hasStopSaveFailed());

        machine.onStopPersisted();
        assertEquals(SessionState.COMPLETED, machine.getState());
        assertFalse(machine.hasStopSaveFailed());
    }

    @Test
    public void aCompletedSessionCanBeFollowedByANewOne() {
        activeSession();
        machine.stop();
        machine.onStopPersisted();

        assertTrue(machine.start().isChanged());
        assertEquals(SessionState.STARTING, machine.getState());
        assertNull(machine.getSessionId());
    }

    // --- summary -------------------------------------------------------------------------------

    @Test
    public void theSummaryReportsTheLiveDuration() {
        activeSession();
        clock.advance(7_500L);

        assertEquals(7_500L, machine.summary().getActiveDurationMillis());
        assertEquals(SessionState.ACTIVE, machine.summary().getState());
        assertNotNull(machine.summary().getId());
    }

    private void activeSession() {
        machine.start();
        machine.onSessionCreated("session-1");
    }
}
