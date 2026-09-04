package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * Synchronous data access for saved attractions. Callers must invoke these methods off the main
 * thread; the repository implementation owns that scheduling.
 */
@Dao
public interface SavedAttractionDao {

    @Query("SELECT * FROM saved_attractions ORDER BY saved_at_millis DESC, attraction_id ASC")
    List<SavedAttractionEntity> findAllByRecency();

    @Query("SELECT attraction_id FROM saved_attractions")
    List<String> findAllIds();

    @Nullable
    @Query("SELECT * FROM saved_attractions WHERE attraction_id = :attractionId LIMIT 1")
    SavedAttractionEntity findById(String attractionId);

    /**
     * Inserts unless the place is already saved.
     *
     * <p>{@code IGNORE} rather than the {@code ABORT} {@link VisitDao} uses: a second arrival in
     * one session is a condition the store has to notice, but a second tap on a bookmark is just
     * the user asking for the state it is already in. Ignoring also protects the original
     * {@code saved_at_millis}, so re-saving does not reshuffle the list.
     *
     * @return the inserted row id, or -1 when a record already existed
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIgnoring(SavedAttractionEntity saved);

    /** @return the number of rows removed: 1, or 0 when the place was not saved */
    @Query("DELETE FROM saved_attractions WHERE attraction_id = :attractionId")
    int deleteById(String attractionId);

    /** @return how many saved places were removed */
    @Query("DELETE FROM saved_attractions")
    int deleteAll();

    @Query("SELECT COUNT(*) FROM saved_attractions")
    int count();
}
