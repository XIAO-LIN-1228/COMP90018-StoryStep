package com.comp90018.storystep.ui.journey;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionNotFoundException;

/**
 * Reads one recorded journey.
 *
 * <p>Everything it publishes came off the device: the visits carry their own snapshotted names and
 * story text, and the weather and movement were copied onto the session while it ran. Opening a
 * journey therefore works with no network and with the attraction cache empty.
 */
public class JourneyDetailViewModel extends ViewModel {

    private final HistoryRepository history;
    private final MutableLiveData<JourneyDetailUiState> uiState = new MutableLiveData<>();

    private String loadedSessionId;

    public JourneyDetailViewModel(HistoryRepository history) {
        this.history = history;
    }

    public LiveData<JourneyDetailUiState> getUiState() {
        return uiState;
    }

    /** Loads only if this session has not been published yet, so a rotation does not re-read. */
    public void loadIfNeeded(String sessionId) {
        if (uiState.getValue() != null && sessionId != null
                && sessionId.equals(loadedSessionId)) {
            return;
        }
        load(sessionId);
    }

    public void load(String sessionId) {
        if (sessionId == null) {
            // Navigated here without an id. Nothing to look up, and the same dead end for the
            // user as a journey that no longer exists.
            uiState.setValue(JourneyDetailUiState.notFound());
            return;
        }
        loadedSessionId = sessionId;
        uiState.setValue(JourneyDetailUiState.loading());
        history.loadDetail(sessionId, new RepositoryCallback<SessionHistoryDetail>() {
            @Override
            public void onSuccess(SessionHistoryDetail detail) {
                uiState.setValue(JourneyDetailUiState.content(detail));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(error instanceof SessionNotFoundException
                        ? JourneyDetailUiState.notFound()
                        : JourneyDetailUiState.error());
            }
        });
    }
}
