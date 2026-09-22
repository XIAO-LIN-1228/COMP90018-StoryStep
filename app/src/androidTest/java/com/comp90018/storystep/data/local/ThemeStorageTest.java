package com.comp90018.storystep.data.local;

import static org.junit.Assert.*;
import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.comp90018.storystep.data.repository.*;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.theme.*;
import com.comp90018.storystep.domain.time.Clock;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ThemeStorageTest {
    private static final String NAME = "theme-storage-test.db";
    private final Context context = ApplicationProvider.getApplicationContext();
    private StoryStepDatabase db;
    private final Clock clock = new Clock() {
        public long wallClockMillis() { return 1000; }
        public long monotonicMillis() { return 1000; }
    };
    @Before public void open() { context.deleteDatabase(NAME); reopen(); }
    private void reopen() { db = Room.databaseBuilder(context, StoryStepDatabase.class, NAME)
            .addMigrations(StoryStepMigrations.all()).build(); }
    @After public void close() { db.close(); context.deleteDatabase(NAME); }
    private RoomSessionStore store(ThemeDao dao) { return new RoomSessionStore(new RoomTransactions(db), db.sessionDao(),
            db.routePointDao(), db.visitDao(), clock, Runnable::run, Runnable::run, dao); }
    private RoomThemeRepository themes() { return new RoomThemeRepository(new RoomTransactions(db), db.themeDao(),
            db.sessionDao(), db.visitDao(), Runnable::run, Runnable::run); }
    @Test public void reopenAfterCacheDeletionKeepsStoriesArrivalSkipAndAnswer() {
        SessionSummary run = start(); String id = run.getId();
        ThemeStop stop = ThemeCatalog.parkville().stops.get(0);
        Result<Visit> visit = new Result<>(); store(db.themeDao()).recordVisit(new Visit(id, stop.id, 1000, stop.name, stop.story, true), visit);
        assertNull(visit.error);
        Result<ThemeProgress> answer = new Result<>(); themes().answer(id, stop.id, 0, answer); assertNull(answer.error);
        themes().setSkipped(id, "old-quad", true, new Result<>());
        db.attractionDao().deleteAll(); db.close(); reopen();
        ThemeProgress p = load(id); assertEquals(1, p.arrivedCount()); assertEquals(1, p.skippedCount());
        assertEquals(Integer.valueOf(0), p.answer(stop.id)); assertEquals(stop.story, p.theme.stop(stop.id).story);
        Result<RestoredSession> restored = new Result<>(); store(db.themeDao()).findUnfinished(restored);
        assertNull(restored.error);
        // Storage preserves the last durable state; the state machine turns process recovery into
        // PAUSED. The coordinator tests additionally assert that no location subscription opens.
        com.comp90018.storystep.domain.session.SessionStateMachine recovered =
                new com.comp90018.storystep.domain.session.SessionStateMachine(clock);
        recovered.restore(restored.value.getSummary()); assertEquals(SessionState.PAUSED, recovered.getState());
        assertEquals(stop.id, restored.value.getDestination().getId());
    }
    @Test public void failedStopInsertionRollsBackSessionAndThemeTogether() {
        ThemeDao real = db.themeDao();
        ThemeDao failing = new ThemeDao() {
            public void insertRun(ThemeRunEntity row) { real.insertRun(row); }
            public void insertStops(List<ThemeStopEntity> rows) {
                real.insertStops(Collections.singletonList(rows.get(0)));
                throw new IllegalStateException("disk full after first station");
            }
            public ThemeRunEntity findRun(String id) { return real.findRun(id); }
            public List<ThemeStopEntity> findStops(String id) { return real.findStops(id); }
            public int setSkipped(String id, String stop, boolean skip) { return real.setSkipped(id, stop, skip); }
            public int answer(String id, String stop, int answer) { return real.answer(id, stop, answer); }
        };
        Result<SessionSummary> failed = new Result<>(); store(failing).createForTheme(ThemeCatalog.parkville(), failed);
        assertNotNull(failed.error); assertNull(db.sessionDao().findUnfinished());
        try (android.database.Cursor c = db.getOpenHelper().getReadableDatabase().query("SELECT COUNT(*) FROM theme_runs")) {
            assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0));
        }
        assertEquals(3, load(start().getId()).pendingCount());
    }
    @Test public void deletingFinishedHistoryCascadesThemesAndKeepsActiveRunAndSavedPlaces() {
        SessionSummary finished = start(); Result<Void> end = new Result<>(); store(db.themeDao()).finish(finished, end); assertNull(end.error);
        SessionSummary active = start(); SavedAttractionEntity saved = new SavedAttractionEntity();
        saved.attractionId = "kept"; saved.name = "Kept place"; saved.category = "park";
        saved.indoor = "outdoor"; db.savedAttractionDao().insertIgnoring(saved);
        assertEquals(1, db.sessionDao().deleteAllFinished());
        assertNull(db.themeDao().findRun(finished.getId())); assertTrue(db.themeDao().findStops(finished.getId()).isEmpty());
        assertNotNull(db.themeDao().findRun(active.getId())); assertEquals(3, db.themeDao().findStops(active.getId()).size());
        assertNotNull(db.savedAttractionDao().findById("kept"));
    }
    @Test public void discardRemovesOnlyItsOwnTheme() {
        SessionSummary run = start(); Result<Void> r = new Result<>(); store(db.themeDao()).discard(run.getId(), r);
        assertNull(r.error); assertNull(db.themeDao().findRun(run.getId())); assertTrue(db.themeDao().findStops(run.getId()).isEmpty());
        assertEquals(0, db.sessionDao().countUnfinished());
    }
    @Test public void duplicateSqliteArrivalCannotInflateThemeProgress() {
        String id = start().getId(); Visit v = new Visit(id, "old-arts", 1000, "Old Arts", "Story", true);
        store(db.themeDao()).recordVisit(v, new Result<>());
        Result<Visit> second = new Result<>(); store(db.themeDao()).recordVisit(v, second);
        assertTrue(second.error instanceof DuplicateVisitException); assertEquals(1, load(id).arrivedCount());
    }
    private SessionSummary start() { Result<SessionSummary> r = new Result<>(); store(db.themeDao()).createForTheme(ThemeCatalog.parkville(), r);
        assertNull(r.error); return r.value; }
    private ThemeProgress load(String id) { Result<ThemeProgress> r = new Result<>(); themes().load(id, r); assertNull(r.error); return r.value; }
    private static final class Result<T> implements RepositoryCallback<T> {
        T value; Throwable error;
        public void onSuccess(T value) { this.value = value; }
        public void onError(Throwable error) { this.error = error; }
    }
}
