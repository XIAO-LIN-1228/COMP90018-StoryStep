package com.comp90018.storystep.data.local;

import androidx.room.*;
import java.util.List;

@Dao
public interface ThemeDao {
    @Insert void insertRun(ThemeRunEntity run);
    @Insert void insertStops(List<ThemeStopEntity> stops);
    @Query("SELECT * FROM theme_runs WHERE session_id = :sessionId") ThemeRunEntity findRun(String sessionId);
    @Query("SELECT * FROM theme_stops WHERE session_id = :sessionId") List<ThemeStopEntity> findStops(String sessionId);
    @Query("UPDATE theme_stops SET skipped = :skipped WHERE session_id = :sessionId AND stop_id = :stopId")
    int setSkipped(String sessionId, String stopId, boolean skipped);
    @Query("UPDATE theme_stops SET answer = :answer WHERE session_id = :sessionId AND stop_id = :stopId")
    int answer(String sessionId, String stopId, int answer);
}
