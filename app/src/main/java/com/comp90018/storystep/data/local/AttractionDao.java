package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Synchronous data access for cached attractions. Callers must invoke these methods off the
 * main thread; the repository implementation owns that scheduling.
 */
@Dao
public interface AttractionDao {

    @Query("SELECT * FROM attractions ORDER BY name ASC")
    List<AttractionEntity> findAll();

    @Nullable
    @Query("SELECT * FROM attractions WHERE id = :attractionId LIMIT 1")
    AttractionEntity findById(String attractionId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<AttractionEntity> attractions);

    @Query("DELETE FROM attractions")
    void deleteAll();
}
