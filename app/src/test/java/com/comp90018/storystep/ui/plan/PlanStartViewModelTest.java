package com.comp90018.storystep.ui.plan;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.SensingSources;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class PlanStartViewModelTest {
    @Rule public final InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private final FakeClock clock = FakeClock.startingAt(1_000);
    private final FakeLocationSource location = new FakeLocationSource();
    private final DeferredStore store = new DeferredStore();
    private final Attraction destination = SampleAttractions.get().get(0);
    private ExplorationCoordinator coordinator;
    private PlanStartViewModel model;

    @Before public void setUp() {
        coordinator = TestCoordinators.with(clock, location, SensingSources.none(), store);
        coordinator.onVisibilityChanged(true);
        model = new PlanStartViewModel(coordinator);
    }

    @Test public void deniedStartsNeverCreateARecordOrRegisterLocation() {
        for (LocationAccess access : new LocationAccess[] {LocationAccess.NOT_REQUESTED,
                LocationAccess.DENIED, LocationAccess.PERMANENTLY_DENIED}) {
            model.start(destination, access);
            assertEquals(PlanStartViewModel.State.FAILED, model.getState().getValue());
            assertEquals(SessionState.IDLE, coordinator.summary().getState());
            assertNull(coordinator.summary().getId());
        }
        assertEquals(0, store.creates);
        assertEquals(0, location.registrationCount());
    }

    @Test public void navigationWaitsForPersistenceAndDoubleStartCreatesOnlyOnce() {
        model.start(destination, LocationAccess.PRECISE);
        model.start(destination, LocationAccess.PRECISE);
        assertEquals(1, store.creates);
        assertEquals(PlanStartViewModel.State.STARTING, model.getState().getValue());
        assertNull(coordinator.summary().getId());
        assertEquals(0, location.registrationCount());
        store.succeed();
        assertEquals(PlanStartViewModel.State.READY, model.getState().getValue());
        assertNotNull(coordinator.summary().getId());
        assertEquals(1, location.registrationCount());
    }

    @Test public void failedPersistenceStaysInPlannerAndCanBeRetried() {
        model.start(destination, LocationAccess.PRECISE);
        store.pending.onError(new IllegalStateException("Cannot write session"));
        assertEquals(PlanStartViewModel.State.FAILED, model.getState().getValue());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
        assertEquals(0, location.registrationCount());
        model.start(destination, LocationAccess.PRECISE);
        store.succeed();
        assertEquals(2, store.creates);
        assertEquals(PlanStartViewModel.State.READY, model.getState().getValue());
    }

    @Test public void approximatePermissionCanStartAWalk() {
        model.start(destination, LocationAccess.APPROXIMATE);
        store.succeed();
        assertEquals(PlanStartViewModel.State.READY, model.getState().getValue());
        assertEquals(LocationAccess.APPROXIMATE, coordinator.getAccess());
    }

    @Test public void losingAccessDuringTheWriteOpensAPausedSessionWithoutCollection() {
        model.start(destination, LocationAccess.PRECISE);
        coordinator.onLocationAccessChanged(LocationAccess.DENIED);
        store.succeed();
        assertEquals(PlanStartViewModel.State.READY, model.getState().getValue());
        assertEquals(SessionState.PAUSED, coordinator.summary().getState());
        assertEquals(0, location.registrationCount());
    }

    @Test public void backgroundedStartCannotOfferAnIdleDashboard() {
        coordinator.onVisibilityChanged(false);
        model.start(destination, LocationAccess.PRECISE);
        assertEquals(PlanStartViewModel.State.FAILED, model.getState().getValue());
        assertEquals(0, store.creates);
        assertEquals(0, location.registrationCount());
    }

    @Test public void returningToPlannerDoesNotNavigateAgainOnSessionUpdates() {
        model.start(destination, LocationAccess.PRECISE);
        store.succeed();
        model.acknowledgeResult();
        coordinator.requestPause();
        assertEquals(PlanStartViewModel.State.IDLE, model.getState().getValue());
    }

    @Test public void anExistingPausedWalkIsPreservedWithoutStartingAnother() {
        model.start(destination, LocationAccess.PRECISE);
        store.succeed();
        model.acknowledgeResult();
        coordinator.requestPause();
        String id = coordinator.summary().getId();
        model.start(SampleAttractions.get().get(1), LocationAccess.PRECISE);
        assertEquals(1, store.creates);
        assertEquals(id, coordinator.summary().getId());
        assertEquals(destination, coordinator.getDestination());
        assertEquals(SessionState.PAUSED, coordinator.summary().getState());
        assertEquals(PlanStartViewModel.State.READY, model.getState().getValue());
    }

    /** Delays only creation; all other storage operations use the ordinary in-memory store. */
    private final class DeferredStore implements SessionStore {
        private final InMemorySessionStore delegate = new InMemorySessionStore(clock);
        private RepositoryCallback<SessionSummary> pending;
        private int creates;
        @Override public void create(RepositoryCallback<SessionSummary> callback) {
            creates++;
            pending = callback;
        }
        void succeed() { delegate.create(pending); }
        @Override public void checkpoint(SessionSummary s, RepositoryCallback<Void> c) { delegate.checkpoint(s, c); }
        @Override public void appendRoute(String id, int first, List<RoutePoint> points, double metres,
                int segments, RepositoryCallback<Void> c) { delegate.appendRoute(id, first, points, metres, segments, c); }
        @Override public void saveDestination(String id, Attraction a, RepositoryCallback<Void> c) { delegate.saveDestination(id, a, c); }
        @Override public void saveSessionContext(String id, MovementSnapshot m, WeatherSnapshot w,
                RepositoryCallback<Void> c) { delegate.saveSessionContext(id, m, w, c); }
        @Override public void finish(SessionSummary s, RepositoryCallback<Void> c) { delegate.finish(s, c); }
        @Override public void discard(String id, RepositoryCallback<Void> c) { delegate.discard(id, c); }
        @Override public void recordVisit(Visit v, RepositoryCallback<Visit> c) { delegate.recordVisit(v, c); }
        @Override public void findUnreadVisit(String id, RepositoryCallback<Visit> c) { delegate.findUnreadVisit(id, c); }
        @Override public void findVisit(String id, String attraction, RepositoryCallback<Visit> c) { delegate.findVisit(id, attraction, c); }
        @Override public void markVisitRead(String id, String attraction, RepositoryCallback<Void> c) { delegate.markVisitRead(id, attraction, c); }
        @Override public void findUnfinished(RepositoryCallback<RestoredSession> c) { delegate.findUnfinished(c); }
    }
}
