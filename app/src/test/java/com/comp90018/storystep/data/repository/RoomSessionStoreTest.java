package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.DirectTransactions;
import com.comp90018.storystep.data.local.FakeRoutePointDao;
import com.comp90018.storystep.data.local.FakeVisitDao;
import com.comp90018.storystep.data.local.FakeSessionDao;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Before;
import org.junit.Test;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rules of the durable session store, held to the same contract as
 * {@link InMemorySessionStoreTest} plus the parts only a stored route can have.
 *
 * <p>Runs against fake DAOs on a direct executor, so the assertions are about the store's own
 * decisions. Whether SQLite really rolls a failed transaction back, and whether the migration
 * preserves rows, are device questions and are answered by the instrumented tests.
 */
public class RoomSessionStoreTest {

    private static final Attraction OLD_ARTS = new Attraction(
            "old-arts", "Old Arts Building", AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.", false, new GeoPoint(-37.79810, 144.95910));

    private FakeClock clock;
    private FakeSessionDao sessionDao;
    private FakeRoutePointDao routePointDao;
    private FakeVisitDao visitDao;
    private RoomSessionStore store;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        sessionDao = new FakeSessionDao();
        routePointDao = new FakeRoutePointDao();
        visitDao = new FakeVisitDao();
        store = new RoomSessionStore(new DirectTransactions(), sessionDao, routePointDao, visitDao,
                clock,
                Runnable::run, Runnable::run);
    }

    // --- creation ------------------------------------------------------------------------------

    @Test
    public void aCreatedSessionIsActiveOnItsFirstSegment() {
        SessionSummary created = create();

        assertNotNull(created.getId());
        assertEquals(SessionState.ACTIVE, created.getState());
        assertEquals(1, created.getSegment());
        assertEquals(1_000L, created.getStartWallClockMillis());
        assertEquals(1, sessionDao.size());
    }

    @Test
    public void aSecondCreateIsRejectedWhileOneIsUnfinished() {
        create();

        Result<SessionSummary> second = new Result<>();
        store.create(second);

        assertTrue(second.failed());
        // The rejected create must leave no half-written row behind.
        assertEquals(1, sessionDao.size());
    }

    @Test
    public void aNewSessionCanBeCreatedOnceTheLastOneIsFinished() {
        finish(create());

        SessionSummary second = create();

        assertNotNull(second.getId());
        assertEquals(2, sessionDao.size());
    }

    @Test
    public void aFailedCreateReportsTheErrorRatherThanAnEmptySession() {
        sessionDao.failWrites = new IllegalStateException("disk full");

        Result<SessionSummary> result = new Result<>();
        store.create(result);

        assertTrue(result.failed());
        assertNull(result.value);
    }

    // --- checkpoint and finish -----------------------------------------------------------------

    @Test
    public void aCheckpointStoresTheAccumulatedDurationAndPauseReason() {
        SessionSummary created = create();

        checkpoint(new SessionSummary(created.getId(), SessionState.PAUSED,
                PauseReason.APP_NOT_VISIBLE, created.getStartWallClockMillis(), 45_000L, 1));

        SessionSummary stored = findUnfinished().getSummary();
        assertEquals(45_000L, stored.getActiveDurationMillis());
        assertEquals(SessionState.PAUSED, stored.getState());
        assertEquals(PauseReason.APP_NOT_VISIBLE, stored.getPauseReason());
    }

    @Test
    public void aCheckpointForAnUnknownSessionFails() {
        Result<Void> result = new Result<>();
        store.checkpoint(new SessionSummary("never-created", SessionState.ACTIVE, null, 1_000L,
                0L, 1), result);

        assertTrue(result.failed());
    }

    @Test
    public void aFinishedSessionIsNoLongerUnfinished() {
        finish(create());

        assertNull(findUnfinished());
    }

