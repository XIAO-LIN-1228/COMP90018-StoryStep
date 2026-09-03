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

    /**
     * Inserts only the rows whose ids are not already stored.
     *
     * <p>This is what makes seeding idempotent, and it is deliberately not
     * {@link #upsertAll(List)}: a later fetched row carries real provenance and a real fetch time,
     * and replacing it with the bundled copy on every launch would quietly downgrade live content
     * back to demonstration data.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertMissing(List<AttractionEntity> attractions);

    @Query("SELECT COUNT(*) FROM attractions")
    int count();

    @Query("DELETE FROM attractions")
    void deleteAll();
}
