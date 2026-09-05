package com.comp90018.storystep.ui.journey;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The history list's states and its deletion behaviour.
 *
 * <p>The distinction that matters most here is EMPTY against ERROR: "you have not explored yet"
 * and "your history did not load" must look different, or a storage failure reads to the user as
 * their walks having been lost.
 */
public class JourneysViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static SessionHistoryEntry entry(String id) {
        return new SessionHistoryEntry(id, 1_000L, 2_000L, 60_000L, 250.0, 2, 1, false, "Museum");
    }

    @Test
    public void anEmptyHistoryIsReportedAsEmptyRatherThanAsContent() {
        JourneysViewModel viewModel = new JourneysViewModel(new FakeHistory());

        viewModel.loadIfNeeded();

        assertEquals(JourneysUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aFailedReadIsReportedAsAnErrorRatherThanAsAnEmptyHistory() {
        FakeHistory history = new FakeHistory();
        history.failReads = true;
        JourneysViewModel viewModel = new JourneysViewModel(history);

        viewModel.loadIfNeeded();

        assertEquals("a storage failure must never look like having explored nothing",
                JourneysUiState.Status.ERROR, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void recordedJourneysAreShownAsContent() {
        FakeHistory history = new FakeHistory();
        history.entries = Arrays.asList(entry("first"), entry("second"));
        JourneysViewModel viewModel = new JourneysViewModel(history);

        viewModel.loadIfNeeded();

        assertEquals(JourneysUiState.Status.CONTENT, viewModel.getUiState().getValue().getStatus());
        assertEquals(2, viewModel.getUiState().getValue().getJourneys().size());
    }

    @Test
    public void loadIfNeededDoesNotReReadAfterSomethingHasBeenPublished() {
        FakeHistory history = new FakeHistory();
        history.entries = Collections.singletonList(entry("first"));
        JourneysViewModel viewModel = new JourneysViewModel(history);

        viewModel.loadIfNeeded();
        viewModel.loadIfNeeded();

        assertEquals("a rotation must not re-read the database", 1, history.reads);
    }

    @Test
    public void deletingReloadsExactlyOnce() {
        FakeHistory history = new FakeHistory();
        history.entries = new ArrayList<>(Collections.singletonList(entry("first")));
        JourneysViewModel viewModel = new JourneysViewModel(history);
        viewModel.loadIfNeeded();
        int readsBeforeDelete = history.reads;

        viewModel.onDeleteConfirmed("first");

        assertEquals("one tap must produce one re-read, not one per notification path",
                readsBeforeDelete + 1, history.reads);
        assertEquals(JourneysUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aSecondTapOnAJourneyAlreadyBeingDeletedIsIgnored() {
        DeferredHistory history = new DeferredHistory();
        history.entries = Collections.singletonList(entry("first"));
        JourneysViewModel viewModel = new JourneysViewModel(history);
        viewModel.loadIfNeeded();

        viewModel.onDeleteConfirmed("first");
        viewModel.onDeleteConfirmed("first");

        assertEquals("a double tap must not issue a second delete",
                1, history.deleteRequests);
    }

    @Test
    public void aFailedDeleteIsReportedOnceAndThenConsumed() {
        FakeHistory history = new FakeHistory();
        history.entries = Collections.singletonList(entry("first"));
        history.failDeletes = true;
        JourneysViewModel viewModel = new JourneysViewModel(history);
        viewModel.loadIfNeeded();

        viewModel.onDeleteConfirmed("first");
        assertTrue(viewModel.getDeleteFailed().getValue());

        viewModel.consumeDeleteFailure();
        assertFalse("a one-shot signal must not survive being reported",
                viewModel.getDeleteFailed().getValue());
    }

    @Test
    public void aFailedDeleteCanBeRetried() {
        FakeHistory history = new FakeHistory();
        history.entries = new ArrayList<>(Collections.singletonList(entry("first")));
        history.failDeletes = true;
        JourneysViewModel viewModel = new JourneysViewModel(history);
        viewModel.loadIfNeeded();
        viewModel.onDeleteConfirmed("first");

        history.failDeletes = false;
        viewModel.onDeleteConfirmed("first");

        assertEquals("the in-flight guard must release on failure, or retry is impossible",
                JourneysUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void historyClearedElsewhereReloadsThisList() {
        FakeHistory history = new FakeHistory();
        history.entries = new ArrayList<>(Arrays.asList(entry("first"), entry("second")));
        JourneysViewModel viewModel = new JourneysViewModel(history);
        viewModel.loadIfNeeded();

        // What the privacy screen's clear-all does.
        history.entries = Collections.emptyList();
        history.publish();

        assertEquals("the list must not keep showing journeys another screen deleted",
                JourneysUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aNotificationBeforeTheFirstLoadDoesNotReadTheDatabase() {
        FakeHistory history = new FakeHistory();
        new JourneysViewModel(history);

        history.publish();

        assertEquals("loadIfNeeded owns the first read", 0, history.reads);
    }

    @Test
    public void theObserverIsRemovedWhenTheScreenGoesAway() {
        FakeHistory history = new FakeHistory();
        JourneysViewModel viewModel = new JourneysViewModel(history);
        assertEquals(1, history.observers.size());

        // Same package, so the protected teardown is reachable without a test-only hook.
        viewModel.onCleared();

        assertEquals("a leaked observer would keep a dead screen reloading",
                0, history.observers.size());
    }

    /** Synchronous history, answering on the calling thread the way direct executors do. */
    private static class FakeHistory implements HistoryRepository {

        final List<Observer> observers = new ArrayList<>();
        List<SessionHistoryEntry> entries = Collections.emptyList();
        boolean failReads;
        boolean failDeletes;
        int reads;
        int deleteRequests;

        @Override
        public void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> callback) {
            reads++;
            if (failReads) {
                callback.onError(new IllegalStateException("database unavailable"));
                return;
            }
            callback.onSuccess(entries);
        }

        @Override
        public void loadDetail(String sessionId,
                               RepositoryCallback<SessionHistoryDetail> callback) {
            callback.onError(new UnsupportedOperationException("not used by this test"));
        }

        @Override
        public void delete(String sessionId, RepositoryCallback<Boolean> callback) {
            deleteRequests++;
            if (failDeletes) {
                callback.onError(new IllegalStateException("write failed"));
                return;
            }
            List<SessionHistoryEntry> remaining = new ArrayList<>();
            for (SessionHistoryEntry entry : entries) {
                if (!entry.getId().equals(sessionId)) {
                    remaining.add(entry);
                }
            }
            boolean removed = remaining.size() != entries.size();
            entries = remaining;
            if (removed) {
                publish();
            }
            callback.onSuccess(removed);
        }

        @Override
        public void clearAll(RepositoryCallback<Integer> callback) {
            int removed = entries.size();
            entries = Collections.emptyList();
            if (removed > 0) {
                publish();
            }
            callback.onSuccess(removed);
        }

        @Override
        public void addObserver(Observer observer) {
            observers.add(observer);
        }

        @Override
        public void removeObserver(Observer observer) {
            observers.remove(observer);
        }

        void publish() {
            for (Observer observer : new ArrayList<>(observers)) {
                observer.onHistoryChanged();
            }
        }
    }

    /** Never answers a delete, so the in-flight window stays open for the double-tap case. */
    private static final class DeferredHistory extends FakeHistory {

        @Override
        public void delete(String sessionId, RepositoryCallback<Boolean> callback) {
            deleteRequests++;
            // Deliberately no answer: the write is still in flight.
        }
    }
}
