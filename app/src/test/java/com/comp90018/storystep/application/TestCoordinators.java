package com.comp90018.storystep.application;

import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.platform.location.FakeLocationSource;

/**
 * Builds a coordinator wired entirely to fakes, for tests about screens rather than about the
 * coordinator itself.
 *
 * <p>Shared so that a change to the coordinator's dependencies is a one-line change here instead
 * of an edit in every ViewModel test.
 */
public final class TestCoordinators {

    private TestCoordinators() {
    }

    public static ExplorationCoordinator with(FakeClock clock, FakeLocationSource locationSource) {
        return new ExplorationCoordinator(
                new SessionStateMachine(clock),
                new InMemorySessionStore(clock),
                locationSource,
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock);
    }

    /** A coordinator with its own fresh fakes, for tests that never drive time or location. */
    public static ExplorationCoordinator idle() {
        return with(FakeClock.startingAt(1_000L), new FakeLocationSource());
    }
}
