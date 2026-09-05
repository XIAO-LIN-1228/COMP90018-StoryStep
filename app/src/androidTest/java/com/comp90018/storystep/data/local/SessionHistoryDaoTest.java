package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * The history queries and deletions, against real SQLite.
 *
 * <p>This is the test that proves the {@code ON DELETE CASCADE} the whole deletion design rests
 * on. The JVM tests wire a fake cascade by hand, which can only show that the repository behaves
 * correctly <em>given</em> a cascading store; only a real database can show that the cascade runs
 * at all, and that no orphan route point or visit is left behind when it does.
 */
@RunWith(AndroidJUnit4.class)
public class SessionHistoryDaoTest {

    private StoryStepDatabase database;
    private SessionDao sessions;
    private RoutePointDao routePoints;
    private VisitDao visits;

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        sessions = database.sessionDao();
        routePoints = database.routePointDao();
        visits = database.visitDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void onlyFinishedSessionsAreListedAndTheNewestComesFirst() {
        sessions.insert(session("oldest", 1_000L, 2_000L, true));
        sessions.insert(session("newest", 8_000L, 9_000L, true));
        sessions.insert(session("running", 10_000L, 0L, false));

        List<SessionHistoryRow> history = sessions.findFinished();

        assertEquals("a walk in progress is not history", 2, history.size());
        assertEquals("newest", history.get(0).id);
        assertEquals("oldest", history.get(1).id);
    }

    @Test
    public void sessionsThatEndedTogetherAreOrderedDeterministically() {
        sessions.insert(session("b-walk", 1_000L, 9_000L, true));
        sessions.insert(session("a-walk", 1_000L, 9_000L, true));

        List<SessionHistoryRow> history = sessions.findFinished();

        assertEquals("a tie must not leave the order up to SQLite", "a-walk", history.get(0).id);
        assertEquals("b-walk", history.get(1).id);
    }

    @Test
    public void theVisitCountIsAggregatedAndASessionWithNoVisitsStillAppears() {
        sessions.insert(session("busy", 1_000L, 5_000L, true));
        sessions.insert(session("quiet", 6_000L, 7_000L, true));
        visits.insert(visit("busy", "old-arts"));
        visits.insert(visit("busy", "museum"));

        List<SessionHistoryRow> history = sessions.findFinished();

        assertEquals(2, history.size());
        assertEquals("quiet", history.get(0).id);
        assertEquals("a walk where nothing was reached is still a walk", 0, history.get(0).visitCount);
        assertEquals(2, history.get(1).visitCount);
    }

    @Test
    public void deletingOneSessionCascadesToItsOwnRoutePointsAndVisitsOnly() {
        sessions.insert(session("deleted", 1_000L, 2_000L, true));
        sessions.insert(session("kept", 3_000L, 4_000L, true));
        routePoints.insertAll(Collections.singletonList(routePoint("deleted", 0)));
        routePoints.insertAll(Collections.singletonList(routePoint("deleted", 1)));
        routePoints.insertAll(Collections.singletonList(routePoint("kept", 0)));
        visits.insert(visit("deleted", "old-arts"));
        visits.insert(visit("kept", "museum"));

        assertEquals(1, sessions.deleteById("deleted"));

        assertEquals("the foreign key must actually cascade, not merely be declared",
                0, routePoints.countBySession("deleted"));
        assertEquals(0, visits.countBySession("deleted"));
        assertEquals("another session's records must be untouched",
                1, routePoints.countBySession("kept"));
        assertEquals(1, visits.countBySession("kept"));
        assertEquals("no orphan route rows may survive the cascade",
                1, totalRoutePoints());
        assertEquals("no orphan visit rows may survive the cascade", 1, totalVisits());
    }

    @Test
    public void deletingAnUnknownSessionRemovesNothing() {
        sessions.insert(session("kept", 1_000L, 2_000L, true));

        assertEquals(0, sessions.deleteById("never-existed"));
        assertEquals(1, sessions.countFinished());
    }

