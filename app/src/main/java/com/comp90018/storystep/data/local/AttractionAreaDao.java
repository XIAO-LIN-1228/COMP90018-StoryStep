package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

/**
 * Synchronous data access for fetched-area bookkeeping. Callers must invoke these methods off the
 * main thread; the repository implementation owns that scheduling.
 */
@Dao
public interface AttractionAreaDao {

    @Nullable
    @Query("SELECT * FROM attraction_areas WHERE area_key = :areaKey LIMIT 1")
    AttractionAreaEntity findByKey(String areaKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(AttractionAreaEntity area);

    /**
     * Removes bookkeeping for areas last fetched before {@code cutoffMillis} (D-06 retention).
     *
     * <p>Removed together with the records they account for. An area row that outlived its
     * records would claim the area had been fetched and hand the user a legitimately-empty
     * verdict for a place that simply aged out.
     *
     * @return how many area records were removed
     */
    @Query("DELETE FROM attraction_areas WHERE fetched_at_millis < :cutoffMillis")
    int deleteFetchedBefore(long cutoffMillis);

    @Query("DELETE FROM attraction_areas")
    int deleteAll();

    @Query("SELECT COUNT(*) FROM attraction_areas")
    int count();
}