    @Test
    public void finishingTwiceSucceedsAndKeepsOneRecord() {
        SessionSummary created = create();
        finish(created);
        clock.advance(60_000L);

        Result<Void> second = new Result<>();
        store.finish(created, second);

        assertTrue(second.succeeded());
        assertEquals(1, sessionDao.size());
        // The end time belongs to the finish that actually ended the session, not to the retry.
        assertEquals(1_000L, sessionDao.findById(created.getId()).endWallClockMillis);
    }

    // --- route ---------------------------------------------------------------------------------

    @Test
    public void appendedPointsComeBackInOrderWithTheirDistance() {
        SessionSummary created = create();

        appendRoute(created.getId(), 0, Arrays.asList(point(-37.7981, 144.9591, 1),
                point(-37.7982, 144.9592, 1)), 12.5, 1);

        RestoredSession restored = findUnfinished();
        assertEquals(2, restored.getRoute().getPoints().size());
        assertEquals(12.5, restored.getRoute().getTotalDistanceMeters(), 0.001);
        assertEquals(1, restored.getRoute().getSegmentCount());
        assertEquals(-37.7981, restored.getRoute().getPoints().get(0).getPosition().getLatitude(),
                1e-9);
    }

    @Test
    public void reOfferingPointsThatWereAlreadyStoredDoesNotDuplicateTheTrace() {
        SessionSummary created = create();
        List<RoutePoint> points = Arrays.asList(point(-37.7981, 144.9591, 1),
                point(-37.7982, 144.9592, 1));
        appendRoute(created.getId(), 0, points, 12.5, 1);

        // A caller that never had its append confirmed re-sends the same points.
        appendRoute(created.getId(), 0, points, 12.5, 1);

        assertEquals(2, routePointDao.countBySession(created.getId()));
    }

    @Test
    public void anEmptyAppendStillRecordsTheDistanceAndSegmentCount() {
        SessionSummary created = create();

        appendRoute(created.getId(), 0, Collections.<RoutePoint>emptyList(), 340.0, 3);

        RestoredSession restored = findUnfinished();
        assertEquals(340.0, restored.getRoute().getTotalDistanceMeters(), 0.001);
        assertEquals(3, restored.getRoute().getSegmentCount());
        assertTrue(restored.getRoute().getPoints().isEmpty());
    }

    @Test
    public void aRestoredRouteCarriesNoLastFix() {
        SessionSummary created = create();
        appendRoute(created.getId(), 0, Collections.singletonList(point(-37.7981, 144.9591, 1)),
                5.0, 1);

        // A fix from before the interruption is not a current position, and joining it to the
        // first fix after would invent travel across the gap.
        assertNull(findUnfinished().getRoute().getLastSample());
    }

    @Test
    public void appendingToAnUnknownSessionFails() {
        Result<Void> result = new Result<>();
        store.appendRoute("never-created", 0,
                Collections.singletonList(point(-37.79, 144.96, 1)), 1.0, 1, result);

        assertTrue(result.failed());
        assertEquals(0, routePointDao.totalRows());
    }

    // --- destination ---------------------------------------------------------------------------

    @Test
    public void theDestinationSnapshotSurvivesAsPartOfTheSession() {
        SessionSummary created = create();

        saveDestination(created.getId(), OLD_ARTS);

        Attraction restored = findUnfinished().getDestination();
        assertNotNull(restored);
        assertEquals("old-arts", restored.getId());
        assertEquals("Old Arts Building", restored.getName());
        assertEquals(AttractionCategory.UNIVERSITY_LANDMARK, restored.getCategory());
        assertEquals(-37.79810, restored.getLocation().getLatitude(), 1e-9);
    }

    @Test
    public void clearingTheDestinationLeavesNoPhantomTarget() {
        SessionSummary created = create();
        saveDestination(created.getId(), OLD_ARTS);

        saveDestination(created.getId(), null);

        assertNull(findUnfinished().getDestination());
    }

    // --- discard -------------------------------------------------------------------------------

