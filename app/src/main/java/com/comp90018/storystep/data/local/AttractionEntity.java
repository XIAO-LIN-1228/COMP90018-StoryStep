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
}
