package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.DirectTransactions;
import com.comp90018.storystep.data.local.FakeRoutePointDao;
import com.comp90018.storystep.data.local.FakeSessionDao;
import com.comp90018.storystep.data.local.FakeVisitDao;

/**
 * Builds the real {@link RoomHistoryRepository} over fake DAOs and direct executors, so tests
 * exercise the production repository rather than a second implementation of the same rules.
 *
 * <p>Wires the session fake's cascade to the two child fakes. The real schema deletes route points
 * and visits through a foreign key, which no in-memory map does on its own; without this the JVM
 * tests would "prove" a cascade that only the database can actually perform. The instrumented DAO
 * test is what proves the real one.
 */
public final class TestHistory {

    private TestHistory() {
        // Utility class.
    }

    public static RoomHistoryRepository over(FakeSessionDao sessions,
                                             FakeRoutePointDao routePoints,
                                             FakeVisitDao visits) {
        sessions.cascadeListener = sessionId -> {
            routePoints.cascadeDelete(sessionId);
            visits.cascadeDelete(sessionId);
        };
        return new RoomHistoryRepository(new DirectTransactions(), sessions, routePoints, visits,
                Runnable::run, Runnable::run);
    }
}
