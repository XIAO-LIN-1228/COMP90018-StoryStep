package com.comp90018.storystep.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

/**
 * The data controls on the privacy screen.
 *
 * <p>Only saved places are actually clearable so far. Clearing journey history stays a prototype
 * until the deletion rules of REQ-16 and the audit of NFR-04 are settled — a control that half
 * deletes is worse than one that visibly does not.
 */
public class PrivacyDataViewModel extends ViewModel {

    /** What the screen should say after a clear attempt. */
    public enum ClearOutcome {
        CLEARED,
        FAILED
    }

    private final SavedAttractionRepository savedAttractions;
    private final MutableLiveData<ClearOutcome> savedCleared = new MutableLiveData<>();

    public PrivacyDataViewModel(SavedAttractionRepository savedAttractions) {
        this.savedAttractions = savedAttractions;
    }

    /** Emitted once per clear attempt; the screen consumes it after reporting. */
    public LiveData<ClearOutcome> getSavedCleared() {
        return savedCleared;
    }

    public void consumeSavedCleared() {
        savedCleared.setValue(null);
    }

    /** The user confirmed clearing every saved place. Journey history is untouched. */
    public void onClearSavedConfirmed() {
        savedAttractions.clearAll(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer removed) {
                savedCleared.setValue(ClearOutcome.CLEARED);
            }

            @Override
            public void onError(Throwable error) {
                savedCleared.setValue(ClearOutcome.FAILED);
            }
        });
    }
}
