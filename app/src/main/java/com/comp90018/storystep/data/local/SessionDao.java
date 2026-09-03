package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

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
}
