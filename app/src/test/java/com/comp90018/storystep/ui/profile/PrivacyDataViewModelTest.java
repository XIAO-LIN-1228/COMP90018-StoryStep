package com.comp90018.storystep.ui.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.data.local.FakeRoutePointDao;
import com.comp90018.storystep.data.local.FakeSavedAttractionDao;
import com.comp90018.storystep.data.local.FakeSessionDao;
import com.comp90018.storystep.data.local.FakeVisitDao;
import com.comp90018.storystep.data.local.LocalSavedAttractionMapper;
import com.comp90018.storystep.data.local.SessionEntity;
import com.comp90018.storystep.data.repository.TestHistory;
import com.comp90018.storystep.data.repository.TestSavedAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * The privacy screen's two clear controls.
 *
 * <p>The property under test is independence. Saved places and journey history are separate
 * user-owned records, and clearing one must never take any part of the other with it — the
 * confirmation the user accepted says so explicitly.
 */
public class PrivacyDataViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final Attraction MUSEUM = new Attraction("museum", "Melbourne Museum",
            AttractionCategory.MUSEUM, "Natural history and culture.", true,
            new GeoPoint(-37.80320, 144.97170));

    private FakeSavedAttractionDao savedDao;
    private FakeSessionDao sessions;
    private FakeRoutePointDao routePoints;
    private FakeVisitDao visits;
    private SavedAttractionRepository saved;
    private HistoryRepository history;

    @Before
    public void setUp() {
        savedDao = new FakeSavedAttractionDao();
        sessions = new FakeSessionDao();
        routePoints = new FakeRoutePointDao();
        visits = new FakeVisitDao();
        saved = TestSavedAttractions.over(savedDao);
        history = TestHistory.over(sessions, routePoints, visits);
    }

    @Test
    public void clearingHistoryLeavesSavedPlacesAlone() {
        savedDao.insertIgnoring(LocalSavedAttractionMapper.toEntity(MUSEUM, 1_000L));
        storeFinishedSession("walk");
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, history);

        viewModel.onClearHistoryConfirmed();

        assertEquals(PrivacyDataViewModel.ClearOutcome.CLEARED,
                viewModel.getHistoryCleared().getValue());
        assertEquals("history is gone", 0, sessions.size());
        assertEquals("a saved place is the user's separately and must survive",
                1, savedDao.count());
    }

    @Test
    public void clearingSavedPlacesLeavesHistoryAlone() {
        savedDao.insertIgnoring(LocalSavedAttractionMapper.toEntity(MUSEUM, 1_000L));
        storeFinishedSession("walk");
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, history);

        viewModel.onClearSavedConfirmed();

        assertEquals(PrivacyDataViewModel.ClearOutcome.CLEARED,
                viewModel.getSavedCleared().getValue());
        assertEquals(0, savedDao.count());
        assertEquals("a recorded walk must survive clearing saved places", 1, sessions.size());
    }

    @Test
    public void clearingHistoryLeavesAWalkInProgressAlone() {
        storeFinishedSession("finished");
        storeUnfinishedSession("running");
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, history);

        viewModel.onClearHistoryConfirmed();

        assertEquals("clearing history must not throw away the walk the user is on",
                1, sessions.size());
        assertEquals("running", sessions.findUnfinished().id);
    }

    @Test
    public void aFailedHistoryClearIsReportedRatherThanAssumedToHaveWorked() {
        storeFinishedSession("walk");
        sessions.failWrites = new IllegalStateException("database locked");
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, history);

        viewModel.onClearHistoryConfirmed();

        assertEquals(PrivacyDataViewModel.ClearOutcome.FAILED,
                viewModel.getHistoryCleared().getValue());
    }

    @Test
    public void theClearOutcomeIsConsumedAfterBeingReported() {
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, history);
        viewModel.onClearHistoryConfirmed();

        viewModel.consumeHistoryCleared();

        assertNull("a one-shot signal must not survive being reported",
                viewModel.getHistoryCleared().getValue());
    }

    @Test
    public void aSecondTapWhileTheFirstClearIsStillInFlightIsIgnored() {
        CountingHistory counting = new CountingHistory();
        PrivacyDataViewModel viewModel = new PrivacyDataViewModel(saved, counting);

        viewModel.onClearHistoryConfirmed();
        viewModel.onClearHistoryConfirmed();

        assertEquals("a double tap must not issue a second clear", 1, counting.clears);
    }

    private void storeFinishedSession(String id) {
        SessionEntity session = new SessionEntity();
        session.id = id;
        session.startWallClockMillis = 1_000L;
        session.endWallClockMillis = 2_000L;
        session.finished = true;
        sessions.insert(session);
    }

    private void storeUnfinishedSession(String id) {
        SessionEntity session = new SessionEntity();
        session.id = id;
        session.startWallClockMillis = 5_000L;
        session.finished = false;
        sessions.insert(session);
    }

    /** Never answers a clear, so the in-flight window stays open for the double-tap case. */
    private static final class CountingHistory implements HistoryRepository {

        int clears;

        @Override
        public void loadHistory(RepositoryCallback<java.util.List<
                com.comp90018.storystep.domain.model.SessionHistoryEntry>> callback) {
            callback.onSuccess(java.util.Collections.emptyList());
        }

        @Override
        public void loadDetail(String sessionId, RepositoryCallback<
                com.comp90018.storystep.domain.model.SessionHistoryDetail> callback) {
            callback.onError(new UnsupportedOperationException("not used by this test"));
        }

        @Override
        public void delete(String sessionId, RepositoryCallback<Boolean> callback) {
            callback.onSuccess(false);
        }

        @Override
        public void clearAll(RepositoryCallback<Integer> callback) {
            clears++;
            // Deliberately no answer: the write is still in flight.
        }

        @Override
        public void addObserver(Observer observer) {
        }

        @Override
        public void removeObserver(Observer observer) {
        }
    }
}
