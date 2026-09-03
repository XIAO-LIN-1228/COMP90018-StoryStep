package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room row for a cached attraction. This is a storage type only: the rest of the application
 * works with {@link com.comp90018.storystep.domain.model.Attraction} via
 * {@link LocalAttractionMapper}, so a schema change stays inside the data layer.
 */
@Entity(tableName = "attractions")
public class AttractionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    @ColumnInfo(name = "name")
    public String name;

    /** Stable lower-case category code; see {@link com.comp90018.storystep.data.AttractionCategoryCodec}. */
    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "description")
    public String description;

    @ColumnInfo(name = "indoor")
    public boolean indoor;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    /**
     * Where this row came from — {@link #PROVENANCE_SEED} for the bundled demonstration set, and
     * later a provider identifier for fetched content.
     *
     * <p>Recorded because a seeded place and a place returned by a real service are not
     * interchangeable evidence, and a screen that cannot tell them apart will eventually present
     * one as the other.
     */
    @ColumnInfo(name = "provenance")
    public String provenance;

    /** When this row was written, in epoch milliseconds. Zero for rows predating the column. */
    @ColumnInfo(name = "fetched_at_millis")
    public long fetchedAtMillis;

    /** The bundled demonstration data set; not fetched from any service. */
    public static final String PROVENANCE_SEED = "seed";
}
