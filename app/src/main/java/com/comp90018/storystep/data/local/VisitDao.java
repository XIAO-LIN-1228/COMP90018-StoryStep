package com.comp90018.storystep.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Synchronous data access for recorded visits.
 *
 * <p>{@code ABORT} on conflict rather than {@code IGNORE}: a second arrival at the same place in
 * the same session is a condition the store must notice and handle — it maps the constraint failure
 * to a duplicate result, which the caller treats as "already recorded" rather than as an error —
 * not one the storage layer should swallow silently.
 */
@Dao
public interface VisitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(VisitEntity visit);

    @Query("SELECT * FROM visits WHERE session_id = :sessionId ORDER BY arrival_wall_clock_millis ASC")
    List<VisitEntity> findBySession(String sessionId);

    @Query("SELECT COUNT(*) FROM visits WHERE session_id = :sessionId")
    int countBySession(String sessionId);

    /** Every place that has ever recorded a visit, across all sessions. */
    @Query("SELECT DISTINCT attraction_id FROM visits")
    List<String> visitedAttractionIds();

    /**
     * The session's oldest visit whose story has not been opened, or null when there is none.
     *
     * <p>Oldest first rather than newest: after an interruption the user should be offered the
     * story they were about to read, not the one that happened to be recorded last.
     */
    @Query("SELECT * FROM visits WHERE session_id = :sessionId AND unread != 0 "
            + "ORDER BY arrival_wall_clock_millis ASC LIMIT 1")
    VisitEntity findFirstUnread(String sessionId);

    @Query("SELECT * FROM visits WHERE session_id = :sessionId AND attraction_id = :attractionId")
    VisitEntity find(String sessionId, String attractionId);

    /** Marks one story read. Returns 0 for an unknown pair, which is not an error. */
    @Query("UPDATE visits SET unread = 0 "
            + "WHERE session_id = :sessionId AND attraction_id = :attractionId")
    int markRead(String sessionId, String attractionId);

    @Query("DELETE FROM visits WHERE session_id = :sessionId")
    int deleteBySession(String sessionId);
}
