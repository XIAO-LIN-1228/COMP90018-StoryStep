package com.comp90018.storystep.ui.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

import java.util.Set;

/** Screen state for a single attraction, loaded by identifier through the repository. */
public class AttractionDetailViewModel extends ViewModel
        implements SavedAttractionRepository.Observer {

    private final AttractionRepository repository;
    private final SavedAttractionRepository savedAttractions;
    private final ExplorationCoordinator coordinator;
    private final MutableLiveData<AttractionDetailUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveFailed = new MutableLiveData<>(false);

    /** Identifiers reported by the store; the screen's bookmark is a lookup in here. */
    private Set<String> savedIds = java.util.Collections.emptySet();

    /** The attraction whose save is still being written, or null when none is. */
    private String pendingToggleId;

    public AttractionDetailViewModel(AttractionRepository repository,
                                     SavedAttractionRepository savedAttractions,
                                     ExplorationCoordinator coordinator) {
        this.repository = repository;
        this.savedAttractions = savedAttractions;
        this.coordinator = coordinator;
        savedAttractions.addObserver(this);
    }

    @Override
    protected void onCleared() {
        // The repository is process-scoped and outlives this screen; leaving the observer
        // registered would keep publishing into a ViewModel nobody is watching.
        savedAttractions.removeObserver(this);
        super.onCleared();
    }

    public LiveData<AttractionDetailUiState> getUiState() {
        return uiState;
    }

    /** True once a save or unsave has failed; the screen consumes it and reports it once. */
    public LiveData<Boolean> getSaveFailed() {
        return saveFailed;
    }

    public void consumeSaveFailure() {
        if (Boolean.TRUE.equals(saveFailed.getValue())) {
            saveFailed.setValue(false);
        }
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
                uiState.setValue(AttractionDetailUiState.content(attraction)
                        .withSaved(savedIds.contains(attraction.getId()), false));
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

    // --- saving --------------------------------------------------------------------------------

    /**
     * The user tapped the bookmark.
     *
     * <p>Does nothing while a write for this attraction is outstanding. Serialising here rather
     * than disabling the control keeps the screen responsive, and means a burst of taps resolves
     * to one write per completed round trip instead of a race between two writes at the same row.
     */
    public void onSaveToggleRequested() {
        AttractionDetailUiState state = uiState.getValue();
        Attraction attraction = state == null ? null : state.getAttraction();
        if (attraction == null || pendingToggleId != null) {
            return;
        }
        final String attractionId = attraction.getId();
        final boolean wasSaved = state.isSaved();
        pendingToggleId = attractionId;
        uiState.setValue(state.withSaved(wasSaved, true));

        RepositoryCallback<Boolean> callback = new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean changed) {
                pendingToggleId = null;
                // The observer callback already carries the new identifier set; this only clears
                // the in-flight flag, and republishes in case nothing about the set changed.
                republishSavedState();
            }

            @Override
            public void onError(Throwable error) {
                pendingToggleId = null;
                // Fall back to what the store last reported, so a failed write cannot leave the
                // bookmark claiming a state the database never reached.
                republishSavedState();
                saveFailed.setValue(true);
            }
        };
        if (wasSaved) {
            savedAttractions.unsave(attractionId, callback);
        } else {
            savedAttractions.save(attraction, callback);
        }
    }

    @Override
    public void onSavedIdsChanged(Set<String> ids) {
        savedIds = ids;
        republishSavedState();
    }

    private void republishSavedState() {
        AttractionDetailUiState state = uiState.getValue();
        if (state == null || state.getAttraction() == null) {
            return;
        }
        String attractionId = state.getAttraction().getId();
        uiState.setValue(state.withSaved(
                savedIds.contains(attractionId), attractionId.equals(pendingToggleId)));
    }
}
