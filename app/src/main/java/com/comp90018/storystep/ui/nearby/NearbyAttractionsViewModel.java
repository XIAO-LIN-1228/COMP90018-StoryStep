package com.comp90018.storystep.ui.nearby;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen state for the nearby list. Distance comes from {@link GeoCalculator} and the data comes
 * from an {@link AttractionRepository}; the Fragment only renders what this class publishes.
 */
public class NearbyAttractionsViewModel extends ViewModel {

    private final AttractionRepository repository;
    private final GeoPoint origin;
    private final double radiusMeters;
    private final MutableLiveData<NearbyUiState> uiState = new MutableLiveData<>();

    public NearbyAttractionsViewModel(AttractionRepository repository,
                                      GeoPoint origin,
                                      double radiusMeters) {
        this.repository = repository;
        this.origin = origin;
        this.radiusMeters = radiusMeters;
    }

    public LiveData<NearbyUiState> getUiState() {
        return uiState;
    }

    /** Loads the list. Safe to call again for a retry. */
    public void load() {
        uiState.setValue(NearbyUiState.loading());
        repository.loadNearbyAttractions(origin, radiusMeters,
                new RepositoryCallback<List<Attraction>>() {
                    @Override
                    public void onSuccess(List<Attraction> attractions) {
                        uiState.setValue(NearbyUiState.content(toItems(attractions)));
                    }

                    @Override
                    public void onError(Throwable error) {
                        uiState.setValue(NearbyUiState.error());
                    }
                });
    }

    /** Loads only if nothing has been published yet, so a rotation does not refetch. */
    public void loadIfNeeded() {
        if (uiState.getValue() == null) {
            load();
        }
    }

    private List<AttractionListItem> toItems(List<Attraction> attractions) {
        List<AttractionListItem> items = new ArrayList<>(attractions.size());
        for (Attraction attraction : attractions) {
            items.add(new AttractionListItem(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getCategory(),
                    GeoCalculator.distanceMeters(origin, attraction.getLocation())));
        }
        return items;
    }
}
