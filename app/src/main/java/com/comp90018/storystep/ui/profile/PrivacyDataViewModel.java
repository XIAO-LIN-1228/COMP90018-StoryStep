package com.comp90018.storystep.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

/**
 * The data controls on the privacy screen.
 *
 * <p>Both controls are real. Each clears exactly its own kind of record and nothing else: saved
 * places and journey history are separate user-owned data, and clearing one must never take the
 * other with it.
 */
public class PrivacyDataViewModel extends ViewModel {

    /** What the screen should say after a clear attempt. */
    public enum ClearOutcome {
        CLEARED,
        FAILED
    }

    private final SavedAttractionRepository savedAttractions;
    private final HistoryRepository history;

    private final MutableLiveData<ClearOutcome> savedCleared = new MutableLiveData<>();
    private final MutableLiveData<ClearOutcome> historyCleared = new MutableLiveData<>();

    /** Guards against a second tap issuing a second clear while the first is still being written. */
    private boolean clearingSaved;
    private boolean clearingHistory;

    public PrivacyDataViewModel(SavedAttractionRepository savedAttractions,
                                HistoryRepository history) {
        this.savedAttractions = savedAttractions;
        this.history = history;
    }

    /** Emitted once per clear attempt; the screen consumes it after reporting. */
    public LiveData<ClearOutcome> getSavedCleared() {
        return savedCleared;
    }

    public void consumeSavedCleared() {
        savedCleared.setValue(null);
    }

    /** Emitted once per clear attempt; the screen consumes it after reporting. */
    public LiveData<ClearOutcome> getHistoryCleared() {
        return historyCleared;
    }

    public void consumeHistoryCleared() {
        historyCleared.setValue(null);
    }

    /** The user confirmed clearing every saved place. Journey history is untouched. */
    public void onClearSavedConfirmed() {
        if (clearingSaved) {
            return;
        }
        clearingSaved = true;
        savedAttractions.clearAll(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer removed) {
                clearingSaved = false;
                savedCleared.setValue(ClearOutcome.CLEARED);
            }

            @Override
            public void onError(Throwable error) {
                clearingSaved = false;
                savedCleared.setValue(ClearOutcome.FAILED);
            }
        });
    }

    /**
     * The user confirmed clearing their journey history.
     *
     * <p>Finished journeys only, and saved places are untouched — both are properties of
     * {@code HistoryRepository.clearAll}, and both are stated in the confirmation the user just
     * accepted.
     */
    public void onClearHistoryConfirmed() {
        if (clearingHistory) {
            return;
        }
        clearingHistory = true;
        history.clearAll(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer removed) {
                clearingHistory = false;
                historyCleared.setValue(ClearOutcome.CLEARED);
            }

            @Override
            public void onError(Throwable error) {
                clearingHistory = false;
                historyCleared.setValue(ClearOutcome.FAILED);
            }
        });
    }
}
