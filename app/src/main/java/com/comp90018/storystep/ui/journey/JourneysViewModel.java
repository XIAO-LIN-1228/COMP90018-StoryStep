package com.comp90018.storystep.ui.journey;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.model.Journey;
import com.comp90018.storystep.domain.repository.JourneyRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.List;

/** Loads journey history through the repository boundary. */
public final class JourneysViewModel extends ViewModel {

    private final JourneyRepository repository;
    private final MutableLiveData<JourneysUiState> uiState = new MutableLiveData<>();

    public JourneysViewModel(JourneyRepository repository) {
        this.repository = repository;
    }

    public LiveData<JourneysUiState> getUiState() { return uiState; }

    public void loadIfNeeded() {
        if (uiState.getValue() == null) { load(); }
    }

    public void load() {
        uiState.setValue(JourneysUiState.loading());
        repository.loadJourneys(new RepositoryCallback<List<Journey>>() {
            @Override public void onSuccess(List<Journey> data) {
                uiState.setValue(JourneysUiState.content(data));
            }

            @Override public void onError(Throwable error) {
                uiState.setValue(JourneysUiState.error());
            }
        });
    }
}
