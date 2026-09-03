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
 * the same session is a condition REQ-13 must notice and handle, not one the storage layer should
 * swallow silently.
 */
@Dao
public interface VisitDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(VisitEntity visit);

    @Query("SELECT * FROM visits WHERE session_id = :sessionId ORDER BY arrival_wall_clock_millis ASC")
    List<VisitEntity> findBySession(String sessionId);

    @Query("SELECT COUNT(*) FROM visits WHERE session_id = :sessionId")
    int countBySession(String sessionId);

    @Query("DELETE FROM visits WHERE session_id = :sessionId")
    int deleteBySession(String sessionId);
}
