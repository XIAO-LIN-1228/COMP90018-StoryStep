package com.comp90018.storystep.data.repository;

import static org.junit.Assert.*;
import com.comp90018.storystep.data.local.*;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.theme.*;
import java.util.*;
import org.junit.Test;

public class RoomThemeRepositoryTest {
    private final FakeSessionDao sessions = new FakeSessionDao();
    private final FakeVisitDao visits = new FakeVisitDao();
    private final FakeThemeDao themes = new FakeThemeDao();
    private final RoomSessionStore store = new RoomSessionStore(new DirectTransactions(), sessions,
            new FakeRoutePointDao(), visits, FakeClock.startingAt(1000), Runnable::run, Runnable::run, themes);
    private final RoomThemeRepository repository = new RoomThemeRepository(new DirectTransactions(), themes,
            sessions, visits, Runnable::run, Runnable::run);
    @Test public void startingPersistsDefinitionAndInitialDestinationInOneSession() {
        String id = start(); ThemeProgress p = load(id);
        assertEquals(3, p.pendingCount()); assertEquals("old-arts", sessions.findById(id).destinationId);
        Result<SessionSummary> second = new Result<>(); store.createForTheme(ThemeCatalog.parkville(), second);
        assertNotNull(second.error); assertEquals(1, sessions.size());
    }
    @Test public void ordinaryWalkHasNoTheme() {
        Result<SessionSummary> r = new Result<>(); store.create(r); assertNull(load(r.value.getId()));
    }
    @Test public void skipAndUndoPersistWithoutMarkingArrival() {
        String id = start(); Result<ThemeProgress> r = new Result<>();
        repository.setSkipped(id, "old-arts", true, r); assertNull(r.error);
        assertEquals(1, load(id).skippedCount()); assertEquals(0, load(id).arrivedCount());
        repository.setSkipped(id, "old-arts", false, r); assertEquals(3, load(id).pendingCount());
    }
    @Test public void arrivalAfterSkipWinsAndCannotBeSkippedAgain() {
        String id = start(); repository.setSkipped(id, "old-arts", true, new Result<>());
        arrive(id, "old-arts"); assertEquals(1, load(id).arrivedCount()); assertEquals(0, load(id).skippedCount());
        Result<ThemeProgress> r = new Result<>(); repository.setSkipped(id, "old-arts", true, r);
        assertNotNull(r.error); assertEquals(1, load(id).arrivedCount());
    }
    @Test public void questionsRequireArrivalAndValidAnswerButDoNotChangeProgress() {
        String id = start(); Result<ThemeProgress> locked = new Result<>();
        repository.answer(id, "old-arts", 0, locked); assertNotNull(locked.error);
        arrive(id, "old-arts"); Result<ThemeProgress> invalid = new Result<>();
        repository.answer(id, "old-arts", 3, invalid); assertNotNull(invalid.error);
        Result<ThemeProgress> valid = new Result<>(); repository.answer(id, "old-arts", 1, valid);
        assertNull(valid.error); assertEquals(Integer.valueOf(1), load(id).answer("old-arts"));
        assertEquals(1, load(id).arrivedCount());
    }
    @Test public void duplicateVisitIsRejectedAndOtherWalksNeverUnlockStops() {
        String id = start(); arrive("another-session", "old-arts"); assertEquals(0, load(id).arrivedCount());
        arrive(id, "old-arts"); Result<Visit> duplicate = new Result<>();
        store.recordVisit(new Visit(id, "old-arts", 1001, "Place", "Story", true), duplicate);
        assertNotNull(duplicate.error); assertEquals(1, load(id).arrivedCount());
    }
    @Test public void finishedJourneyIsReadOnlyAndAnotherRunStartsEmpty() {
        String id = start(); arrive(id, "old-arts");
        SessionEntity row = sessions.findById(id); row.finished = true; row.state = "COMPLETED"; sessions.update(row);
        Result<ThemeProgress> r = new Result<>(); repository.setSkipped(id, "old-quad", true, r);
        assertNotNull(r.error); Result<ThemeProgress> answer = new Result<>(); repository.answer(id, "old-arts", 0, answer);
        assertNotNull(answer.error); assertEquals(1, load(id).arrivedCount());
        assertEquals(0, load(start()).arrivedCount());
    }
    @Test public void missingOrCorruptStorageReportsFailureAndDoesNotInventProgress() {
        String id = start(); themes.runs.get(id).definitionJson = "{}";
        Result<ThemeProgress> r = new Result<>(); repository.load(id, r);
        assertNotNull(r.error); assertNull(r.value);
    }
    @Test public void writesFailHonestlyAndUnknownStopIsRejected() {
        String id = start(); Result<ThemeProgress> unknown = new Result<>(); repository.setSkipped(id, "unknown", true, unknown);
        assertNotNull(unknown.error); themes.failure = new IllegalStateException("disk error");
        Result<ThemeProgress> failed = new Result<>(); repository.setSkipped(id, "old-arts", true, failed);
        assertNotNull(failed.error);
    }
    @Test public void callbacksWaitForMainExecutor() {
        String id = start(); List<Runnable> io = new ArrayList<>(), main = new ArrayList<>();
        RoomThemeRepository async = new RoomThemeRepository(new DirectTransactions(), themes, sessions, visits,
                io::add, main::add); Result<ThemeProgress> r = new Result<>(); async.load(id, r);
        assertFalse(r.called); io.remove(0).run(); assertFalse(r.called); main.remove(0).run(); assertTrue(r.called);
    }
    private String start() { Result<SessionSummary> r = new Result<>(); store.createForTheme(ThemeCatalog.parkville(), r);
        assertNull(r.error); return r.value.getId(); }
    private ThemeProgress load(String id) { Result<ThemeProgress> r = new Result<>(); repository.load(id, r);
        assertNull(r.error); return r.value; }
    private void arrive(String id, String stop) {
        // Insert a durable visit directly so a different session can be represented by the DAO fake.
        VisitEntity v = new VisitEntity(); v.sessionId = id; v.attractionId = stop;
        v.attractionName = "Place"; v.story = "Story"; v.arrivalWallClockMillis = 1000; visits.insert(v);
    }
    private static final class Result<T> implements RepositoryCallback<T> {
        T value; Throwable error; boolean called;
        public void onSuccess(T value) { this.value = value; called = true; }
        public void onError(Throwable error) { this.error = error; called = true; }
    }
}
