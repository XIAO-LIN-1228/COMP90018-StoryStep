package com.comp90018.storystep.ui.profile;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.comp90018.storystep.data.local.*;
import com.comp90018.storystep.data.repository.*;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.*;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;

public class ProfileViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    @Test public void actualCountsChangeAfterDeletionAndSaveWithoutFixtureStatistics() {
        FakeSessionDao sessions = new FakeSessionDao();
        FakeVisitDao visits = new FakeVisitDao();
        RoomHistoryRepository history = TestHistory.over(sessions, new FakeRoutePointDao(), visits);
        SessionEntity finished = new SessionEntity();
        finished.id = "finished";
        finished.state = "COMPLETED";
        finished.finished = true;
        finished.startWallClockMillis = 1000;
        finished.endWallClockMillis = 2000;
        sessions.insert(finished);
        RoomSavedAttractionRepository saved = TestSavedAttractions.empty();
        ProfileViewModel model = new ProfileViewModel(history, saved);
        model.refresh();
        assertEquals(Integer.valueOf(1), model.getJourneys().getValue());
        assertEquals(Integer.valueOf(0), model.getVisits().getValue());
        saved.save(Attraction.seeded("place", "Place", AttractionCategory.PARK, "",
                IndoorClassification.OUTDOOR, new GeoPoint(0, 0)), success());
        assertEquals(Integer.valueOf(1), model.getSaves().getValue());
        history.clearAll(success());
        assertEquals(Integer.valueOf(0), model.getJourneys().getValue());
        assertEquals(Integer.valueOf(1), model.getSaves().getValue());
    }

    @Test public void failedCountsAreUnknownRatherThanZero() {
        DelayedHistory history = new DelayedHistory();
        ProfileViewModel model = new ProfileViewModel(history, TestSavedAttractions.empty());
        model.refresh();
        history.pending.onError(new IllegalStateException("unreadable"));
        assertNull(model.getJourneys().getValue());
        assertEquals(Integer.valueOf(0), model.getSaves().getValue());
    }

    @Test public void oldReadAndClearedModelCannotOverwriteNewCounts() {
        DelayedHistory history = new DelayedHistory();
        ProfileViewModel model = new ProfileViewModel(history, TestSavedAttractions.empty());
        model.refresh();
        RepositoryCallback<List<SessionHistoryEntry>> old = history.pending;
        model.refresh();
        history.pending.onSuccess(Collections.emptyList());
        old.onSuccess(Collections.singletonList(new SessionHistoryEntry("old", 1000, 2000, 1000, 0, 0, 0, false, null)));
        assertEquals(Integer.valueOf(0), model.getJourneys().getValue());
        model.refresh();
        model.onCleared();
        history.pending.onError(new IllegalStateException());
        assertEquals(Integer.valueOf(0), model.getJourneys().getValue());
    }

    private static <T> RepositoryCallback<T> success() {
        return new RepositoryCallback<T>() {
            public void onSuccess(T value) { }
            public void onError(Throwable error) { throw new AssertionError(error); }
        };
    }

    private static class DelayedHistory implements HistoryRepository {
        RepositoryCallback<List<SessionHistoryEntry>> pending;
        public void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> cb) { pending = cb; }
        public void loadDetail(String id, RepositoryCallback<SessionHistoryDetail> cb) { throw new AssertionError(); }
        public void delete(String id, RepositoryCallback<Boolean> cb) { throw new AssertionError(); }
        public void clearAll(RepositoryCallback<Integer> cb) { throw new AssertionError(); }
        public void addObserver(Observer observer) { }
        public void removeObserver(Observer observer) { }
    }
}
