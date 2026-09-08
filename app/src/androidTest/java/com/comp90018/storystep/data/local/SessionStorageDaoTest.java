package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The session, route and visit tables against a real SQLite database.
 *
 * <p>These are the guarantees the JVM tests cannot make: that the unique indexes exist, that the
 * foreign keys cascade, and that the cascade takes nothing it does not own.
 */
@RunWith(AndroidJUnit4.class)
public class SessionStorageDaoTest {

    private StoryStepDatabase database;
    private SessionDao sessionDao;
    private RoutePointDao routePointDao;
    private VisitDao visitDao;
    private AttractionDao attractionDao;

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        sessionDao = database.sessionDao();
        routePointDao = database.routePointDao();
        visitDao = database.visitDao();
        attractionDao = database.attractionDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void aSessionRoundTripsWithItsDestinationSnapshot() {
        sessionDao.insert(session("s1", false));

        SessionEntity stored = sessionDao.findById("s1");
        assertNotNull(stored);
        assertEquals("old-arts", stored.destinationId);
        assertEquals("Old Arts Building", stored.destinationName);
        assertEquals(-37.79810, stored.destinationLatitude, 1e-9);
        assertEquals(45_000L, stored.activeDurationMillis);
        assertEquals(123.5, stored.distanceMeters, 1e-9);
    }

    @Test
    public void onlyUnfinishedSessionsAreOfferedForRecovery() {
        sessionDao.insert(session("finished", true));
        sessionDao.insert(session("running", false));

        assertEquals("running", sessionDao.findUnfinished().id);
        assertEquals(1, sessionDao.countUnfinished());
    }

    @Test
    public void thereIsNothingToRecoverWhenEverySessionIsFinished() {
        sessionDao.insert(session("finished", true));

        assertNull(sessionDao.findUnfinished());
    }

    @Test
    public void routePointsComeBackInSequenceOrder() {
        sessionDao.insert(session("s1", false));

        routePointDao.insertAll(Arrays.asList(
                routePoint("s1", 1, 2), routePoint("s1", 0, 1), routePoint("s1", 2, 2)));

        List<RoutePointEntity> stored = routePointDao.findBySession("s1");
        assertEquals(3, stored.size());
        assertEquals(0, stored.get(0).sequence);
        assertEquals(1, stored.get(1).sequence);
        assertEquals(2, stored.get(2).sequence);
        assertEquals(1, stored.get(0).segment);
        assertEquals(2, stored.get(1).segment);
    }

    @Test
    public void reInsertingAStoredSequenceIsIgnoredRatherThanDuplicated() {
        sessionDao.insert(session("s1", false));
        routePointDao.insertAll(Collections.singletonList(routePoint("s1", 0, 1)));

        routePointDao.insertAll(Collections.singletonList(routePoint("s1", 0, 1)));

        assertEquals(1, routePointDao.countBySession("s1"));
    }

    @Test
    public void twoSessionsMayEachHaveTheirOwnSequenceZero() {
        sessionDao.insert(session("s1", false));
        sessionDao.insert(session("s2", true));

        routePointDao.insertAll(Collections.singletonList(routePoint("s1", 0, 1)));
        routePointDao.insertAll(Collections.singletonList(routePoint("s2", 0, 1)));

        assertEquals(1, routePointDao.countBySession("s1"));
        assertEquals(1, routePointDao.countBySession("s2"));
    }

    @Test
    public void aSecondVisitToTheSamePlaceInOneSessionIsRejected() {
        sessionDao.insert(session("s1", false));
        visitDao.insert(visit("s1", "old-arts"));

        try {
            visitDao.insert(visit("s1", "old-arts"));
            fail("the unique index is what makes a visit exactly-once");
        } catch (SQLiteConstraintException expected) {
            // Expected: re-entering the same target must not record a second visit.
        }
        assertEquals(1, visitDao.countBySession("s1"));
    }

    @Test
    public void thesamePlaceMayBeVisitedAgainInADifferentSession() {
        sessionDao.insert(session("s1", true));
        sessionDao.insert(session("s2", false));
        visitDao.insert(visit("s1", "old-arts"));

        visitDao.insert(visit("s2", "old-arts"));

        assertEquals(1, visitDao.countBySession("s1"));
        assertEquals(1, visitDao.countBySession("s2"));
    }

    @Test
    public void deletingASessionRemovesItsOwnRouteAndVisitsAndNothingElse() {
        sessionDao.insert(session("s1", false));
        sessionDao.insert(session("s2", true));
        routePointDao.insertAll(Collections.singletonList(routePoint("s1", 0, 1)));
        routePointDao.insertAll(Collections.singletonList(routePoint("s2", 0, 1)));
        visitDao.insert(visit("s1", "old-arts"));
        visitDao.insert(visit("s2", "old-arts"));
        attractionDao.upsertAll(Collections.singletonList(attractionRow()));

        sessionDao.deleteById("s1");

        assertEquals(0, routePointDao.countBySession("s1"));
        assertEquals(0, visitDao.countBySession("s1"));
        assertEquals("another session's records are not this session's to delete",
                1, routePointDao.countBySession("s2"));
        assertEquals(1, visitDao.countBySession("s2"));
        assertNotNull("the attraction cache is not owned by a session",
                attractionDao.findById("old-arts"));
    }

