package com.comp90018.storystep.ui.journey;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the user's journey history and removes entries from it.
 *
 * <p>Observes the repository so that history cleared from the privacy screen leaves this list
 * correct without the user having to know to come back and refresh it.
 */
public class JourneysViewModel extends ViewModel implements HistoryRepository.Observer {

    private final HistoryRepository history;

    private final MutableLiveData<JourneysUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> deleteFailed = new MutableLiveData<>(false);

    /** Sessions whose deletion is still being written; a second tap on one of them is ignored. */
    private final Set<String> deleting = new HashSet<>();

    public JourneysViewModel(HistoryRepository history) {
        this.history = history;
        history.addObserver(this);
    }

    @Override
    protected void onCleared() {
        history.removeObserver(this);
        super.onCleared();
    }

    public LiveData<JourneysUiState> getUiState() {
        return uiState;
    }

    public LiveData<Boolean> getDeleteFailed() {
        return deleteFailed;
    }

    public void consumeDeleteFailure() {
        if (Boolean.TRUE.equals(deleteFailed.getValue())) {
            deleteFailed.setValue(false);
        }
    }

    /** Loads history. Safe to call again as a retry. */
    public void load() {
        uiState.setValue(JourneysUiState.loading());
        history.loadHistory(new RepositoryCallback<List<SessionHistoryEntry>>() {
            @Override
            public void onSuccess(List<SessionHistoryEntry> entries) {
                uiState.setValue(JourneysUiState.content(entries));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(JourneysUiState.error());
            }
        });
    }

    /** Loads only if nothing has been published yet, so a rotation does not re-read the database. */
    public void loadIfNeeded() {
        if (uiState.getValue() == null) {
            load();
        }
    }

    /**
     * Removes one journey. Called only after the user has confirmed it.
     *
     * <p>The reload comes from {@link #onHistoryChanged()}, which the repository publishes for
     * every successful removal. Reloading here as well would read the database twice for one tap,
     * and re-reading rather than dropping the row locally is what guarantees the list and the
     * database cannot disagree.
     */
    public void onDeleteConfirmed(String sessionId) {
        if (sessionId == null || deleting.contains(sessionId)) {
            return;
        }
        deleting.add(sessionId);
        history.delete(sessionId, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean removed) {
                deleting.remove(sessionId);
                // The observer has already reloaded. A session that was already gone reports
                // false and publishes nothing, and the list is correct either way.
            }

            @Override
            public void onError(Throwable error) {
                deleting.remove(sessionId);
                deleteFailed.setValue(true);
            }
        });
    }

    @Override
    public void onHistoryChanged() {
        // Only once something has been shown: before that, loadIfNeeded owns the first read.
        if (uiState.getValue() != null) {
            load();
        }
    }
}
