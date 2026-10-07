package com.comp90018.storystep.ui.exploration;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.application.WalkPlanner;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.plan.PlannedRoute;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

/** Planned geometry never becomes recorded travel; late routing responses cannot replace a new plan. */
public class ExplorationPlannedRouteTest {
    @Rule public InstantTaskExecutorRule executorRule = new InstantTaskExecutorRule();
    private final GeoPoint origin = new GeoPoint(-37.7963, 144.9614);
    private final Attraction first = Attraction.seeded("first", "First stop", AttractionCategory.PARK,
            "", IndoorClassification.OUTDOOR, new GeoPoint(-37.7943, 144.9614));
    private final Attraction second = Attraction.seeded("second", "Second stop", AttractionCategory.PARK,
            "", IndoorClassification.OUTDOOR, new GeoPoint(-37.7943, 144.9634));
    private final WalkPlanner planner = new WalkPlanner();
    private final List<Runnable> requests = new ArrayList<>();
    private final FakeClock clock = FakeClock.startingAt(1_000L);
    private final FakeLocationSource location = new FakeLocationSource();
    private ExplorationViewModel model;
    private boolean routingAvailable = true;

    @Before public void startPlannedWalk() {
        planner.setPlan(WalkPlan.EMPTY.with(first).with(second));
        ExplorationCoordinator coordinator = TestCoordinators.with(clock, location);
        model = new ExplorationViewModel(coordinator, new ExplorationViewModel.Ticker() {
            @Override public void start(Runnable tick, long interval) { }
            @Override public void stop() { }
        }, planner, points -> routingAvailable ? PlannedRoute.walked(points, 300.0, 250_000L) : null,
                requests::add, Runnable::run);
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        coordinator.requestStart(first);
    }

    @Test public void planAppearsBeforeGpsButRoutingWaitsForTheRealOrigin() {
        assertEquals(2, state().getPlannedStops().size());
        assertFalse(state().getPlannedRoute().followsStreets());
        assertNull(state().getPosition());
        assertTrue(state().getSegments().isEmpty());
        assertTrue(requests.isEmpty());
        fix(origin);
        assertEquals(1, requests.size());
        requests.get(0).run();
        assertTrue(state().getPlannedRoute().followsStreets());
        assertEquals(origin, state().getPlannedRoute().getPath().get(0));
        assertEquals(3, state().getPlannedRoute().getPath().size());
        assertEquals("Future stops are not recorded travel", 1, state().getSegments().get(0).size());
        clock.advance(10_000L);
        fix(new GeoPoint(-37.7962, 144.9614));
        assertEquals("Movement must not cause continuous rerouting", 1, requests.size());
        assertEquals(2, state().getSegments().get(0).size());
    }

    @Test public void offlineRoutingKeepsALabeledStraightLinePlanAndTheRealTrail() {
        routingAvailable = false;
        fix(origin);
        requests.get(0).run();
        assertFalse(state().getPlannedRoute().followsStreets());
        assertEquals(3, state().getPlannedRoute().getPath().size());
        assertEquals(origin, state().getPosition());
        assertEquals(1, state().getSegments().size());
    }

    @Test public void anOldRoutingResponseCannotOverwriteAnEditedPlan() {
        fix(origin);
        planner.remove(second.getId());
        assertEquals(2, requests.size());
        requests.get(0).run();
        assertFalse(state().getPlannedRoute().followsStreets());
        assertEquals(1, state().getPlannedStops().size());
        requests.get(1).run();
        assertTrue(state().getPlannedRoute().followsStreets());
        assertEquals(2, state().getPlannedRoute().getPath().size());
    }

    @Test public void routingDoesNotPublishAfterTheScreenModelIsCleared() {
        fix(origin);
        ExplorationMapState before = state();
        model.onCleared();
        requests.get(0).run();
        assertSame(before, state());
    }

    private void fix(GeoPoint point) {
        location.emit(new LocationSample(point, 5f, clock.wallClockMillis(), clock.monotonicMillis(),
                LocationOrigin.DEVICE));
    }

    private ExplorationMapState state() { return model.getMapState().getValue(); }
}
