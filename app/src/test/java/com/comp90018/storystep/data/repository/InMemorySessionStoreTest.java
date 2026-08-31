package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
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
    public void nothingIsReportedWhenNoSessionWasEverCreated() {
        assertNull(findUnfinished());
    }

    // --- helpers -------------------------------------------------------------------------------

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
        Result<SessionSummary> result = new Result<>();
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
