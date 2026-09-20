package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.SavedAttraction;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A saved store whose writes never answer until a test says so.
 *
 * <p>Real storage answers whenever it answers, and the interesting window for a double tap is
 * exactly the one where it has not yet. The direct executors used elsewhere close that window
 * completely, so serialisation can only be tested against a store that holds its callbacks open.
 */
public final class DeferredSavedAttractions implements SavedAttractionRepository {

    /** Writes issued but not yet answered, oldest first. */
    public final List<Runnable> pending = new ArrayList<>();

    private final List<Observer> observers = new ArrayList<>(2);
    private final Set<String> savedIds = new LinkedHashSet<>();

    @Override
    public void loadSaved(RepositoryCallback<List<SavedAttraction>> callback) {
        callback.onSuccess(Collections.<SavedAttraction>emptyList());
    }

    @Override
    public void loadSavedIds(RepositoryCallback<Set<String>> callback) {
        callback.onSuccess(new LinkedHashSet<>(savedIds));
    }

    @Override
    public void loadSavedById(String attractionId, RepositoryCallback<SavedAttraction> callback) {
        // This fake only tracks identifiers, not stored content, so it can say whether a place is
        // saved but has no snapshot to hand back. The tests that need one use TestSavedAttractions.
        callback.onSuccess(null);
    }

    @Override
    public void save(Attraction attraction, RepositoryCallback<Boolean> callback) {
        pending.add(() -> {
            boolean added = savedIds.add(attraction.getId());
            notifyObservers();
            callback.onSuccess(added);
        });
    }

    @Override
    public void unsave(String attractionId, RepositoryCallback<Boolean> callback) {
        pending.add(() -> {
            boolean removed = savedIds.remove(attractionId);
            notifyObservers();
            callback.onSuccess(removed);
        });
    }

    @Override
    public void clearAll(RepositoryCallback<Integer> callback) {
        pending.add(() -> {
            int removed = savedIds.size();
            savedIds.clear();
            notifyObservers();
            callback.onSuccess(removed);
        });
    }

    /** Answers the oldest outstanding write. */
    public void completeNext() {
        if (pending.isEmpty()) {
            throw new IllegalStateException("no write is outstanding");
        }
        pending.remove(0).run();
    }

    @Override
    public void addObserver(Observer observer) {
        observers.add(observer);
        observer.onSavedIdsChanged(Collections.unmodifiableSet(new LinkedHashSet<>(savedIds)));
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    private void notifyObservers() {
        Set<String> snapshot = Collections.unmodifiableSet(new LinkedHashSet<>(savedIds));
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onSavedIdsChanged(snapshot);
        }
    }
}