    @Test
    public void theOldestUnreadVisitIsTheOneOfferedBack() {
        sessionDao.insert(session("s1", false));
        visitDao.insert(visitAt("s1", "system-garden", 2_000L));
        visitDao.insert(visitAt("s1", "old-arts", 1_000L));

        VisitEntity unread = visitDao.findFirstUnread("s1");

        // After an interruption the user should be offered the story they were about to read,
        // not whichever one happened to be recorded last.
        assertNotNull(unread);
        assertEquals("old-arts", unread.attractionId);
    }

    @Test
    public void aReadVisitIsNoLongerOffered() {
        sessionDao.insert(session("s1", false));
        visitDao.insert(visit("s1", "old-arts"));

        assertEquals(1, visitDao.markRead("s1", "old-arts"));

        assertNull(visitDao.findFirstUnread("s1"));
        assertEquals("marking a story read must not remove the visit",
                1, visitDao.countBySession("s1"));
    }

    @Test
    public void markingAnUnknownVisitReadChangesNothing() {
        sessionDao.insert(session("s1", false));
        visitDao.insert(visit("s1", "old-arts"));

        assertEquals(0, visitDao.markRead("s1", "never-visited"));

        assertNotNull(visitDao.findFirstUnread("s1"));
    }

    @Test
    public void anUnreadVisitBelongsOnlyToItsOwnSession() {
        sessionDao.insert(session("s1", false));
        sessionDao.insert(session("s2", true));
        visitDao.insert(visit("s2", "old-arts"));

        assertNull(visitDao.findFirstUnread("s1"));
        assertNotNull(visitDao.findFirstUnread("s2"));
    }

    @Test
    public void aVisitCanBeLookedUpByItsSessionAndAttractionPair() {
        sessionDao.insert(session("s1", false));
        visitDao.insert(visit("s1", "old-arts"));

        assertNotNull(visitDao.find("s1", "old-arts"));
        assertNull(visitDao.find("s1", "system-garden"));
    }

    // --- fixtures ------------------------------------------------------------------------------

    private static SessionEntity session(String id, boolean finished) {
        SessionEntity entity = new SessionEntity();
        entity.id = id;
        entity.state = finished ? "COMPLETED" : "PAUSED";
        entity.pauseReason = finished ? null : "PROCESS_INTERRUPTED";
        entity.startWallClockMillis = 1_000L;
        entity.endWallClockMillis = finished ? 90_000L : 0L;
        entity.activeDurationMillis = 45_000L;
        entity.distanceMeters = 123.5;
        entity.segment = 2;
        entity.segmentCount = 2;
        entity.finished = finished;
        entity.lastCheckpointWallClockMillis = 46_000L;
        entity.destinationId = "old-arts";
        entity.destinationName = "Old Arts Building";
        entity.destinationCategory = "university_landmark";
        entity.destinationDescription = "The 1924 clocktower building.";
        entity.destinationIndoor = "outdoor";
        entity.destinationLatitude = -37.79810;
        entity.destinationLongitude = 144.95910;
        return entity;
    }

    private static RoutePointEntity routePoint(String sessionId, int sequence, int segment) {
        RoutePointEntity entity = new RoutePointEntity();
        entity.sessionId = sessionId;
        entity.sequence = sequence;
        entity.segment = segment;
        entity.latitude = -37.79630;
        entity.longitude = 144.96140;
        entity.accuracyMeters = 8.0f;
        entity.wallClockMillis = 1_700_000_000_000L + sequence;
        return entity;
    }

    private static VisitEntity visitAt(String sessionId, String attractionId, long arrivalMillis) {
        VisitEntity entity = visit(sessionId, attractionId);
        entity.arrivalWallClockMillis = arrivalMillis;
        return entity;
    }

    private static VisitEntity visit(String sessionId, String attractionId) {
        VisitEntity entity = new VisitEntity();
        entity.sessionId = sessionId;
        entity.attractionId = attractionId;
        entity.arrivalWallClockMillis = 1_700_000_000_000L;
        entity.attractionName = "Old Arts Building";
        entity.story = "The clocktower has stood since 1924.";
        entity.unread = true;
        return entity;
    }

    private static AttractionEntity attractionRow() {
        AttractionEntity entity = new AttractionEntity();
        entity.id = "old-arts";
        entity.name = "Old Arts Building";
        entity.category = "university_landmark";
        entity.description = "The 1924 clocktower building.";
        entity.indoor = "outdoor";
        entity.latitude = -37.79810;
        entity.longitude = 144.95910;
        entity.provenance = AttractionEntity.PROVENANCE_SEED;
        entity.fetchedAtMillis = 1_700_000_000_000L;
        return entity;
    }
}
