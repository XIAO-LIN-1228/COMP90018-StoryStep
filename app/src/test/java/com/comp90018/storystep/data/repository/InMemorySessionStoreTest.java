package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.repository.DuplicateVisitException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Before;
import org.junit.Test;

/**
 * Contract tests for the session store. They are written against the interface's rules, not this
 * implementation's internals, so the Room-backed store can be held to the same ones.
 */
public class InMemorySessionStoreTest {

    private FakeClock clock;
    private InMemorySessionStore store;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        store = new InMemorySessionStore(clock);
    }

    @Test
    public void aCreatedSessionIsActiveOnItsFirstSegment() {
        SessionSummary created = create();

        assertNotNull(created.getId());
        assertEquals(SessionState.ACTIVE, created.getState());
        assertEquals(1, created.getSegment());
        assertEquals(1_000L, created.getStartWallClockMillis());
    }

    @Test
    public void asecondCreateIsRejectedWhileOneIsUnfinished() {
        create();

        Result<SessionSummary> second = new Result<>();
        store.create(second);

        // Two unfinished sessions would make every later "the session" query ambiguous.
        assertTrue(second.failed());
    }

    @Test
    public void aNewSessionCanBeCreatedOnceTheLastOneIsFinished() {
        SessionSummary first = create();
        finish(first);

        SessionSummary second = create();

        assertNotNull(second.getId());
        assertTrue(!second.getId().equals(first.getId()));
    }

    @Test
    public void aCheckpointReplacesTheStoredProgress() {
        SessionSummary created = create();
        SessionSummary progressed = withDuration(created, 45_000L);

        Result<Void> result = new Result<>();
        store.checkpoint(progressed, result);

        assertTrue(result.succeeded());
        assertEquals(45_000L, findUnfinished().getActiveDurationMillis());
    }

    @Test
    public void aCheckpointForAnUnknownSessionFails() {
        create();

        Result<Void> result = new Result<>();
        store.checkpoint(
                new SessionSummary("session-999", SessionState.ACTIVE, null, 1_000L, 0L, 1),
                result);

        assertTrue(result.failed());
    }

    @Test
    public void finishingTwiceSucceedsWithoutCreatingASecondRecord() {
        SessionSummary created = create();
        finish(created);

        Result<Void> second = new Result<>();
        store.finish(created, second);

        // A retried save after a success must not fail; the user already saw the session end.
        assertTrue(second.succeeded());
        assertNull("a finished session is no longer unfinished", findUnfinished());
    }

    @Test
    public void anUnfinishedSessionIsReportedForRecovery() {
        SessionSummary created = create();
        store.checkpoint(withDuration(created, 20_000L), new Result<Void>());

        SessionSummary unfinished = findUnfinished();

        assertNotNull(unfinished);
        assertEquals(created.getId(), unfinished.getId());
        assertEquals(20_000L, unfinished.getActiveDurationMillis());
    }

    @Test
    public void discardingRemovesTheUnfinishedSession() {
        SessionSummary created = create();
        Result<Void> discarded = new Result<>();

        store.discard(created.getId(), discarded);

        assertTrue(discarded.succeeded());
        assertNull(findUnfinished());
    }

    @Test
    public void nothingIsReportedWhenNoSessionWasEverCreated() {
        assertNull(findUnfinished());
    }

    // --- helpers -------------------------------------------------------------------------------

    // --- visits ---------------------------------------------------------------------------------

    @Test
    public void aVisitIsRecordedAgainstTheRunningSession() {
        SessionSummary session = create();

        Result<Visit> result = new Result<>();
        store.recordVisit(visit(session.getId(), "old-arts"), result);

        assertTrue(result.succeeded());
        assertEquals("old-arts", result.value.getAttractionId());
    }

    @Test
    public void asecondVisitToTheSamePlaceInOneSessionIsRejected() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        Result<Visit> second = new Result<>();
        store.recordVisit(visit(session.getId(), "old-arts"), second);

        // The exactly-once guarantee: a repeated arrival is refused by the store, not deduplicated
        // by whichever caller happens to notice.
        assertTrue(second.failed());
        assertTrue(second.error instanceof DuplicateVisitException);
    }

    @Test
    public void twoPlacesInOneSessionAreBothRecorded() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        Result<Visit> second = new Result<>();
        store.recordVisit(visit(session.getId(), "system-garden"), second);

        assertTrue(second.succeeded());
    }

    @Test
    public void aVisitForAnUnknownSessionIsRejected() {
        create();

        Result<Visit> result = new Result<>();
        store.recordVisit(visit("session-does-not-exist", "old-arts"), result);

        assertTrue(result.failed());
    }

    @Test
    public void anUnreadVisitIsFoundAndAReadOneIsNot() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        assertNotNull(findUnread(session.getId()));

        Result<Void> marked = new Result<>();
        store.markVisitRead(session.getId(), "old-arts", marked);
        assertTrue(marked.succeeded());

        assertNull(findUnread(session.getId()));
    }

    @Test
    public void avisitCanBeLookedUpByItsSessionAndAttractionPair() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        Result<Visit> found = new Result<>();
        store.findVisit(session.getId(), "old-arts", found);
        assertTrue(found.succeeded());
        assertNotNull(found.value);

        Result<Visit> missing = new Result<>();
        store.findVisit(session.getId(), "system-garden", missing);
        assertTrue("a place that was never visited is absent, not an error", missing.succeeded());
        assertNull(missing.value);
    }

    @Test
    public void areadVisitIsStillFoundByItsPair() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");
        Result<Void> marked = new Result<>();
        store.markVisitRead(session.getId(), "old-arts", marked);

        Result<Visit> found = new Result<>();
        store.findVisit(session.getId(), "old-arts", found);

        // Reopening a story from history has to keep working after it has been read once.
        assertNotNull(found.value);
        assertFalse(found.value.isUnread());
    }

    @Test
    public void markingAnUnknownVisitReadIsNotAFailure() {
        SessionSummary session = create();

        Result<Void> result = new Result<>();
        store.markVisitRead(session.getId(), "never-visited", result);

        // Nothing to mark is not the same as something going wrong.
        assertTrue(result.succeeded());
    }

    @Test
    public void markingAVisitReadDoesNotCreateAnother() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        Result<Void> marked = new Result<>();
        store.markVisitRead(session.getId(), "old-arts", marked);

        Result<Visit> again = new Result<>();
        store.recordVisit(visit(session.getId(), "old-arts"), again);
        assertTrue("reading a story must not free the pair to be recorded again", again.failed());
    }

    @Test
    public void discardingASessionRemovesItsVisits() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        Result<Void> discarded = new Result<>();
        store.discard(session.getId(), discarded);
        assertTrue(discarded.succeeded());

        assertNull(findUnread(session.getId()));
    }

    @Test
    public void afinishedSessionKeepsItsVisits() {
        SessionSummary session = create();
        recordVisit(session.getId(), "old-arts");

        finish(session);

        // Finishing is what turns a session into history; its visits are that history.
        assertNotNull(findUnread(session.getId()));
    }

    private Visit visit(String sessionId, String attractionId) {
        return new Visit(sessionId, attractionId, 5_000L, "Old Arts Building",
                "The 1924 clocktower building.", true);
    }

    private void recordVisit(String sessionId, String attractionId) {
        Result<Visit> result = new Result<>();
        store.recordVisit(visit(sessionId, attractionId), result);
        assertTrue(result.succeeded());
    }

    private Visit findUnread(String sessionId) {
        Result<Visit> result = new Result<>();
        store.findUnreadVisit(sessionId, result);
        assertTrue(result.succeeded());
        return result.value;
    }

    private SessionSummary create() {
        Result<SessionSummary> result = new Result<>();
        store.create(result);
        assertTrue(result.succeeded());
        return result.value;
    }

    private void finish(SessionSummary summary) {
        Result<Void> result = new Result<>();
        store.finish(summary, result);
        assertTrue(result.succeeded());
    }

    private SessionSummary findUnfinished() {
        RestoredSession restored = findRestored();
        return restored == null ? null : restored.getSummary();
    }

    private RestoredSession findRestored() {
        Result<RestoredSession> result = new Result<>();
        store.findUnfinished(result);
        assertTrue(result.succeeded());
        return result.value;
    }

    private static SessionSummary withDuration(SessionSummary summary, long activeMillis) {
        return new SessionSummary(summary.getId(), summary.getState(), summary.getPauseReason(),
                summary.getStartWallClockMillis(), activeMillis, summary.getSegment());
    }

    /** Captures which callback arm ran, since the store answers synchronously. */
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
