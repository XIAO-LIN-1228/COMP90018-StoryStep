package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.FakeRoutePointDao;
import com.comp90018.storystep.data.local.FakeSessionDao;
import com.comp90018.storystep.data.local.FakeVisitDao;
import com.comp90018.storystep.data.local.RoutePointEntity;
import com.comp90018.storystep.data.local.SessionEntity;
import com.comp90018.storystep.data.local.VisitEntity;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.RouteSegment;
import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.ActiveSessionNotDeletableException;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionNotFoundException;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Covers REQ-16.4: what history must get right about empty and large collections, ordering,
 * unknown identifiers, and deletion that removes exactly what the user asked it to.
 *
 * <p>Every case drives the production repository over fake DAOs with direct executors, so the
 * rules under test are the ones that ship. The one thing these cannot prove is the real
 * {@code ON DELETE CASCADE}, which belongs to SQLite; {@code SessionHistoryDaoTest} covers that on
 * a device.
 */
public class RoomHistoryRepositoryTest {

    private static final double LATITUDE = -37.79630;
    private static final double LONGITUDE = 144.96140;

    private FakeSessionDao sessions;
    private FakeRoutePointDao routePoints;
    private FakeVisitDao visits;
    private RoomHistoryRepository repository;

    @Before
    public void setUp() {
        sessions = new FakeSessionDao();
        routePoints = new FakeRoutePointDao();
        visits = new FakeVisitDao();
        repository = TestHistory.over(sessions, routePoints, visits);
    }

    // --- reading -------------------------------------------------------------------------------

    @Test
    public void emptyHistoryReportsAnEmptyListRatherThanAnError() {
        List<SessionHistoryEntry> entries = loadHistory();

        assertNotNull("an empty database is a successful read, not a failure", entries);
        assertTrue(entries.isEmpty());
    }

    @Test
    public void onlyFinishedSessionsAppearInHistory() {
        storeSession("finished", 1_000L, 5_000L, true);
        storeSession("running", 9_000L, 0L, false);

        List<SessionHistoryEntry> entries = loadHistory();

        assertEquals("a walk that is still happening is not history", 1, entries.size());
        assertEquals("finished", entries.get(0).getId());
    }

    @Test
    public void historyIsOrderedByMostRecentlyEndedFirst() {
        storeSession("oldest", 1_000L, 2_000L, true);
        storeSession("newest", 8_000L, 9_000L, true);
        storeSession("middle", 4_000L, 5_000L, true);

        List<SessionHistoryEntry> entries = loadHistory();

        assertEquals("newest", entries.get(0).getId());
        assertEquals("middle", entries.get(1).getId());
        assertEquals("oldest", entries.get(2).getId());
    }

    @Test
    public void sessionsThatEndedInTheSameMillisecondStillHaveADeterministicOrder() {
        storeSession("b-session", 1_000L, 9_000L, true);
        storeSession("a-session", 1_000L, 9_000L, true);

        List<SessionHistoryEntry> first = loadHistory();
        List<SessionHistoryEntry> second = loadHistory();

        assertEquals("a tie must not leave the order up to the database",
                "a-session", first.get(0).getId());
        assertEquals(first.get(0).getId(), second.get(0).getId());
        assertEquals(first.get(1).getId(), second.get(1).getId());
    }

    @Test
    public void aLargeHistoryIsReadWithOneQueryAndKeepsItsVisitCounts() {
        for (int index = 0; index < 200; index++) {
            String id = String.format("session-%03d", index);
            storeSession(id, 1_000L + index, 2_000L + index, true);
            sessions.visitCounts.put(id, index % 5);
        }

        List<SessionHistoryEntry> entries = loadHistory();

        assertEquals(200, entries.size());
        assertEquals("the newest session is first however many there are",
                "session-199", entries.get(0).getId());
        assertEquals("visit counts must survive the projection",
                199 % 5, entries.get(0).getVisitCount());
    }

    @Test
    public void aSessionWithNoVisitsStillAppears() {
        storeSession("quiet-walk", 1_000L, 2_000L, true);

        List<SessionHistoryEntry> entries = loadHistory();

        assertEquals(1, entries.size());
        assertEquals("a walk where nothing was reached is still a walk",
                0, entries.get(0).getVisitCount());
    }

    @Test
    public void interruptedAndGappedSessionsCarryTheirLabels() {
        SessionEntity session = storeSession("rough-walk", 1_000L, 9_000L, true);
        session.interrupted = true;
        session.segmentCount = 3;
        sessions.update(session);

        SessionHistoryEntry entry = loadHistory().get(0);

        assertTrue("an interrupted walk must keep saying so after it finishes",
                entry.isInterrupted());
        assertTrue("more than one recorded span means the route has gaps", entry.hasRouteGaps());
        assertEquals(3, entry.getSegmentCount());
    }

    // --- detail --------------------------------------------------------------------------------

    @Test
    public void anUnknownSessionIdIsReportedAsNotFound() {
        Throwable error = loadDetailError("never-existed");

        assertTrue("an invalid id must be a typed failure the screen can act on",
                error instanceof SessionNotFoundException);
    }

