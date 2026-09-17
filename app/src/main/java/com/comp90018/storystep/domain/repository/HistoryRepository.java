package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;

import java.util.List;

/**
 * The user's own record of where they have explored.
 *
 * <p>Separate from {@link SessionStore} even though both read the same tables. {@code SessionStore}
 * is the live session's write path, and every invariant it documents — at most one unfinished
 * session, append-only routes, exactly-once visits — is about a walk that is happening now. This is
 * a read-and-delete path with entirely different callers, and putting it on the same contract would
 * mean the coordinator carried methods for deleting the history it is busy creating.
 *
 * <p>Everything here is user-owned data. Deletion is therefore real deletion, and its scope is
 * exactly what the caller asked for: a session owns its route points and its visits and nothing
 * else. Saved attractions are the user's separately and survive every operation on this contract.
 *
 * <p>Implementations follow the {@link RepositoryCallback} threading contract: work off the main
 * thread, results back on it.
 */
public interface HistoryRepository {

    /**
     * Notified after history actually changed on disk.
     *
     * <p>Parameterless because there is no small shared value worth caching — a listener's job is
     * to re-read. It exists so that clearing history from the privacy screen leaves the history
     * list correct without the user having to know to refresh it.
     *
     * <p>Delivered on the main thread.
     */
    interface Observer {
        void onHistoryChanged();
    }

    /**
     * Every finished session, most recently ended first.
     *
     * <p>Unfinished sessions are deliberately absent: a walk that is still happening is not
     * history, and the exploration screen already shows it.
     */
    void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> callback);

    /**
     * One finished session with its visits, its segmented route, and the movement and weather
     * recorded while it ran.
     *
     * <p>Fails with {@link SessionNotFoundException} for an unknown id and for a session that has
     * not finished. Both are the same thing from here: not something history can show.
     */
    void loadDetail(String sessionId, RepositoryCallback<SessionHistoryDetail> callback);

    /**
     * Removes one finished session together with the route points and visits it owns.
     *
     * <p>Idempotent: deleting a session that is already gone succeeds with {@code false} rather
     * than failing, which is what makes a double tap and a stale id harmless.
     *
     * <p>Fails with {@link ActiveSessionNotDeletableException} for a session still in progress.
     *
     * @param callback receives true when a session was removed, false when there was none
     */
    void delete(String sessionId, RepositoryCallback<Boolean> callback);

    /**
     * Removes every finished session.
     *
     * <p>Finished only. A session in progress, or one restored as paused, is not history, and a
     * clear-history control that silently threw away the walk the user is on would be destroying
     * data they never asked to lose. That restriction is the operation's scope and must be stated
     * to the user before they confirm it.
     *
     * @param callback receives how many sessions were removed
     */
    void clearAll(RepositoryCallback<Integer> callback);

    /**
     * The ids of every place that has ever recorded a visit, across all sessions.
     *
     * <p>What the nearby list's "Unvisited" filter and its unlock count read. A default of
     * "none" keeps in-memory fakes honest without forcing them to model visits.
     */
    default void loadVisitedAttractionIds(RepositoryCallback<java.util.Set<String>> callback) {
        callback.onSuccess(java.util.Collections.<String>emptySet());
    }

    void addObserver(Observer observer);

    void removeObserver(Observer observer);
}
