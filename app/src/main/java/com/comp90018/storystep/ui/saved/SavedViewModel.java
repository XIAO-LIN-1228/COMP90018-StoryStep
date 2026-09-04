package com.comp90018.storystep.ui.saved;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.SavedAttraction;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;
import com.comp90018.storystep.ui.nearby.AttractionListItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Screen state for the saved list.
 *
 * <p>Reads only the saved store. It deliberately does not consult the attraction cache: a saved
 * place carries its own copy of its content, which is what lets the list stay complete offline and
 * survive the cache being refreshed or replaced.
 *
 * <p>Distances are measured from the reference area, like the nearby list before a session starts.
 * This screen starts no location updates.
 */
public class SavedViewModel extends ViewModel implements SavedAttractionRepository.Observer {

    private final SavedAttractionRepository savedAttractions;
    private final GeoPoint referenceOrigin;

    private final MutableLiveData<SavedUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveFailed = new MutableLiveData<>(false);

    /** Rows whose removal is still being written; a further tap on one of them is ignored. */
    private final Set<String> pendingRemovalIds = new HashSet<>();

    /** The identifiers currently on screen, so an observer callback can tell when to reload. */
    private Set<String> shownIds = Collections.emptySet();

    /** Suppresses the reload the very first observer callback would otherwise trigger. */
    private boolean loadStarted;

    public SavedViewModel(SavedAttractionRepository savedAttractions, GeoPoint referenceOrigin) {
        this.savedAttractions = savedAttractions;
        this.referenceOrigin = referenceOrigin;
        savedAttractions.addObserver(this);
    }

    @Override
    protected void onCleared() {
        savedAttractions.removeObserver(this);
        super.onCleared();
    }

    public LiveData<SavedUiState> getUiState() {
        return uiState;
    }

    /** True once a removal has failed; the screen consumes it and reports it once. */
    public LiveData<Boolean> getSaveFailed() {
        return saveFailed;
    }

    public void consumeSaveFailure() {
        if (Boolean.TRUE.equals(saveFailed.getValue())) {
            saveFailed.setValue(false);
        }
    }

    /** Loads the saved list. Safe to call again for a retry. */
    public void load() {
        loadStarted = true;
        uiState.setValue(SavedUiState.loading());
        savedAttractions.loadSaved(new RepositoryCallback<List<SavedAttraction>>() {
            @Override
            public void onSuccess(List<SavedAttraction> saved) {
                publish(saved);
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(SavedUiState.error());
            }
        });
    }

    /** Loads only if nothing has been published yet, so a rotation does not refetch. */
    public void loadIfNeeded() {
        if (uiState.getValue() == null) {
            load();
        }
    }

    /**
     * The user pressed a row's bookmark. On this screen every row is saved, so this always removes.
     *
     * <p>The row disappears when the reload triggered by the store's notification arrives, not
     * optimistically: a removal that failed must leave the place where it was.
     */
    public void onSaveToggleRequested(AttractionListItem item) {
        if (item == null || pendingRemovalIds.contains(item.getId())) {
            return;
        }
        final String attractionId = item.getId();
        pendingRemovalIds.add(attractionId);
        savedAttractions.unsave(attractionId, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean removed) {
                pendingRemovalIds.remove(attractionId);
            }

            @Override
            public void onError(Throwable error) {
                pendingRemovalIds.remove(attractionId);
                saveFailed.setValue(true);
            }
        });
    }

    @Override
    public void onSavedIdsChanged(Set<String> ids) {
        if (!loadStarted || ids.equals(shownIds)) {
            return;
        }
        // Something changed the saved set — this screen, or the detail screen behind it. The
        // identifiers alone are not enough to draw a row, so the content is fetched again.
        load();
    }

    private void publish(List<SavedAttraction> saved) {
        List<AttractionListItem> items = new ArrayList<>(saved.size());
        Set<String> ids = new LinkedHashSet<>();
        for (SavedAttraction record : saved) {
            Attraction attraction = record.getAttraction();
            ids.add(attraction.getId());
            items.add(new AttractionListItem(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getCategory(),
                    GeoCalculator.distanceMeters(referenceOrigin, attraction.getLocation()),
                    true));
        }
        shownIds = ids;
        uiState.setValue(SavedUiState.content(items));
    }
}
