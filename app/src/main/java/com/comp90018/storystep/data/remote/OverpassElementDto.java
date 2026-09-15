package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Wire model for one Overpass element: an OSM node, way or relation.
 *
 * <p>Nodes carry {@link #lat}/{@link #lon} directly. Ways and relations have no single coordinate,
 * so the query asks for {@code out center} and they come back with {@link #center} instead. Both
 * shapes are normal, and a record with neither is unusable.
 *
 * <p>{@link #tags} is an open map rather than a set of declared fields. OSM's tagging is
 * open-ended and a record's useful content is not knowable in advance, so the mapper reads the
 * keys it understands and keeps {@code wikidata}/{@code wikipedia} for later, instead of a DTO
 * that silently discards everything nobody thought to declare.
 */
public class OverpassElementDto {

    /** {@code node}, {@code way} or {@code relation}. Part of the stable qualified id. */
    @SerializedName("type")
    @Nullable
    public String type;

    /** The OSM object id. Unique only within its {@link #type}, which is why the id is qualified. */
    @SerializedName("id")
    @Nullable
    public Long id;

    @SerializedName("lat")
    @Nullable
    public Double lat;

    @SerializedName("lon")
    @Nullable
    public Double lon;

    /** Present for ways and relations because the query asks for {@code out center}. */
    @SerializedName("center")
    @Nullable
    public Center center;

    @SerializedName("tags")
    @Nullable
    public Map<String, String> tags;

    /** The computed centre of a way or relation. */
    public static class Center {

        @SerializedName("lat")
        @Nullable
        public Double lat;

        @SerializedName("lon")
        @Nullable
        public Double lon;
    }
}
