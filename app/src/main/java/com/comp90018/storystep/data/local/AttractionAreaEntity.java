package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Records that one grid cell has been fetched, and when.
 *
 * <p>This table exists to answer a question the {@code attractions} table cannot: the difference
 * between an area that has never been fetched and an area that was fetched and genuinely contains
 * no attractions. Both look like zero rows. Without this distinction the app would re-request an
 * empty area on every query, and — worse — could not tell a legitimately empty answer from a
 * provider failure, which is exactly what AT-18B requires it to distinguish.
 *
 * <p>It is also where cache freshness is decided. Freshness is a property of the area, not of the
 * individual places in it: a museum row that has not changed in a year is not stale, but an area
 * last searched a year ago may well be missing places that opened since.
 */
@Entity(tableName = "attraction_areas")
public class AttractionAreaEntity {

    /**
     * The grid-cell key; see
     * {@link com.comp90018.storystep.domain.attraction.AttractionAreaBucket#key()}.
     */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "area_key")
    public String areaKey = "";

    /** When this area was last fetched successfully, in epoch milliseconds. */
    @ColumnInfo(name = "fetched_at_millis")
    public long fetchedAtMillis;

    /**
     * How many usable records the fetch produced.
     *
     * <p>Zero is a meaningful value, and the reason it is stored rather than derived: it records
     * that the provider was asked and had nothing, which is a real answer worth keeping.
     */
    @ColumnInfo(name = "record_count")
    public int recordCount;
}