    @Test
    public void anUnfinishedSessionIsNotOpenableFromHistory() {
        storeSession("running", 1_000L, 0L, false);

        Throwable error = loadDetailError("running");

        assertTrue("a walk in progress is not something history can show",
                error instanceof SessionNotFoundException);
    }

    @Test
    public void detailCarriesVisitsRouteSegmentsMovementAndWeather() {
        SessionEntity session = storeSession("walk", 1_000L, 9_000L, true);
        session.movementState = MovementState.MOVING.name();
        session.movementMovingMillis = 120_000L;
        session.movementStationaryMillis = 30_000L;
        session.weatherAreaKey = "-37.80,144.95";
        session.weatherCondition = WeatherCondition.CLOUDY.name();
        session.weatherTemperatureCelsius = 14.5;
        session.weatherObservedAtMillis = 4_000L;
        session.weatherFetchedAtMillis = 4_100L;
        sessions.update(session);
        storeVisit("walk", "old-arts", 5_000L);
        storeRoutePoint("walk", 0, 1);
        storeRoutePoint("walk", 1, 1);
        storeRoutePoint("walk", 2, 2);

        SessionHistoryDetail detail = loadDetail("walk");

        assertEquals(1, detail.getVisits().size());
        assertEquals("old-arts", detail.getVisits().get(0).getAttractionId());
        assertEquals("the trace is split at the collection boundary, not joined across it",
                2, detail.getSegments().size());
        assertEquals(2, detail.getSegments().get(0).size());
        assertEquals(1, detail.getSegments().get(1).size());
        assertEquals(MovementState.MOVING, detail.getMovement().getState());
        assertTrue(detail.hasWeather());
        assertEquals(WeatherCondition.CLOUDY, detail.getWeather().getCondition());
    }

    @Test
    public void aSessionWithNoRecordedWeatherReportsNoneRatherThanZeroDegrees() {
        storeSession("walk", 1_000L, 9_000L, true);

        SessionHistoryDetail detail = loadDetail("walk");

        assertFalse("a null area key is how 'no weather was recorded' is spelled",
                detail.hasWeather());
        assertNull(detail.getWeather());
    }

    @Test
    public void aSessionRecordedBeforeMovementWasStoredReportsUnavailable() {
        // A row migrated forward from version 4: its movement columns are the migration's
        // defaults, not something a session measured.
        storeSession("old-walk", 1_000L, 9_000L, true);

        SessionHistoryDetail detail = loadDetail("old-walk");

        assertEquals("movement that was never recorded must not read as a motionless walk",
                MovementState.UNAVAILABLE, detail.getMovement().getState());
    }

    @Test
    public void aSessionWithNoRouteHasNoSegments() {
        storeSession("walk", 1_000L, 9_000L, true);

        SessionHistoryDetail detail = loadDetail("walk");

        assertFalse(detail.hasRoute());
        assertEquals(Collections.<RouteSegment>emptyList(), detail.getSegments());
    }

    // --- deletion ------------------------------------------------------------------------------

    @Test
    public void deletingASessionRemovesTheRoutePointsAndVisitsItOwnsAndNothingElse() {
        storeSession("deleted", 1_000L, 2_000L, true);
        storeSession("kept", 3_000L, 4_000L, true);
        storeRoutePoint("deleted", 0, 1);
        storeRoutePoint("deleted", 1, 1);
        storeRoutePoint("kept", 0, 1);
        storeVisit("deleted", "old-arts", 1_500L);
        storeVisit("kept", "museum", 3_500L);

        assertTrue(delete("deleted"));

        assertEquals("only the selected session goes", 1, sessions.size());
        assertEquals("its route points go with it",
                0, routePoints.countBySession("deleted"));
        assertEquals("its visits go with it", 0, visits.countBySession("deleted"));
        assertEquals("another session's route must be untouched",
                1, routePoints.countBySession("kept"));
        assertEquals(1, visits.countBySession("kept"));
        assertEquals("no orphan route rows may survive the delete", 1, routePoints.totalRows());
        assertEquals("no orphan visit rows may survive the delete", 1, visits.totalRows());
    }

    @Test
    public void deletingASessionThatIsAlreadyGoneSucceedsWithoutRemovingAnything() {
        storeSession("kept", 1_000L, 2_000L, true);

        assertFalse("an id that names nothing is not an error, it is already the desired state",
                delete("never-existed"));
        assertEquals(1, sessions.size());
    }

    @Test
    public void aSessionStillInProgressCannotBeDeletedFromHistory() {
        storeSession("running", 1_000L, 0L, false);

        Throwable error = deleteError("running");

        assertTrue("discarding a live walk is the recovery screen's job, not history's",
                error instanceof ActiveSessionNotDeletableException);
        assertEquals("nothing may be removed when the delete is refused", 1, sessions.size());
    }

