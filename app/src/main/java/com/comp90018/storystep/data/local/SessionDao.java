package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Synchronous data access for sessions. Callers must invoke these methods off the main thread;
 * {@link com.comp90018.storystep.data.repository.RoomSessionStore} owns that scheduling.
 */
@Dao
public interface SessionDao {

    @Insert
    void insert(SessionEntity session);

    @Update
    void update(SessionEntity session);

    @Nullable
    @Query("SELECT * FROM sessions WHERE id = :sessionId LIMIT 1")
    SessionEntity findById(String sessionId);

    /**
     * The one session still in progress, if any.
     *
     * <p>Ordered and limited rather than assumed unique so that a database somehow holding two
     * unfinished rows returns the most recent one instead of throwing. The invariant is enforced
     * on write; this query stays readable either way.
     */
    @Nullable
    @Query("SELECT * FROM sessions WHERE finished = 0 ORDER BY start_wall_clock_millis DESC LIMIT 1")
    SessionEntity findUnfinished();

    @Query("SELECT COUNT(*) FROM sessions WHERE finished = 0")
    int countUnfinished();

    /** Removes the session; its route points and visits cascade with it. */
    @Query("DELETE FROM sessions WHERE id = :sessionId")
    int deleteById(String sessionId);

    /**
     * Every finished session, most recently ended first, with the number of places it visited.
     *
     * <p>The visit count is a correlated subquery rather than a join with a group-by: a session
     * with no visits still has to appear, and this shape rides {@code index_visits_session_id}
     * without changing the row count.
     *
     * <p>The ordering is a total order. Two sessions can end in the same millisecond — synthetic
     * test data reliably does — and a partial order would leave their relative position up to
     * SQLite, which is not something a test or a user should have to guess at.
     */
    @Query("SELECT s.id AS id, "
            + "s.start_wall_clock_millis AS startWallClockMillis, "
            + "s.end_wall_clock_millis AS endWallClockMillis, "
            + "s.active_duration_millis AS activeDurationMillis, "
            + "s.distance_meters AS distanceMeters, "
            + "s.segment_count AS segmentCount, "
            + "s.interrupted AS interrupted, "
            + "s.destination_name AS destinationName, "
            + "s.title AS title, "
            + "s.place_name AS placeName, "
            + "s.photo_files AS photoFiles, "
            + "s.steps AS steps, "
            + "s.ascent_meters AS ascentMeters, "
            + "(SELECT COUNT(*) FROM visits v WHERE v.session_id = s.id) AS visitCount "
            + "FROM sessions s WHERE s.finished != 0 "
            + "ORDER BY s.end_wall_clock_millis DESC, s.start_wall_clock_millis DESC, s.id ASC")
    List<SessionHistoryRow> findFinished();

    @Query("SELECT COUNT(*) FROM sessions WHERE finished != 0")
    int countFinished();

    /**
     * Writes the published record onto a finished session. Finished only: a walk still in
     * progress has nothing to publish yet, and the row count says whether anything was written.
     */
    /** Writes what the walk's sensors counted; nulls mean the device could not count. */
    @Query("UPDATE sessions SET steps = :steps, ascent_meters = :ascentMeters WHERE id = :sessionId")
    int saveWalkTotals(String sessionId, Long steps, Double ascentMeters);

    @Query("UPDATE sessions SET title = :title, place_name = :placeName, photo_files = :photoFiles "
            + "WHERE id = :sessionId AND finished != 0")
    int publish(String sessionId, String title, String placeName, String photoFiles);

    /**
     * Removes every finished session; their route points and visits cascade with them.
     *
     * <p>Deliberately restricted to finished rows. A session in progress, or one restored as
     * paused, is not history, and clearing history must not silently discard a walk the user is
     * still on.
     */
    @Query("DELETE FROM sessions WHERE finished != 0")
    int deleteAllFinished();
}
