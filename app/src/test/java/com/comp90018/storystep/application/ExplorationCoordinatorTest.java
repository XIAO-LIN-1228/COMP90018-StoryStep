package com.comp90018.storystep.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Covers the coordinator's job: owning exactly one location subscription for one session, and
 * never letting a torn-down subscription reach a session that has moved on.
 *
 * <p>Most assertions are about {@code registrationCount} rather than about published state,
 * because a duplicate subscription is invisible in the UI and expensive on the battery — it can
 * only be caught by counting registrations.
 */
public class ExplorationCoordinatorTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private FakeClock clock;
    private FakeLocationSource locationSource;
    private InMemorySessionStore sessionStore;
    private ExplorationCoordinator coordinator;
    private RecordingObserver observer;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        locationSource = new FakeLocationSource();
        sessionStore = new InMemorySessionStore(clock);
        coordinator = new ExplorationCoordinator(
                new SessionStateMachine(clock),
                sessionStore,
                locationSource,
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock);
        observer = new RecordingObserver();
        coordinator.addObserver(observer);
    }

    // --- starting ------------------------------------------------------------------------------

    @Test
    public void nothingIsRegisteredBeforeTheUserStarts() {
        becomeUsable();

        assertEquals(0, locationSource.registrationCount());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
    }

    @Test
    public void startRegistersExactlyOneSubscription() {
        becomeUsable();

        coordinator.requestStart();

        assertEquals(1, locationSource.registrationCount());
        assertTrue(locationSource.isRegistered());
        assertEquals(SessionState.ACTIVE, coordinator.summary().getState());
    }

    @Test
    public void twoRapidStartsProduceOneSessionAndOneSubscription() {
        becomeUsable();

        coordinator.requestStart();
        coordinator.requestStart();

        assertEquals("a double tap must not open a second GPS listener",
                1, locationSource.registrationCount());
        assertEquals("session-1", coordinator.summary().getId());
    }

    @Test
    public void startWithoutAccessRegistersNothingAndLeavesNoSession() {
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.DENIED);

        coordinator.requestStart();

        assertEquals(0, locationSource.registrationCount());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
        assertNull(coordinator.summary().getId());
    }

    @Test
    public void startWhileNotVisibleRegistersNothing() {
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);

        coordinator.requestStart();

        assertEquals(0, locationSource.registrationCount());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
    }

    @Test
    public void aFailedRecordLeavesNoSessionAndNoSubscription() {
        ExplorationCoordinator failing = coordinatorWith(new FailingSessionStore());
        failing.onVisibilityChanged(true);
        failing.onLocationAccessChanged(LocationAccess.PRECISE);

        failing.requestStart();

        assertEquals("a session the user believes is recording must never exist without a record",
                SessionState.IDLE, failing.summary().getState());
        assertEquals(0, locationSource.registrationCount());
    }

    // --- pausing -------------------------------------------------------------------------------

    @Test
    public void leavingTheAppUnregistersImmediately() {
        activeSession();

        coordinator.onVisibilityChanged(false);

        assertFalse("foreground-only means the subscription goes with the foreground",
                locationSource.isRegistered());
        assertEquals(SessionState.PAUSED, coordinator.summary().getState());
        assertEquals(PauseReason.APP_NOT_VISIBLE, coordinator.summary().getPauseReason());
    }

    @Test
    public void returningToTheAppDoesNotResumeOnItsOwn() {
        activeSession();
        coordinator.onVisibilityChanged(false);

        coordinator.onVisibilityChanged(true);

        assertFalse("resuming without being asked would collect location the user did not request",
                locationSource.isRegistered());
        assertEquals(SessionState.PAUSED, coordinator.summary().getState());
        assertEquals(1, locationSource.registrationCount());
    }

    @Test
    public void losingPermissionMidSessionPausesAndUnregisters() {
        activeSession();

        coordinator.onLocationAccessChanged(LocationAccess.DENIED);

        assertFalse(locationSource.isRegistered());
        assertEquals(PauseReason.PERMISSION_LOST, coordinator.summary().getPauseReason());
    }

    @Test
    public void aDisabledProviderPausesTheSession() {
        activeSession();

        locationSource.emitUnavailable(LocationSource.Reason.PROVIDER_DISABLED);

        assertEquals(PauseReason.PROVIDER_LOST, coordinator.summary().getPauseReason());
        assertFalse(locationSource.isRegistered());
        assertTrue(observer.unavailableReasons.contains(LocationSource.Reason.PROVIDER_DISABLED));
    }

    @Test
    public void aMissingFixIsReportedWithoutPausingTheSession() {
        activeSession();

        locationSource.emitUnavailable(LocationSource.Reason.NO_RECENT_FIX);

        assertEquals("waiting for a fix is not a reason to end collection",
                SessionState.ACTIVE, coordinator.summary().getState());
        assertTrue(locationSource.isRegistered());
    }

    // --- resuming ------------------------------------------------------------------------------

    @Test
    public void resumeRegistersAgainUnderTheSameSession() {
        activeSession();
        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);

        coordinator.requestResume();

        assertTrue(locationSource.isRegistered());
        assertEquals(2, locationSource.registrationCount());
        assertEquals("session-1", coordinator.summary().getId());
        assertEquals(2, coordinator.summary().getSegment());
    }

    @Test
    public void resumeIsRefusedWhileTheAppIsNotVisible() {
        activeSession();
        coordinator.onVisibilityChanged(false);

        coordinator.requestResume();

        assertFalse(locationSource.isRegistered());
        assertEquals(SessionState.PAUSED, coordinator.summary().getState());
    }

    @Test
    public void resumeIsRefusedWhileAccessIsStillMissing() {
        activeSession();
        coordinator.onLocationAccessChanged(LocationAccess.DENIED);

        coordinator.requestResume();

        assertEquals("resuming into a denied state would only pause straight back",
                SessionState.PAUSED, coordinator.summary().getState());
        assertEquals(1, locationSource.registrationCount());
    }

    @Test
    public void resumeComparesTheNewSegmentAgainstNothingFromBeforeThePause() {
        activeSession();
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        assertEquals(1, observer.samples.size());

        coordinator.onVisibilityChanged(false);
        clock.advance(600_000L);
        coordinator.onVisibilityChanged(true);
        coordinator.requestResume();

        // The same position again. Without resetting the filter this would be discarded as
        // "below the minimum displacement" and the resumed session would look dead.
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 601_000L));

        assertEquals(2, observer.samples.size());
    }

    // --- stopping ------------------------------------------------------------------------------

    @Test
    public void stopUnregistersAndCompletesTheSession() {
        activeSession();

        coordinator.requestStop();

        assertFalse(locationSource.isRegistered());
        assertEquals(SessionState.COMPLETED, coordinator.summary().getState());
    }

    @Test
    public void aSecondStopChangesNothing() {
        activeSession();
        coordinator.requestStop();
        int publishedAfterStop = observer.sessions.size();

        coordinator.requestStop();

        assertEquals(SessionState.COMPLETED, coordinator.summary().getState());
        assertEquals(publishedAfterStop, observer.sessions.size());
    }

    @Test
    public void aPausedSessionCanBeStoppedWithoutResumingIt() {
        activeSession();
        coordinator.onVisibilityChanged(false);

        coordinator.requestStop();

        assertEquals(SessionState.COMPLETED, coordinator.summary().getState());
    }

    @Test
    public void aFailedFinalSaveIsNotPresentedAsACompletedSession() {
        ExplorationCoordinator failing = coordinatorWith(new FailOnFinishSessionStore());
        failing.onVisibilityChanged(true);
        failing.onLocationAccessChanged(LocationAccess.PRECISE);
        failing.requestStart();

        failing.requestStop();

        assertEquals(SessionState.STOPPING, failing.summary().getState());
        assertFalse("collection must end even when the save does not",
                locationSource.isRegistered());
    }

    @Test
    public void stoppingAndStartingAgainOpensASecondSession() {
        activeSession();
        coordinator.requestStop();

        coordinator.requestStart();

        assertEquals("session-2", coordinator.summary().getId());
        assertEquals(2, locationSource.registrationCount());
    }

    // --- late callbacks ------------------------------------------------------------------------

    @Test
    public void aSampleQueuedBeforeStopIsDiscarded() {
        activeSession();
        coordinator.requestStop();

        // The fix was already on its way when the user pressed Stop.
        locationSource.emitToRetiredListener(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertTrue("a stopped session's totals must not move", observer.samples.isEmpty());
    }

    @Test
    public void aSampleFromAPreviousSegmentCannotReachTheNewOne() {
        activeSession();
        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);
        coordinator.requestResume();

        locationSource.emitToRetiredListener(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertTrue(observer.samples.isEmpty());
    }

    // --- sample quality ------------------------------------------------------------------------

    @Test
    public void onlySamplesThatPassTheQualityPolicyArePublished() {
        activeSession();

        // Accuracy 120 m is well beyond the policy limit.
        locationSource.emit(sampleAt(ORIGIN, 120.0f, 1_000L));

        assertTrue("a screen must never be handed a position the device did not observe well enough",
                observer.samples.isEmpty());
    }

    @Test
    public void anAcceptedSampleReachesEveryObserver() {
        RecordingObserver second = new RecordingObserver();
        coordinator.addObserver(second);
        activeSession();

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertEquals(1, observer.samples.size());
        assertEquals(1, second.samples.size());
    }

    @Test
    public void aRemovedObserverStopsReceivingUpdates() {
        activeSession();
        coordinator.removeObserver(observer);

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertTrue(observer.samples.isEmpty());
    }

    @Test
    public void aNewObserverIsGivenTheCurrentSessionImmediately() {
        activeSession();

        RecordingObserver late = new RecordingObserver();
        coordinator.addObserver(late);

        // A screen created mid-session must not start out believing the session is idle.
        assertEquals(1, late.sessions.size());
        assertEquals(SessionState.ACTIVE, late.sessions.get(0).getState());
    }

    // --- helpers -------------------------------------------------------------------------------

    private void becomeUsable() {
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    private void activeSession() {
        becomeUsable();
        coordinator.requestStart();
    }

    private ExplorationCoordinator coordinatorWith(SessionStore store) {
        return new ExplorationCoordinator(
                new SessionStateMachine(clock),
                store,
                locationSource,
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock);
    }

    private static LocationSample sampleAt(GeoPoint position,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        return new LocationSample(position, accuracyMeters, 1_700_000_000_000L, monotonicMillis,
                LocationOrigin.DEVICE);
    }

    private static final class RecordingObserver implements ExplorationCoordinator.Observer {

        final List<SessionSummary> sessions = new ArrayList<>();
        final List<LocationSample> samples = new ArrayList<>();
        final List<LocationSource.Reason> unavailableReasons = new ArrayList<>();

        @Override
        public void onSessionChanged(SessionSummary summary) {
            sessions.add(summary);
        }

        @Override
        public void onSample(LocationSample sample) {
            samples.add(sample);
        }

        @Override
        public void onLocationUnavailable(LocationSource.Reason reason) {
            unavailableReasons.add(reason);
        }
    }

    private static class FailingSessionStore implements SessionStore {
        @Override
        public void create(RepositoryCallback<SessionSummary> callback) {
            callback.onError(new IOException("disk full"));
        }

        @Override
        public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
            callback.onError(new IOException("disk full"));
        }

        @Override
        public void finish(SessionSummary summary, RepositoryCallback<Void> callback) {
            callback.onError(new IOException("disk full"));
        }

        @Override
        public void findUnfinished(RepositoryCallback<SessionSummary> callback) {
            callback.onSuccess(null);
        }
    }

    /** Creates normally so a session exists, then fails the one write that ends it. */
    private final class FailOnFinishSessionStore extends FailingSessionStore {
        @Override
        public void create(RepositoryCallback<SessionSummary> callback) {
            sessionStore.create(callback);
        }

        @Override
        public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }
    }
}
