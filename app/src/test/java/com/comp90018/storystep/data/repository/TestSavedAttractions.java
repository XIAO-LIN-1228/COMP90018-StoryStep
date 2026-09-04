package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.FakeSavedAttractionDao;
import com.comp90018.storystep.domain.session.FakeClock;

/**
 * Builds the real {@link RoomSavedAttractionRepository} over a fake DAO and direct executors, so
 * ViewModel tests exercise the production store rather than a second implementation of the same
 * rules written only for tests.
 */
public final class TestSavedAttractions {

    private TestSavedAttractions() {
        // Utility class.
    }

    public static RoomSavedAttractionRepository over(FakeSavedAttractionDao dao) {
        return new RoomSavedAttractionRepository(
                dao, FakeClock.startingAt(1_000L), Runnable::run, Runnable::run);
    }

    public static RoomSavedAttractionRepository empty() {
        return over(new FakeSavedAttractionDao());
    }
}
