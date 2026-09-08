package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room row for one saved attraction.
 *
 * <p>The attraction identifier is the primary key, which is the whole of the "one saved record per
 * attraction" rule: a repeated save cannot create a second row, whatever the UI does upstream.
 *
 * <p><b>No foreign key onto {@code attractions}.</b> That is the deliberate difference from
 * {@link VisitEntity}, which cascades from its session. The attraction cache is disposable — it is
 * refreshed, evicted, and will be replaced wholesale once a real content service is wired in — and
 * a cascade would turn any of those into a silent unsave. So the content is copied in instead, and
 * a saved place stays readable offline even when nothing in the cache matches it any more.
 */
@Entity(tableName = "saved_attractions")
public class SavedAttractionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "attraction_id")
    public String attractionId = "";

    @ColumnInfo(name = "name")
    public String name;

    /** Stable lower-case category code; see {@link com.comp90018.storystep.data.AttractionCategoryCodec}. */
    @ColumnInfo(name = "category")
    public String category;

    @ColumnInfo(name = "description")
    public String description;

    /**
     * Stable lower-case indoor code; see
     * {@link com.comp90018.storystep.data.IndoorClassificationCodec}. Text rather than the boolean
     * it was until schema 6, so that saving a place the provider never classified does not turn it
     * into a claim that it is outdoors.
     */
    @ColumnInfo(name = "indoor")
    public String indoor;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    /** When the user saved this place, in epoch milliseconds. Orders the saved list. */
    @ColumnInfo(name = "saved_at_millis")
    public long savedAtMillis;

    /**
     * Where the saved place came from; see
     * {@link com.comp90018.storystep.data.AttractionProvenanceCodec}.
     *
     * <p>Part of the snapshot because the snapshot is what the saved list renders. A licence that
     * requires the source to be credited still requires it after the user presses Save, and this
     * row is the only thing left to ask.
     */
    @ColumnInfo(name = "provenance")
    public String provenance;

    /** An optional reference into the source's wider data; see {@code AttractionEntity}. */
    @ColumnInfo(name = "source_ref")
    public String sourceRef;
}
