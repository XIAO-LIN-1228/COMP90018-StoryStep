package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.SessionSummary;

import java.util.List;

/**
 * Durable record of exploration sessions, their routes and the places they were heading towards.
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
 * <p>The route is written separately from the summary, and incrementally. A checkpoint that
 * rewrote the whole trace would grow more expensive the longer the user explored, which is exactly
 * backwards: a long session is the one that most needs cheap, frequent checkpoints.
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
     * Appends route points recorded since the last successful append, and updates the session's
     * accumulated distance.
     *
     * <p>Append-only by contract: a point already written is never rewritten or reordered, so a
     * retry after a failed append cannot duplicate the trace as long as the caller re-sends only
     * what it has not yet had confirmed.
     *
     * @param sessionId           the session the points belong to
     * @param firstSequence       the position of {@code newPoints[0]} in the session's trace; the
     *                            caller supplies it because only the caller knows which points it
     *                            has had confirmed, and a retry must re-use the same numbers
     * @param newPoints           points not previously accepted, oldest first; may be empty
     * @param totalDistanceMeters the session's accumulated distance at this checkpoint
     * @param segmentCount        how many collection spans have contributed points
     */
    void appendRoute(String sessionId,
                     int firstSequence,
                     List<RoutePoint> newPoints,
                     double totalDistanceMeters,
                     int segmentCount,
                     RepositoryCallback<Void> callback);

    /**
     * Stores a snapshot of the attraction the user is heading towards, replacing any previous one.
     *
     * <p>A snapshot rather than an id alone: the attraction cache may be refreshed or evicted, and
     * a restored session that could no longer name its own destination would be worse than useless.
     *
     * @param destination the chosen attraction, or null to record that none is selected
     */
    void saveDestination(String sessionId,
                         Attraction destination,
                         RepositoryCallback<Void> callback);

    /**
     * Ends the session permanently. Idempotent: finishing an already-finished session succeeds
     * without creating a second record.
     */
    void finish(SessionSummary summary, RepositoryCallback<Void> callback);

    /**
     * Removes an unfinished session without adding it to journey history.
     *
     * <p>Removes the session's own route points and visits with it, and nothing else. Independently
     * saved attractions belong to the user, not to the session, and must survive.
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
    void findUnfinished(RepositoryCallback<RestoredSession> callback);
}
