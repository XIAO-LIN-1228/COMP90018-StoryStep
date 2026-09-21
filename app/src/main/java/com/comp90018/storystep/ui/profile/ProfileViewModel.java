package com.comp90018.storystep.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;
import java.util.List;
import java.util.Set;

/** Finished walks, distinct visited places and saved places. Unknown counts are never zero. */
public final class ProfileViewModel extends ViewModel {
    private final HistoryRepository history;
    private final SavedAttractionRepository saved;
    private final MutableLiveData<Integer> journeys = new MutableLiveData<>();
    private final MutableLiveData<Integer> visits = new MutableLiveData<>();
    private final MutableLiveData<Integer> saves = new MutableLiveData<>();
    /** Metres walked across every finished walk; null until known. */
    private final MutableLiveData<Double> distanceMeters = new MutableLiveData<>();
    /** When the first finished walk started, or null when there is none. */
    private final MutableLiveData<Long> firstWalkMillis = new MutableLiveData<>();
    private final HistoryRepository.Observer historyObserver = this::refresh;
    private final SavedAttractionRepository.Observer savedObserver = ids -> refresh();
    private int generation;

    public ProfileViewModel(HistoryRepository history, SavedAttractionRepository saved) {
        this.history = history;
        this.saved = saved;
        history.addObserver(historyObserver);
        saved.addObserver(savedObserver);
    }

    public LiveData<Integer> getJourneys() { return journeys; }
    public LiveData<Integer> getVisits() { return visits; }
    public LiveData<Integer> getSaves() { return saves; }
    public LiveData<Double> getDistanceMeters() { return distanceMeters; }
    public LiveData<Long> getFirstWalkMillis() { return firstWalkMillis; }

    public void refresh() {
        final int request = ++generation;
        history.loadHistory(new RepositoryCallback<List<SessionHistoryEntry>>() {
            @Override public void onSuccess(List<SessionHistoryEntry> rows) {
                if (request != generation) { return; }
                journeys.setValue(rows.size());
                double metres = 0;
                Long first = null;
                for (SessionHistoryEntry row : rows) {
                    metres += row.getDistanceMeters();
                    if (first == null || row.getStartWallClockMillis() < first) {
                        first = row.getStartWallClockMillis();
                    }
                }
                distanceMeters.setValue(metres);
                firstWalkMillis.setValue(first);
            }
            @Override public void onError(Throwable error) {
                if (request != generation) { return; }
                journeys.setValue(null);
                distanceMeters.setValue(null);
                firstWalkMillis.setValue(null);
            }
        });
        history.loadVisitedAttractionIds(new RepositoryCallback<Set<String>>() {
            @Override public void onSuccess(Set<String> ids) {
                if (request == generation) { visits.setValue(ids.size()); }
            }
            @Override public void onError(Throwable error) {
                if (request == generation) { visits.setValue(null); }
            }
        });
        saved.loadSavedIds(new RepositoryCallback<Set<String>>() {
            @Override public void onSuccess(Set<String> ids) {
                if (request == generation) { saves.setValue(ids.size()); }
            }
            @Override public void onError(Throwable error) {
                if (request == generation) { saves.setValue(null); }
            }
        });
    }

    @Override protected void onCleared() {
        ++generation;
        history.removeObserver(historyObserver);
        saved.removeObserver(savedObserver);
        super.onCleared();
    }
}
