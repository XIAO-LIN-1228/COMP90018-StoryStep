package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Synchronous data access for the weather cache. Callers must invoke these off the main thread;
 * the repository owns that scheduling.
 */
@Dao
public interface WeatherCacheDao {

    @Nullable
    @Query("SELECT * FROM weather_cache WHERE area_key = :areaKey LIMIT 1")
    WeatherCacheEntity findByArea(String areaKey);

    /**
     * Stores the observation for its area, replacing any previous one.
     *
     * <p>{@code REPLACE} rather than the {@code IGNORE} used for saved attractions: a newer
     * observation for the same cell is strictly better than the one it overwrites, and there is no
     * user decision recorded in the old row to protect.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(WeatherCacheEntity weather);

    /** @return how many cached observations were removed */
    @Query("DELETE FROM weather_cache")
    int deleteAll();

    @Query("SELECT COUNT(*) FROM weather_cache")
    int count();
}
