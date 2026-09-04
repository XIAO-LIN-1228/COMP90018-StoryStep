package com.comp90018.storystep.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * How a detected arrival becomes a recorded visit, and the cases where it must not.
 *
 * <p>The most important test here is {@link #standingStillAtTheDestinationStillArrives}. The route
 * filter drops sub-three-metre movement as receiver noise, which is exactly what a stationary
 * phone produces, so an arrival detector placed behind that filter would fail precisely when the
 * user reaches the place. This asserts the detector is fed the raw fix instead.
 */
public class ExplorationCoordinatorArrivalTest {

    private static final double METERS_PER_DEGREE_LATITUDE = 111_195.0;

    private static final GeoPoint TARGET_POSITION = new GeoPoint(-37.79810, 144.95910);

    private static final Attraction TARGET = new Attraction(
            "old-arts",
            "Old Arts Building",
            AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.",
            false,
            TARGET_POSITION);

    private static final Attraction OTHER_TARGET = new Attraction(
            "melbourne-museum",
            "Melbourne Museum",
            AttractionCategory.MUSEUM,
            "The Carlton Gardens museum.",
            true,
            new GeoPoint(-37.80330, 144.97170));

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
        coordinator = TestCoordinators.with(clock, locationSource, SensingSources.none(),
                sessionStore);
        observer = new RecordingObserver();
        coordinator.addObserver(observer);
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    // --- the happy path ------------------------------------------------------------------------

    @Test
    public void aSustainedStayAtTheDestinationRecordsOneVisit() {
        startTowards(TARGET);

        walkThroughArrival();

        assertEquals(1, observer.pendingStories.size());
        Visit recorded = coordinator.getPendingStory();
        assertNotNull(recorded);
        assertEquals("old-arts", recorded.getAttractionId());
        assertEquals("Old Arts Building", recorded.getAttractionName());
        assertTrue(recorded.isUnread());
    }

    @Test
    public void theVisitSnapshotsTheNameAndStoryAsTheyWereOnArrival() {
        startTowards(TARGET);

        walkThroughArrival();

        Visit recorded = coordinator.getPendingStory();
        assertEquals("The 1924 clocktower building.", recorded.getStory());
    }

    /**
     * The regression that guards the placement of the detector in the sample path.
     *
     * <p>Every fix after the first is well within the route filter's minimum displacement, so all
     * of them are rejected as noise for route purposes. The arrival must happen anyway.
     */
    @Test
    public void standingStillAtTheDestinationStillArrives() {
        startTowards(TARGET);

        // A single centimetre of jitter, five seconds apart: nothing the route would accept.
        emit(0.00, 5.0f, 0L);
        emit(0.01, 5.0f, 5_000L);
        emit(0.00, 5.0f, 10_000L);

        assertNotNull("standing at the destination must still count as arriving",
                coordinator.getPendingStory());
    }

    // --- when it must not fire -----------------------------------------------------------------

    @Test
    public void approximateAccessDoesNotDetectArrival() {
        startTowards(TARGET);
        coordinator.onLocationAccessChanged(LocationAccess.APPROXIMATE);

        walkThroughArrival();

        assertNull("coarse permission cannot support an automatic arrival claim",
                coordinator.getPendingStory());
    }

    @Test
    public void withNoDestinationThereIsNothingToArriveAt() {
        coordinator.requestStart();

        walkThroughArrival();

        assertNull(coordinator.getPendingStory());
    }

    @Test
    public void aPausedSessionDoesNotDetectArrival() {
        startTowards(TARGET);
        coordinator.onVisibilityChanged(false);

        // Late deliveries from the subscription that has just been torn down.
        locationSource.emitToRetiredListener(sampleAt(0.0, 5.0f, 0L));

        assertNull(coordinator.getPendingStory());
    }

    @Test
    public void passingByWithoutStayingDoesNotArrive() {
        startTowards(TARGET);

        // Close, then gone again before the dwell is satisfied.
        emit(10.0, 5.0f, 0L);
        emit(12.0, 5.0f, 4_000L);
        emit(500.0, 5.0f, 8_000L);
        emit(900.0, 5.0f, 12_000L);

        assertNull(coordinator.getPendingStory());
    }

    // --- exactly once --------------------------------------------------------------------------

    @Test
    public void stayingLongerDoesNotRecordASecondVisit() {
        startTowards(TARGET);
        walkThroughArrival();

        for (long t = 15_000L; t <= 60_000L; t += 5_000L) {
            emit(8.0, 5.0f, t);
        }

        assertEquals(1, observer.pendingStories.size());
    }

    @Test
    public void leavingAndReturningInTheSameSessionRecordsOneVisit() {
        startTowards(TARGET);
        walkThroughArrival();
        coordinator.consumeArrival();

        // Away, then back, and a full second stay.
        emit(800.0, 5.0f, 20_000L);
        emit(8.0, 5.0f, 40_000L);
        emit(8.0, 5.0f, 45_000L);
        emit(8.0, 5.0f, 50_000L);

        // The duplicate is recognised as already recorded, so nothing is announced a second time.
        assertNull(coordinator.getPendingStory());
        assertFalse("a duplicate is not a failure", coordinator.hasVisitSaveFailed());
    }

    @Test
    public void consumingTheArrivalDoesNotDiscardTheVisit() {
        startTowards(TARGET);
        walkThroughArrival();

        coordinator.consumeArrival();

        assertNull(coordinator.getPendingStory());
        Result<Visit> unread = new Result<>();
        sessionStore.findUnreadVisit(coordinator.summary().getId(), unread);
        assertNotNull("dismissing the prompt is not reading the story", unread.value);
    }

    // --- failures ------------------------------------------------------------------------------

    @Test
    public void aFailedWriteIsReportedAndNeverAnnouncedAsASavedVisit() {
        useFailingStore();
        startTowards(TARGET);

        walkThroughArrival();

        assertNull("nothing was written, so there is no story to offer",
                coordinator.getPendingStory());
        assertTrue(coordinator.hasVisitSaveFailed());
        assertTrue(observer.visitSaveFailures.contains(Boolean.TRUE));
    }

    @Test
    public void aRetryAfterTheStoreRecoversRecordsTheVisitOnce() {
        FailableSessionStore store = useFailingStore();
        startTowards(TARGET);
        walkThroughArrival();
        assertTrue(coordinator.hasVisitSaveFailed());

        store.failing = false;
        coordinator.retryVisit();

        assertNotNull(coordinator.getPendingStory());
        assertFalse(coordinator.hasVisitSaveFailed());
        assertEquals(1, store.recorded.size());
    }

    @Test
    public void retryingWithNothingToRetryDoesNothing() {
        startTowards(TARGET);

        coordinator.retryVisit();

        assertNull(coordinator.getPendingStory());
        assertFalse(coordinator.hasVisitSaveFailed());
    }

    // --- target changes ------------------------------------------------------------------------

    @Test
    public void switchingDestinationMidStayDoesNotManufactureAnArrival() {
        startTowards(TARGET);
        emit(8.0, 5.0f, 0L);
        emit(8.0, 5.0f, 5_000L);

        coordinator.selectDestination(OTHER_TARGET);
        emit(8.0, 5.0f, 10_000L);

        assertNull(coordinator.getPendingStory());
    }

    @Test
    public void resumingASessionStartsTheStayAgain() {
        startTowards(TARGET);
        emit(8.0, 5.0f, 0L);
        emit(8.0, 5.0f, 5_000L);

        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);
        coordinator.requestResume();

        // One fix after the resume is not a stay, however close it is.
        emit(8.0, 5.0f, 10_000L);
        assertNull(coordinator.getPendingStory());

        emit(8.0, 5.0f, 15_000L);
        emit(8.0, 5.0f, 20_000L);
        assertNotNull(coordinator.getPendingStory());
    }

    @Test
    public void aNewSessionDoesNotInheritThePreviousStory() {
        startTowards(TARGET);
        walkThroughArrival();
        assertNotNull(coordinator.getPendingStory());

        coordinator.requestStop();
        coordinator.requestStart(TARGET);

        assertNull("a story from the previous walk is history, not this walk's news",
                coordinator.getPendingStory());
    }

    @Test
    public void discardingASessionRemovesItsPendingStory() {
        startTowards(TARGET);
        walkThroughArrival();

        coordinator.requestDiscard();

        assertNull(coordinator.getPendingStory());
    }

    // --- recovery ------------------------------------------------------------------------------

    @Test
    public void anInterruptedSessionOffersItsUnreadStoryAgainWithoutRecordingIt() {
        RestoringSessionStore store = new RestoringSessionStore();
        coordinator = TestCoordinators.with(clock, locationSource, SensingSources.none(), store);
        observer = new RecordingObserver();
        coordinator.addObserver(observer);

        coordinator.restoreInterruptedSession();

        Visit restored = coordinator.getPendingStory();
        assertNotNull(restored);
        assertEquals("old-arts", restored.getAttractionId());
        assertEquals("recovery must not write a second visit", 0, store.recordCount);
    }

    // --- helpers -------------------------------------------------------------------------------

    private void startTowards(Attraction attraction) {
        coordinator.requestStart(attraction);
    }

    /** Three fixes at the destination, five seconds apart: the FX-ARRIVAL qualifying sequence. */
    private void walkThroughArrival() {
        emit(8.0, 5.0f, 0L);
        emit(8.0, 5.0f, 5_000L);
        emit(8.0, 5.0f, 10_000L);
    }

    private void emit(double metresNorth, float accuracyMeters, long monotonicMillis) {
        clock.advance(monotonicMillis - (clock.monotonicMillis() - 1_000L));
        locationSource.emit(sampleAt(metresNorth, accuracyMeters, monotonicMillis));
    }

    private static LocationSample sampleAt(double metresNorth,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        GeoPoint position = new GeoPoint(
                TARGET_POSITION.getLatitude() + metresNorth / METERS_PER_DEGREE_LATITUDE,
                TARGET_POSITION.getLongitude());
        return new LocationSample(position, accuracyMeters, 1_000L + monotonicMillis,
                1_000L + monotonicMillis, LocationOrigin.DEVICE);
    }

    private FailableSessionStore useFailingStore() {
        FailableSessionStore store = new FailableSessionStore(clock);
        coordinator = TestCoordinators.with(clock, locationSource, SensingSources.none(), store);
        observer = new RecordingObserver();
        coordinator.addObserver(observer);
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        return store;
    }

    /** Collects a callback's values so a test can count announcements, not just read the latest. */
    private static final class RecordingObserver implements ExplorationCoordinator.Observer {

        final List<Visit> pendingStories = new ArrayList<>();
        final List<Boolean> visitSaveFailures = new ArrayList<>();

        @Override
        public void onSessionChanged(SessionSummary summary) {
        }

        @Override
        public void onSample(LocationSample sample) {
        }

        @Override
        public void onLocationUnavailable(LocationSource.Reason reason) {
        }

        @Override
        public void onPendingStoryChanged(Visit pendingStory) {
            if (pendingStory != null) {
                pendingStories.add(pendingStory);
            }
        }

        @Override
        public void onVisitSaveFailed(boolean failed) {
            visitSaveFailures.add(failed);
        }
    }

    /** A real store whose visit writes can be made to fail, and unfailed again. */
    private static final class FailableSessionStore implements SessionStore {

        private final InMemorySessionStore delegate;

        final List<Visit> recorded = new ArrayList<>();

        boolean failing = true;

        FailableSessionStore(FakeClock clock) {
            this.delegate = new InMemorySessionStore(clock);
        }

        @Override
        public void recordVisit(Visit visit, RepositoryCallback<Visit> callback) {
            if (failing) {
                callback.onError(new IOException("disk full"));
                return;
            }
            recorded.add(visit);
            delegate.recordVisit(visit, callback);
        }

        @Override
        public void create(RepositoryCallback<SessionSummary> callback) {
            delegate.create(callback);
        }

        @Override
        public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
            delegate.checkpoint(summary, callback);
        }

        @Override
        public void appendRoute(String sessionId,
                                int firstSequence,
                                List<RoutePoint> newPoints,
                                double totalDistanceMeters,
                                int segmentCount,
                                RepositoryCallback<Void> callback) {
            delegate.appendRoute(sessionId, firstSequence, newPoints, totalDistanceMeters,
                    segmentCount, callback);
        }

        @Override
        public void saveDestination(String sessionId,
                                    Attraction destination,
                                    RepositoryCallback<Void> callback) {
            delegate.saveDestination(sessionId, destination, callback);
        }

        @Override
        public void finish(SessionSummary summary, RepositoryCallback<Void> callback) {
            delegate.finish(summary, callback);
        }

        @Override
        public void discard(String sessionId, RepositoryCallback<Void> callback) {
            delegate.discard(sessionId, callback);
        }

        @Override
        public void findUnreadVisit(String sessionId, RepositoryCallback<Visit> callback) {
            delegate.findUnreadVisit(sessionId, callback);
        }

        @Override
        public void findVisit(String sessionId,
                              String attractionId,
                              RepositoryCallback<Visit> callback) {
            delegate.findVisit(sessionId, attractionId, callback);
        }

        @Override
        public void markVisitRead(String sessionId,
                                  String attractionId,
                                  RepositoryCallback<Void> callback) {
            delegate.markVisitRead(sessionId, attractionId, callback);
        }

        @Override
        public void findUnfinished(RepositoryCallback<RestoredSession> callback) {
            delegate.findUnfinished(callback);
        }
    }

    /** A store that reports an interrupted session which already holds an unread visit. */
    private static final class RestoringSessionStore implements SessionStore {

        private static final String SESSION_ID = "interrupted-session";

        int recordCount;

        @Override
        public void findUnfinished(RepositoryCallback<RestoredSession> callback) {
            SessionSummary summary = new SessionSummary(SESSION_ID, SessionState.PAUSED,
                    com.comp90018.storystep.domain.model.PauseReason.PROCESS_INTERRUPTED,
                    1_000L, 60_000L, 1);
            callback.onSuccess(
                    new RestoredSession(summary, RouteSnapshot.empty(), TARGET));
        }

        @Override
        public void findUnreadVisit(String sessionId, RepositoryCallback<Visit> callback) {
            callback.onSuccess(new Visit(SESSION_ID, "old-arts", 2_000L, "Old Arts Building",
                    "The 1924 clocktower building.", true));
        }

        @Override
        public void findVisit(String sessionId,
                              String attractionId,
                              RepositoryCallback<Visit> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void recordVisit(Visit visit, RepositoryCallback<Visit> callback) {
            recordCount++;
            callback.onSuccess(visit);
        }

        @Override
        public void create(RepositoryCallback<SessionSummary> callback) {
            callback.onError(new IOException("not used"));
        }

        @Override
        public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void appendRoute(String sessionId,
                                int firstSequence,
                                List<RoutePoint> newPoints,
                                double totalDistanceMeters,
                                int segmentCount,
                                RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void saveDestination(String sessionId,
                                    Attraction destination,
                                    RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void finish(SessionSummary summary, RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void discard(String sessionId, RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }

        @Override
        public void markVisitRead(String sessionId,
                                  String attractionId,
                                  RepositoryCallback<Void> callback) {
            callback.onSuccess(null);
        }
    }

    /** Captures a synchronous callback's value, like the other store tests do. */
    private static final class Result<T> implements RepositoryCallback<T> {

        T value;

        @Override
        public void onSuccess(T result) {
            value = result;
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}
