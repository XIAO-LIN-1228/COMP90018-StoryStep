package com.comp90018.storystep.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Synchronous data access for stored route points.
 *
 * <p>Inserts ignore conflicts on {@code (session_id, sequence)}, which is what allows a caller to
 * re-send points it never had confirmed without risking a duplicated trace.
 */
@Dao
public interface RoutePointDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAll(List<RoutePointEntity> points);

    @Query("SELECT * FROM route_points WHERE session_id = :sessionId ORDER BY sequence ASC")
    List<RoutePointEntity> findBySession(String sessionId);

    @Query("SELECT COUNT(*) FROM route_points WHERE session_id = :sessionId")
    int countBySession(String sessionId);

    @Query("DELETE FROM route_points WHERE session_id = :sessionId")
    int deleteBySession(String sessionId);
}
