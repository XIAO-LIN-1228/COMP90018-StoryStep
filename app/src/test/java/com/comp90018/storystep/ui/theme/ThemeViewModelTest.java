package com.comp90018.storystep.ui.theme;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.comp90018.storystep.application.*;
import com.comp90018.storystep.data.local.*;
import com.comp90018.storystep.data.repository.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.theme.*;
import com.comp90018.storystep.platform.location.*;
import java.util.*;
import org.junit.*;

public class ThemeViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private final DeferredRepository repository = new DeferredRepository();
    private final FakeClock clock = FakeClock.startingAt(1000);
    private final FakeLocationSource location = new FakeLocationSource();
    private final RoomSessionStore store = new RoomSessionStore(new DirectTransactions(), new FakeSessionDao(),
            new FakeRoutePointDao(), new FakeVisitDao(), clock, Runnable::run, Runnable::run, new FakeThemeDao());
    private final ExplorationCoordinator coordinator = TestCoordinators.with(clock, location, SensingSources.none(), store);
    private ThemeViewModel model;
    @Before public void setup() { model = new ThemeViewModel(repository, coordinator); }
    @Test public void previewNeverStartsLocationAndHistoricalViewCannotStart() {
        assertTrue(model.getState().getValue().canStart()); assertFalse(location.isRegistered());
        model.showSession("old"); repository.latest().onSuccess(progress("old"));
        assertFalse(model.getState().getValue().canStart()); assertFalse(model.getState().getValue().editable());
        model.start(); assertFalse(location.isRegistered());
    }
    @Test public void olderReadCannotReplaceNewHistoryOrExposePreviousProgress() {
        model.showSession("first"); RepositoryCallback<ThemeProgress> first = repository.latest();
        model.showSession("second"); RepositoryCallback<ThemeProgress> second = repository.latest();
        assertNull(model.getState().getValue().progress);
        second.onSuccess(progress("second")); first.onSuccess(progress("first"));
        assertEquals("second", model.getState().getValue().progress.sessionId);
    }
    @Test public void readFailureIsVisibleAndRetryCanRecover() {
        model.showSession("old"); repository.latest().onError(new IllegalStateException("disk error"));
        assertTrue(model.getState().getValue().failed); assertFalse(model.getState().getValue().loading);
        model.refresh(); repository.latest().onSuccess(progress("old")); assertFalse(model.getState().getValue().failed);
    }
    @Test public void skipClearsOnlyTheSkippedDestinationAfterDurableSuccess() {
        active(); model.skip("old-arts", true); assertEquals("old-arts", coordinator.getDestination().getId());
        assertTrue(model.getState().getValue().busy); repository.command.onSuccess(progress(coordinator.summary().getId()));
        assertNull(coordinator.getDestination());
    }
    @Test public void failedSkipPreservesDestinationAndExposesRetryableError() {
        active(); model.skip("old-arts", true); repository.command.onError(new IllegalStateException("disk full"));
        assertEquals("old-arts", coordinator.getDestination().getId()); assertTrue(model.getState().getValue().failed);
        assertTrue(model.getState().getValue().editable());
    }
    @Test public void refreshNeverReplacesAUserSelectedDestination() {
        active(); model.select("old-quad"); model.refresh(); repository.latest().onSuccess(progress(coordinator.summary().getId()));
        assertEquals("old-quad", coordinator.getDestination().getId());
    }
    @Test public void staleReadCannotReenableEditingDuringMutation() {
        active(); RepositoryCallback<ThemeProgress> old = repository.latest();
        model.skip("old-quad", true); old.onSuccess(progress(coordinator.summary().getId()));
        assertTrue(model.getState().getValue().busy); assertFalse(model.getState().getValue().editable());
    }
    @Test public void clearedModelIgnoresLateDatabaseCallback() {
        model.showSession("old"); ThemeViewModel.State before = model.getState().getValue();
        model.onCleared(); repository.latest().onSuccess(progress("old")); assertSame(before, model.getState().getValue());
    }
    @Test public void deniedStartDoesNotLeaveBusySpinner() {
        coordinator.onVisibilityChanged(true); model.start();
        assertFalse(model.getState().getValue().busy); assertTrue(model.getState().getValue().failed);
        assertTrue(model.getState().getValue().canStart());
    }
    private void active() {
        coordinator.onVisibilityChanged(true); coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        model.start(); repository.latest().onSuccess(progress(coordinator.summary().getId()));
        assertTrue(model.getState().getValue().editable());
    }
    private static ThemeProgress progress(String id) { return new ThemeProgress(id, ThemeCatalog.parkville(), false,
            Collections.emptySet(), Collections.emptySet(), Collections.emptyMap()); }
    private static final class DeferredRepository implements ThemeRepository {
        final List<RepositoryCallback<ThemeProgress>> reads = new ArrayList<>();
        RepositoryCallback<ThemeProgress> command;
        public void load(String id, RepositoryCallback<ThemeProgress> cb) { reads.add(cb); }
        public void setSkipped(String id, String stop, boolean skip, RepositoryCallback<ThemeProgress> cb) { command = cb; }
        public void answer(String id, String stop, int answer, RepositoryCallback<ThemeProgress> cb) { command = cb; }
        RepositoryCallback<ThemeProgress> latest() { return reads.get(reads.size() - 1); }
    }
}
