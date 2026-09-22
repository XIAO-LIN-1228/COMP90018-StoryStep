package com.comp90018.storystep.application;

import static org.junit.Assert.*;
import com.comp90018.storystep.data.local.*;
import com.comp90018.storystep.data.repository.*;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.theme.*;
import com.comp90018.storystep.platform.location.*;
import org.junit.Test;

/** Real coordinator + durable repository logic, controlled location/time and DAO fakes. */
public class ThemeExplorationTest {
    private final FakeClock clock = FakeClock.startingAt(1000);
    private final FakeSessionDao sessions = new FakeSessionDao();
    private final FakeThemeDao themes = new FakeThemeDao();
    private final FakeVisitDao visits = new FakeVisitDao();
    private final FakeLocationSource location = new FakeLocationSource();
    private final RoomSessionStore store = new RoomSessionStore(new DirectTransactions(), sessions,
            new FakeRoutePointDao(), visits, clock, Runnable::run, Runnable::run, themes);
    private final RoomThemeRepository repository = new RoomThemeRepository(new DirectTransactions(), themes,
            sessions, visits, Runnable::run, Runnable::run);
    private final ExplorationCoordinator coordinator = TestCoordinators.with(clock, location, SensingSources.none(), store);
    @Test public void completeTwoStopsSkipOnePauseRestoreAndFinishPartialJourney() {
        start(); String id = coordinator.summary().getId();
        dwell("old-arts", 5); assertEquals(1, progress(id).arrivedCount());
        dwell("old-arts", 5); assertEquals(1, progress(id).arrivedCount());
        repository.answer(id, "old-arts", 0, success());
        coordinator.consumeArrival();
        coordinator.selectDestination(ThemeCatalog.parkville().stop("old-quad").attraction());
        clock.advance(60_000); dwell("old-quad", 5); assertEquals(2, progress(id).arrivedCount());
        repository.setSkipped(id, "system-garden", true, success());
        coordinator.requestPause(); assertFalse(location.isRegistered());
        assertEquals(PauseReason.USER_REQUESTED, coordinator.summary().getPauseReason());
        FakeLocationSource recoveredLocation = new FakeLocationSource();
        ExplorationCoordinator recovered = TestCoordinators.with(clock, recoveredLocation, SensingSources.none(), store);
        recovered.restoreInterruptedSession();
        assertEquals(id, recovered.summary().getId()); assertEquals(SessionState.PAUSED, recovered.summary().getState());
        assertFalse(recoveredLocation.isRegistered()); assertEquals(2, progress(id).arrivedCount());
        assertEquals(Integer.valueOf(0), progress(id).answer("old-arts"));
        assertEquals(1, progress(id).skippedCount());
        recovered.onVisibilityChanged(true); recovered.onLocationAccessChanged(LocationAccess.PRECISE);
        recovered.requestResume(); assertTrue(recoveredLocation.isRegistered());
        recovered.requestStop(); assertEquals(SessionState.COMPLETED, recovered.summary().getState());
        assertFalse(recoveredLocation.isRegistered()); assertTrue(progress(id).finished); assertFalse(progress(id).allVisited());
    }
    @Test public void allStationsProduceCompleteResultsWithoutRequiringQuizAnswers() {
        start(); String id = coordinator.summary().getId();
        for (ThemeStop stop : ThemeCatalog.parkville().stops) {
            coordinator.selectDestination(stop.attraction()); clock.advance(60_000); dwell(stop.id, 5);
            coordinator.consumeArrival();
        }
        coordinator.requestStop(); assertTrue(progress(id).finished); assertTrue(progress(id).allVisited());
    }
    @Test public void deniedLocationCreatesNothingAndDoubleStartDoesNotDuplicate() {
        coordinator.onVisibilityChanged(true); coordinator.requestStartTheme(ThemeCatalog.parkville());
        assertEquals(0, sessions.size()); assertEquals(0, location.registrationCount());
        start(); coordinator.requestStartTheme(ThemeCatalog.parkville());
        assertEquals(1, sessions.size()); assertEquals(1, location.registrationCount());
    }
    @Test public void poorAccuracyCannotUnlockStories() {
        start(); dwell("old-arts", 80); assertEquals(0, progress(coordinator.summary().getId()).arrivedCount());
    }
    @Test public void storageFailureDoesNotUnlockStopAndCanBeRetried() {
        start(); visits.failWrites = new IllegalStateException("disk full"); dwell("old-arts", 5);
        String id = coordinator.summary().getId(); assertEquals(0, progress(id).arrivedCount());
        assertNull(coordinator.getPendingStory());
        visits.failWrites = null; coordinator.retryVisit(); assertEquals(1, progress(id).arrivedCount());
    }
    @Test public void nextRunNeverRewritesPreviousDestinationOrInheritsItsProgress() {
        start(); String previous = coordinator.summary().getId();
        coordinator.selectDestination(ThemeCatalog.parkville().stop("old-quad").attraction());
        clock.advance(60_000); dwell("old-quad", 5); coordinator.requestStop();
        coordinator.requestStartTheme(ThemeCatalog.parkville());
        assertEquals("old-quad", sessions.findById(previous).destinationId);
        assertEquals(1, progress(previous).arrivedCount());
        assertEquals(0, progress(coordinator.summary().getId()).arrivedCount());
    }
    @Test public void pausedOrBackgroundedExplorationCannotCollectAndResumeUsesOneSubscription() {
        start(); coordinator.requestPause(); coordinator.requestPause(); assertFalse(location.isRegistered());
        coordinator.requestResume(); coordinator.requestResume(); assertEquals(2, location.registrationCount());
        coordinator.onVisibilityChanged(false); assertFalse(location.isRegistered());
        coordinator.onVisibilityChanged(true); assertFalse(location.isRegistered());
    }
    private void start() { coordinator.onVisibilityChanged(true); coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        coordinator.requestStartTheme(ThemeCatalog.parkville()); }
    private void dwell(String stopId, float accuracy) {
        ThemeStop stop = ThemeCatalog.parkville().stop(stopId);
        for (int i = 0; i < 3; i++) { clock.advance(5000); location.emit(new LocationSample(
                new GeoPoint(stop.latitude, stop.longitude), accuracy, clock.wallClockMillis(), clock.monotonicMillis(), LocationOrigin.DEVICE)); }
    }
    private ThemeProgress progress(String id) { ThemeProgress[] p = new ThemeProgress[1];
        repository.load(id, new RepositoryCallback<ThemeProgress>() {
            public void onSuccess(ThemeProgress value) { p[0] = value; }
            public void onError(Throwable error) { throw new AssertionError(error); }
        }); return p[0]; }
    private static <T> RepositoryCallback<T> success() { return new RepositoryCallback<T>() {
        public void onSuccess(T value) { }
        public void onError(Throwable error) { throw new AssertionError(error); }
    }; }
}
