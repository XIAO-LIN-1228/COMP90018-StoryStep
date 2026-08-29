package com.comp90018.storystep.ui.detail;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

/** Screen state for a single attraction, loaded by identifier through the repository. */
public class AttractionDetailViewModel extends ViewModel {

    private final AttractionRepository repository;
    private final MutableLiveData<AttractionDetailUiState> uiState = new MutableLiveData<>();

    public AttractionDetailViewModel(AttractionRepository repository) {
        this.repository = repository;
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

    /** Loads only if nothing has been published yet, so a rotation does not refetch. */
    public void loadIfNeeded(String attractionId) {
        if (uiState.getValue() == null) {
            load(attractionId);
        }
    }
}