    @Test
    public void discardingRemovesTheSessionAndItsOwnRoute() {
        SessionSummary created = create();
        appendRoute(created.getId(), 0, Collections.singletonList(point(-37.7981, 144.9591, 1)),
                5.0, 1);

        discard(created.getId());
        // The fake stands in for the schema's ON DELETE CASCADE, which the instrumented test
        // exercises against a real database.
        routePointDao.cascadeDelete(created.getId());

        assertNull(findUnfinished());
        assertEquals(0, sessionDao.size());
        assertEquals(0, routePointDao.totalRows());
    }

    @Test
    public void discardingAnAlreadyRemovedSessionSucceeds() {
        SessionSummary created = create();
        discard(created.getId());

        Result<Void> again = new Result<>();
        store.discard(created.getId(), again);

        assertTrue(again.succeeded());
    }

    @Test
    public void aCompletedSessionCannotBeDiscardedAsIfItWereADraft() {
        SessionSummary created = create();
        finish(created);

        Result<Void> result = new Result<>();
        store.discard(created.getId(), result);

        // History is not a draft; removing it is the deletion flow of REQ-16, not a discard.
        assertTrue(result.failed());
        assertEquals(1, sessionDao.size());
    }

    // --- restoration ---------------------------------------------------------------------------

    @Test
    public void thereIsNothingToRestoreBeforeAnySessionExists() {
        assertNull(findUnfinished());
    }

    @Test
    public void anUnreadableStoredStateIsRestoredAsPausedRatherThanCollecting() {
        SessionSummary created = create();
        // As a row written by a future version of the app would look to this one.
        com.comp90018.storystep.data.local.SessionEntity row = sessionDao.findById(created.getId());
        row.state = "SOME_FUTURE_STATE";
        sessionDao.update(row);

        assertEquals(SessionState.PAUSED, findUnfinished().getSummary().getState());
    }

    // --- helpers -------------------------------------------------------------------------------

    private SessionSummary create() {
        Result<SessionSummary> result = new Result<>();
        store.create(result);
        assertTrue(result.succeeded());
        return result.value;
    }

    private void checkpoint(SessionSummary summary) {
        Result<Void> result = new Result<>();
        store.checkpoint(summary, result);
        assertTrue(result.succeeded());
    }

    private void finish(SessionSummary summary) {
        Result<Void> result = new Result<>();
        store.finish(summary, result);
        assertTrue(result.succeeded());
    }

    private void discard(String sessionId) {
        Result<Void> result = new Result<>();
        store.discard(sessionId, result);
        assertTrue(result.succeeded());
    }

    private void appendRoute(String sessionId,
                             int firstSequence,
                             List<RoutePoint> points,
                             double distanceMeters,
                             int segmentCount) {
        Result<Void> result = new Result<>();
        store.appendRoute(sessionId, firstSequence, new ArrayList<>(points), distanceMeters,
                segmentCount, result);
        assertTrue(result.succeeded());
    }

    private void saveDestination(String sessionId, Attraction destination) {
        Result<Void> result = new Result<>();
        store.saveDestination(sessionId, destination, result);
        assertTrue(result.succeeded());
    }

    private RestoredSession findUnfinished() {
        Result<RestoredSession> result = new Result<>();
        store.findUnfinished(result);
        assertTrue(result.succeeded());
        return result.value;
    }

    private static RoutePoint point(double latitude, double longitude, int segment) {
        return new RoutePoint(new GeoPoint(latitude, longitude), 8.0f, 1_000L, segment);
    }

    /** Captures which callback arm ran; the executors here are direct, so it is already done. */
    private static final class Result<T> implements RepositoryCallback<T> {

        private T value;
        private Throwable error;
        private boolean called;

        @Override
        public void onSuccess(T result) {
            value = result;
            called = true;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            called = true;
        }

        boolean succeeded() {
            return called && error == null;
        }

        boolean failed() {
            return called && error != null;
        }
    }
}
