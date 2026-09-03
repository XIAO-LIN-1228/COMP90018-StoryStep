package com.comp90018.storystep.ui.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

/** Screen state for a single attraction, loaded by identifier through the repository. */
public class AttractionDetailViewModel extends ViewModel {

    private final AttractionRepository repository;
    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<AttractionDetailUiState> uiState = new MutableLiveData<>();

    public AttractionDetailViewModel(AttractionRepository repository,
                                     ExplorationCoordinator coordinator) {
        this.repository = repository;
        this.coordinator = coordinator;
    }

    public LiveData<AttractionDetailUiState> getUiState() {
        return uiState;
    }

    public void load(String attractionId) {
        if (attractionId == null) {
            uiState.setValue(AttractionDetailUiState.error());
            return;
        }
        uiState.setValue(AttractionDetailUiState.loading());
        repository.loadAttraction(attractionId, new RepositoryCallback<Attraction>() {
            @Override
            public void onSuccess(Attraction attraction) {
                uiState.setValue(AttractionDetailUiState.content(attraction));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(AttractionDetailUiState.error());
            }
        });
    }

    /**
     * The user confirmed "start exploring" for the attraction currently on screen.
     *
     * <p>The destination is recorded either way, so it is already selected when the user comes back
     * with permission granted.
     *
     * @return true when a session is actually under way and the caller may open the dashboard;
     *         false when location access is missing, in which case the caller must send the user
     *         back to the nearby screen, which owns the Android permission and settings flow. This
     *         screen deliberately does not duplicate that flow: two places asking for the same
     *         permission is how apps end up in a request loop.
     */
    public boolean onStartExplorationRequested() {
        AttractionDetailUiState state = uiState.getValue();
        if (state == null || state.getAttraction() == null) {
            return false;
        }
        if (!coordinator.getAccess().isGranted()) {
            coordinator.selectDestination(state.getAttraction());
            return false;
        }
        coordinator.requestStart(state.getAttraction());
        return coordinator.summary().getState() != SessionState.IDLE;
    }

    /** Loads only if nothing has been published yet, so a rotation does not refetch. */
    public void loadIfNeeded(String attractionId) {
        if (uiState.getValue() == null) {
            load(attractionId);
        }
    }
}
