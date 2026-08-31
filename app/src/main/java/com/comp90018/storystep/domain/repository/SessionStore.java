package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.SessionSummary;

/**
 * Durable record of exploration sessions.
 *
 * <p>Asynchronous like {@link AttractionRepository}, and for the same reason: the implementation
 * that matters is the database-backed one, and a contract shaped around a synchronous in-memory
 * store would have to be redesigned when it arrives. The states {@code STARTING} and
 * {@code STOPPING} exist precisely to cover the time these calls take.
 *
 * <p><b>At most one unfinished session may exist.</b> Implementations reject a create while
 * another session is unfinished rather than silently allowing two, because a second one would make
 * every later "the session" query ambiguous.
 *
 * <p>Callbacks are delivered on the main thread, matching {@code RepositoryCallback}.
 */
public interface SessionStore {

    /**
     * Records the start of a session and assigns its id.
     *
     * <p>Fails when an unfinished session already exists. A failure here must leave the caller in
     * a state with no session at all, never a session the user believes is recording.
     */
    void create(RepositoryCallback<SessionSummary> callback);

    /**
     * Saves progress without ending the session, so an interruption does not discard everything.
     *
     * <p>Called on transitions and at a bounded interval — not on every location fix.
     */
    void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback);

    /**
     * Ends the session permanently. Idempotent: finishing an already-finished session succeeds
     * without creating a second record.
     */
    void finish(SessionSummary summary, RepositoryCallback<Void> callback);

    /**
     * Removes an unfinished session without adding it to journey history.
     *
     * <p>Idempotent for an already-removed id. Implementations must not silently discard a
     * different active session.
     */
    void discard(String sessionId, RepositoryCallback<Void> callback);

    /**
     * The unfinished session left by a previous run, if any.
     *
     * <p>Reports success with {@code null} when there is none. A restored session is never resumed
     * automatically; it is presented as paused so the user chooses to continue or finish it.
     */
    void findUnfinished(RepositoryCallback<SessionSummary> callback);
}