    @Test
    public void clearingHistoryRemovesFinishedSessionsAndLeavesAWalkInProgressWithItsRoute() {
        sessions.insert(session("first", 1_000L, 2_000L, true));
        sessions.insert(session("second", 3_000L, 4_000L, true));
        sessions.insert(session("running", 5_000L, 0L, false));
        routePoints.insertAll(Collections.singletonList(routePoint("first", 0)));
        routePoints.insertAll(Collections.singletonList(routePoint("running", 0)));
        visits.insert(visit("second", "museum"));

        assertEquals("the count must describe what was actually removed",
                2, sessions.deleteAllFinished());

        assertNotNull("clearing history must not throw away the walk the user is on",
                sessions.findUnfinished());
        assertEquals("running", sessions.findUnfinished().id);
        assertEquals("its own route survives with it", 1, routePoints.countBySession("running"));
        assertEquals("no orphan route rows may survive the clear", 1, totalRoutePoints());
        assertEquals("no orphan visit rows may survive the clear", 0, totalVisits());
        assertTrue(sessions.findFinished().isEmpty());
    }

    @Test
    public void clearingEmptyHistoryRemovesNothing() {
        assertEquals(0, sessions.deleteAllFinished());
    }

    @Test
    public void theRecordedContextSurvivesAWriteAndReadBack() {
        SessionEntity session = session("walk", 1_000L, 9_000L, true);
        session.weatherAreaKey = "-37.80,144.95";
        session.weatherCondition = "CLOUDY";
        session.weatherTemperatureCelsius = 14.5;
        session.weatherObservedAtMillis = 1_700_000_000_000L;
        session.weatherFetchedAtMillis = 1_700_000_001_000L;
        session.movementState = "MOVING";
        session.movementMovingMillis = 120_000L;
        session.movementStationaryMillis = 30_000L;
        session.interrupted = true;
        sessions.insert(session);

        SessionEntity stored = sessions.findById("walk");

        assertEquals("-37.80,144.95", stored.weatherAreaKey);
        assertEquals("CLOUDY", stored.weatherCondition);
        assertEquals(14.5, stored.weatherTemperatureCelsius, 0.0001);
        assertEquals("MOVING", stored.movementState);
        assertEquals(120_000L, stored.movementMovingMillis);
        assertTrue(stored.interrupted);
        assertTrue("the label has to reach the list query too",
                sessions.findFinished().get(0).interrupted);
    }

    private int totalRoutePoints() {
        return routePoints.countBySession("deleted") + routePoints.countBySession("kept")
                + routePoints.countBySession("first") + routePoints.countBySession("second")
                + routePoints.countBySession("running") + routePoints.countBySession("walk");
    }

    private int totalVisits() {
        return visits.countBySession("deleted") + visits.countBySession("kept")
                + visits.countBySession("first") + visits.countBySession("second")
                + visits.countBySession("running") + visits.countBySession("busy");
    }

    private static SessionEntity session(String id, long start, long end, boolean finished) {
        SessionEntity session = new SessionEntity();
        session.id = id;
        session.state = finished ? "COMPLETED" : "ACTIVE";
        session.startWallClockMillis = start;
        session.endWallClockMillis = end;
        session.activeDurationMillis = 60_000L;
        session.distanceMeters = 250.0;
        session.segment = 1;
        session.segmentCount = 1;
        session.finished = finished;
        session.lastCheckpointWallClockMillis = start;
        return session;
    }

    private static RoutePointEntity routePoint(String sessionId, int sequence) {
        RoutePointEntity point = new RoutePointEntity();
        point.sessionId = sessionId;
        point.sequence = sequence;
        point.segment = 1;
        point.latitude = -37.79630 + sequence * 0.0001;
        point.longitude = 144.96140;
        point.accuracyMeters = 8.0f;
        point.wallClockMillis = 1_700_000_000_000L + sequence;
        return point;
    }

    private static VisitEntity visit(String sessionId, String attractionId) {
        VisitEntity visit = new VisitEntity();
        visit.sessionId = sessionId;
        visit.attractionId = attractionId;
        visit.arrivalWallClockMillis = 1_700_000_000_000L;
        visit.attractionName = attractionId;
        visit.story = "A recorded story.";
        visit.unread = true;
        return visit;
    }
}
