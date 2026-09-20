package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.SavedAttraction;

import java.util.List;
import java.util.Set;

/**
 * The saved-attraction contract. Deliberately separate from {@link AttractionRepository}: saving
 * is user-owned state with its own lifetime, and folding it into the attraction cache would tie a
 * user's decision to whichever content happens to be cached.
 *
 * <p>Implementations follow the threading contract of {@link RepositoryCallback} — work off the
 * main thread, results back on it.
 *
 * <p>Saving is idempotent in both directions. Saving a place that is already saved, or removing
 * one that is not, is a no-op that reports {@code false} rather than an error: the user's intent
 * has been honoured either way, and the screens exist to reflect state, not to argue about it.
 */
public interface SavedAttractionRepository {

    /**
     * Notified whenever the set of saved identifiers changes.
     *
     * <p>This is what keeps the detail screen, the nearby list and the saved list agreeing without
     * any of them knowing the others exist. Callbacks arrive on the main thread.
     */
    interface Observer {

        void onSavedIdsChanged(Set<String> savedIds);
    }

    /** Every saved place, most recently saved first. */
    void loadSaved(RepositoryCallback<List<SavedAttraction>> callback);

    /**
     * Just the identifiers.
     *
     * <p>Kept separate from {@link #loadSaved} because contextual ranking (REQ-21) needs to know
     * which candidates are saved without loading their stored content.
     */
    void loadSavedIds(RepositoryCallback<Set<String>> callback);

    /**
     * The stored copy of one place, or null when it is not saved.
     *
     * <p>This is the offline reading of a saved place. The attraction cache can legitimately lose
     * a record — a refresh replaces an area, or the device has never fetched it — and the saved
     * snapshot is the only remaining source for a place the user explicitly kept. Not being saved
     * is an answer, not a failure, so it arrives as a null success rather than an error.
     *
     * @param callback receives the stored copy, or null when nothing is saved under this id
     */
    void loadSavedById(String attractionId, RepositoryCallback<SavedAttraction> callback);

    /**
     * Stores a copy of {@code attraction}.
     *
     * @param callback receives true when a new record was written, false when it was already saved
     */
    void save(Attraction attraction, RepositoryCallback<Boolean> callback);

    /**
     * Removes the saved record for {@code attractionId}.
     *
     * @param callback receives true when a record was removed, false when there was none
     */
    void unsave(String attractionId, RepositoryCallback<Boolean> callback);

    /**
     * Removes every saved record.
     *
     * @param callback receives how many records were removed
     */
    void clearAll(RepositoryCallback<Integer> callback);

    /** Registers {@code observer} and immediately gives it the currently known identifiers. */
    void addObserver(Observer observer);

    void removeObserver(Observer observer);
}
