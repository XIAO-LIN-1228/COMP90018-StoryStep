package com.comp90018.storystep.domain.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.MotionSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * The classifier against the documented fixtures.
 *
 * <p>The rotation fixture is the important one. A phone turned in the hand produces large per-axis
 * changes while the user does not move a step, and a classifier that watched raw axes instead of
 * the variation of linear acceleration would call it walking — inflating every reported walking
 * time on a device that spends its life being picked up and put down.
 */
public class MovementClassifierTest {

    private MovementClassifier classifier;

    @Before
    public void setUp() {
        classifier = new MovementClassifier();
    }

    // --- fixtures ------------------------------------------------------------------------------

    @Test
    public void aStillPhoneIsStationary() {
        MovementSnapshot snapshot = feed(MotionFixtures.stationary(10_000L));

        assertEquals(MovementState.STATIONARY, snapshot.getState());
    }

    @Test
    public void aWalkingCadenceIsMoving() {
        MovementSnapshot snapshot = feed(MotionFixtures.walking(10_000L, 0L));

        assertEquals(MovementState.MOVING, snapshot.getState());
    }

    @Test
    public void turningThePhoneWithoutWalkingIsNotMoving() {
        MovementSnapshot snapshot = feed(MotionFixtures.rotationWithoutWalking(10_000L));

        assertEquals("rotating a phone is not travel and must never be counted as walking",
                MovementState.STATIONARY, snapshot.getState());
        assertEquals(0L, snapshot.getMovingMillis());
    }

    @Test
    public void borderlineNoiseDoesNotOverturnTheCurrentLabel() {
        feed(MotionFixtures.walking(10_000L, 0L));
        assertEquals(MovementState.MOVING, classifier.snapshot().getState());

        MovementSnapshot snapshot = feed(MotionFixtures.borderlineNoise(6_000L, 10_000L));

        assertEquals("inside the hysteresis band the previous label stands",
                MovementState.MOVING, snapshot.getState());
    }

    // --- unknown and windows -------------------------------------------------------------------

    @Test
    public void theStateIsUnknownBeforeTheFirstWindowCloses() {
        feed(MotionFixtures.stationary(1_000L));

        assertEquals("a partial window is not evidence of standing still",
                MovementState.UNKNOWN, classifier.snapshot().getState());
    }

    @Test
    public void aFreshClassifierReportsUnknownRatherThanStationary() {
        assertEquals(MovementState.UNKNOWN, classifier.snapshot().getState());
    }

    @Test
    public void aLongGapRestartsTheWindowInsteadOfMixingReadings() {
        feed(MotionFixtures.walking(10_000L, 0L));

        // The phone was in a pocket with the screen off for a minute; these readings are unrelated.
        classifier.offer(new MotionSample(0.0f, 0.0f, MotionFixtures.GRAVITY, 70_000L));

        assertEquals("the window across the gap must not be judged",
                0L, classifier.snapshot().getStationaryMillis());
    }

    // --- durations -----------------------------------------------------------------------------

    @Test
    public void durationsFollowTheWindowTimestamps() {
        feed(MotionFixtures.walking(10_000L, 0L));
        MovementSnapshot snapshot = classifier.snapshot();

        assertTrue("ten seconds of walking must record several seconds of moving time",
                snapshot.getMovingMillis() >= 6_000L);
        assertTrue(snapshot.getMovingMillis() <= 10_000L);
        assertEquals(0L, snapshot.getStationaryMillis());
    }

    @Test
    public void bothStatesAccumulateAcrossATransition() {
        feed(MotionFixtures.stationary(10_000L));
        long stationary = classifier.snapshot().getStationaryMillis();

        feed(MotionFixtures.walking(10_000L, 10_000L));
        MovementSnapshot snapshot = classifier.snapshot();

        assertTrue(stationary > 0L);
        assertTrue("time already spent standing still does not disappear when the user sets off",
                snapshot.getStationaryMillis() >= stationary);
        assertTrue("the dwell may attribute a window or two of the transition to standing still",
                snapshot.getStationaryMillis() - stationary <= 3 * MovementClassifier.WINDOW_MILLIS);
        assertTrue(snapshot.getMovingMillis() > 0L);
    }

    @Test
    public void aTransitionIsPickedUpWithinAFewSeconds() {
        feed(MotionFixtures.stationary(10_000L));

        feed(MotionFixtures.walking(6_000L, 10_000L));

        assertEquals(MovementState.MOVING, classifier.snapshot().getState());
    }

    // --- resetting -----------------------------------------------------------------------------

    @Test
    public void resetForgetsTheWindowsButKeepsTheDurations() {
        feed(MotionFixtures.walking(10_000L, 0L));
        long moving = classifier.snapshot().getMovingMillis();

        classifier.reset();
        MovementSnapshot snapshot = classifier.snapshot();

        assertEquals("a pause makes the current reading unknown, not the past minutes untrue",
                MovementState.UNKNOWN, snapshot.getState());
        assertEquals(moving, snapshot.getMovingMillis());
    }

    @Test
    public void clearForgetsEverythingForANewSession() {
        feed(MotionFixtures.walking(10_000L, 0L));

        classifier.clear();
        MovementSnapshot snapshot = classifier.snapshot();

        assertEquals(MovementState.UNKNOWN, snapshot.getState());
        assertEquals(0L, snapshot.getMovingMillis());
        assertEquals(0L, snapshot.getStationaryMillis());
    }

    @Test
    public void readingsAfterAResetAreNotComparedWithThoseBeforeIt() {
        feed(MotionFixtures.walking(10_000L, 0L));
        classifier.reset();

        MovementSnapshot snapshot = feed(MotionFixtures.stationary(10_000L));

        assertEquals(MovementState.STATIONARY, snapshot.getState());
    }

    private MovementSnapshot feed(List<MotionSample> samples) {
        MovementSnapshot snapshot = classifier.snapshot();
        for (MotionSample sample : samples) {
            snapshot = classifier.offer(sample);
        }
        return snapshot;
    }
}