    @Test
    public void clearingHistoryRemovesEveryFinishedSessionAndLeavesAWalkInProgressAlone() {
        storeSession("first", 1_000L, 2_000L, true);
        storeSession("second", 3_000L, 4_000L, true);
        storeSession("running", 5_000L, 0L, false);
        storeRoutePoint("first", 0, 1);
        storeRoutePoint("running", 0, 1);
        storeVisit("second", "museum", 3_500L);

        assertEquals("the count must describe what was actually removed",
                2, Integer.valueOf(clearAll()).intValue());

        assertEquals("the walk in progress survives", 1, sessions.size());
        assertNotNull(sessions.findById("running"));
        assertEquals("its own route survives with it", 1, routePoints.countBySession("running"));
        assertEquals("no orphan route rows may survive the clear", 1, routePoints.totalRows());
        assertEquals("no orphan visit rows may survive the clear", 0, visits.totalRows());
    }

    @Test
    public void clearingEmptyHistoryRemovesNothingAndReportsZero() {
        assertEquals(0, clearAll());
    }

    // --- observer ------------------------------------------------------------------------------

    @Test
    public void observersAreNotifiedOncePerSuccessfulRemovalAndNotForANoOp() {
        storeSession("first", 1_000L, 2_000L, true);
        AtomicInteger notifications = new AtomicInteger();
        repository.addObserver(notifications::incrementAndGet);

        delete("never-existed");
        assertEquals("deleting nothing changed nothing, so there is nothing to announce",
                0, notifications.get());

        delete("first");
        assertEquals(1, notifications.get());

        clearAll();
        assertEquals("clearing an already-empty history announces nothing",
                1, notifications.get());
    }

    @Test
    public void aRemovedObserverStopsBeingNotified() {
        storeSession("first", 1_000L, 2_000L, true);
        storeSession("second", 3_000L, 4_000L, true);
        AtomicInteger notifications = new AtomicInteger();
        HistoryRepository.Observer observer = notifications::incrementAndGet;
        repository.addObserver(observer);
        repository.removeObserver(observer);

        delete("first");

        assertEquals(0, notifications.get());
    }

    // --- helpers -------------------------------------------------------------------------------

    private SessionEntity storeSession(String id, long start, long end, boolean finished) {
        SessionEntity session = new SessionEntity();
        session.id = id;
        session.startWallClockMillis = start;
        session.endWallClockMillis = end;
        session.finished = finished;
        session.activeDurationMillis = 60_000L;
        session.distanceMeters = 250.0;
        session.segment = 1;
        session.segmentCount = 1;
        sessions.insert(session);
        return session;
    }

    private void storeRoutePoint(String sessionId, int sequence, int segment) {
        RoutePointEntity point = new RoutePointEntity();
        point.sessionId = sessionId;
        point.sequence = sequence;
        point.segment = segment;
        point.latitude = LATITUDE + sequence * 0.0001;
        point.longitude = LONGITUDE;
        point.accuracyMeters = 8.0f;
        point.wallClockMillis = 1_000L + sequence;
        routePoints.insertAll(Collections.singletonList(point));
    }

    private void storeVisit(String sessionId, String attractionId, long arrivalMillis) {
        VisitEntity visit = new VisitEntity();
        visit.sessionId = sessionId;
        visit.attractionId = attractionId;
        visit.arrivalWallClockMillis = arrivalMillis;
        visit.attractionName = attractionId;
        visit.story = "A recorded story.";
        visit.unread = true;
        visits.insert(visit);
        Integer existing = sessions.visitCounts.get(sessionId);
        sessions.visitCounts.put(sessionId, existing == null ? 1 : existing + 1);
    }

    private List<SessionHistoryEntry> loadHistory() {
        Capture<List<SessionHistoryEntry>> capture = new Capture<>();
        repository.loadHistory(capture);
        return capture.requireResult();
    }

    private SessionHistoryDetail loadDetail(String sessionId) {
        Capture<SessionHistoryDetail> capture = new Capture<>();
        repository.loadDetail(sessionId, capture);
        return capture.requireResult();
    }

    private Throwable loadDetailError(String sessionId) {
        Capture<SessionHistoryDetail> capture = new Capture<>();
        repository.loadDetail(sessionId, capture);
        return capture.requireError();
    }

    private boolean delete(String sessionId) {
        Capture<Boolean> capture = new Capture<>();
        repository.delete(sessionId, capture);
        return capture.requireResult();
    }

    private Throwable deleteError(String sessionId) {
        Capture<Boolean> capture = new Capture<>();
        repository.delete(sessionId, capture);
        return capture.requireError();
    }

    private int clearAll() {
        Capture<Integer> capture = new Capture<>();
        repository.clearAll(capture);
        return capture.requireResult();
    }

    /** Records the single answer a direct-executor callback produces. */
    private static final class Capture<T> implements RepositoryCallback<T> {

        private T result;
        private Throwable error;
        private boolean answered;

        @Override
        public void onSuccess(T value) {
            result = value;
            answered = true;
        }

        @Override
        public void onError(Throwable failure) {
            error = failure;
            answered = true;
        }

        T requireResult() {
            assertTrue("the callback was never invoked", answered);
            assertNull("expected success but the repository failed: " + error, error);
            return result;
        }

        Throwable requireError() {
            assertTrue("the callback was never invoked", answered);
            assertNotNull("expected a failure but the repository succeeded with " + result, error);
            return error;
        }
    }
}
