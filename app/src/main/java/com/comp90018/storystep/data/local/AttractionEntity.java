package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.comp90018.storystep.data.AttractionProvenanceCodec;

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

    /**
     * Stable lower-case indoor code; see
     * {@link com.comp90018.storystep.data.IndoorClassificationCodec}.
     *
     * <p>Text rather than the boolean it was until schema 6. A real provider frequently says
     * nothing about whether a place is under cover, and a boolean has no way to record that: every
     * unclassified record would have been stored as "outdoors", which weather-aware ranking would
     * then act on as though it were a fact.
     */
    @ColumnInfo(name = "indoor")
    public String indoor;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    /**
     * Where this row came from — see
     * {@link com.comp90018.storystep.data.AttractionProvenanceCodec}.
     *
     * <p>Recorded because a seeded place and a place returned by a real service are not
     * interchangeable evidence, and a screen that cannot tell them apart will eventually present
     * one as the other. It is also what a refresh deletes by, so that replacing fetched content
     * cannot take the bundled set with it.
     */
    @ColumnInfo(name = "provenance")
    public String provenance;

    /** When this row was written, in epoch milliseconds. Zero for rows predating the column. */
    @ColumnInfo(name = "fetched_at_millis")
    public long fetchedAtMillis;

    /**
     * An optional reference into the source's wider data, such as {@code wikidata:Q1747074}.
     *
     * <p>Kept so a later enrichment step can find real article text for the many fetched records
     * that arrive with no description, without needing another migration to reintroduce a column
     * that was dropped on the way in.
     */
    @ColumnInfo(name = "source_ref")
    public String sourceRef;

    /** The bundled demonstration data set; not fetched from any service. */
    public static final String PROVENANCE_SEED = AttractionProvenanceCodec.CODE_SEED;

    /** A photograph's URL from the source, or null. Added at schema version 7. */
    @ColumnInfo(name = "image_url")
    public String imageUrl;

    /** Sitelink count from Wikidata; zero when unknown. Added at schema version 8. */
    @ColumnInfo(name = "popularity", defaultValue = "0")
    public int popularity;
}
