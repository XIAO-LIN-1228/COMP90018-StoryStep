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

    /**
     * Removes every row that came from a provider, leaving the bundled set untouched.
     *
     * <p>Bounded twice over, and both bounds matter.
     *
     * <p>By provenance, because the seed records are the only content guaranteed to exist offline
     * and a place the user saved is still resolved through this table by the detail screen — a
     * refresh that cleared everything would take working content away to make room for new
     * content.
     *
     * <p>By position, because a refresh covers one area and the cache holds several. Deleting
     * every provider row would empty the areas the user walked through earlier while their
     * {@code attraction_areas} entries still claimed to be fetched, so those areas would answer
     * from the seed set alone and call it fresh. The caller passes a box lying entirely inside the
     * circle it just requested, so everything removed here is certainly re-inserted if it still
     * exists.
     */
    @Query("DELETE FROM attractions WHERE provenance != :seedProvenance "
            + "AND latitude BETWEEN :minLatitude AND :maxLatitude "
            + "AND longitude BETWEEN :minLongitude AND :maxLongitude")
    int deleteProviderRowsWithin(String seedProvenance,
                                 double minLatitude,
                                 double maxLatitude,
                                 double minLongitude,
                                 double maxLongitude);

    /**
     * Removes fetched records older than {@code cutoffMillis} (D-06 retention).
     *
     * <p>The seed set is excluded for the same reason a refresh excludes it: it is shipped
     * content rather than a copy of a provider response, and removing it would leave the app with
     * nothing to show offline in an area it has never fetched.
     *
     * <p>A place the user saved keeps its own row in {@code saved_attractions}, which this does
     * not touch, so ageing out a cached record never removes a place the user kept.
     *
     * @return how many fetched records were removed
     */
    @Query("DELETE FROM attractions WHERE provenance != :seedProvenance "
            + "AND fetched_at_millis < :cutoffMillis")
    int deleteFetchedBefore(String seedProvenance, long cutoffMillis);

    @Query("SELECT COUNT(*) FROM attractions")
    int count();

    @Query("DELETE FROM attractions")
    void deleteAll();
}
