package com.comp90018.storystep.data;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.AttractionProvenance;

/**
 * Converts {@link AttractionProvenance} to and from the stable codes stored in the
 * {@code provenance} column.
 *
 * <p>The codes are short strings rather than the enum's Java names because migrations and DAO
 * queries reference them literally — {@code MIGRATION_1_2} back-filled every existing row to
 * {@code 'seed'}, and the refresh path deletes by provenance — so renaming the enum must not
 * silently change what those statements match.
 *
 * <p>An unrecognised code reads back as {@link AttractionProvenance#SEED}. That is the cautious
 * direction: a row of unknown origin gets no licence attribution claimed for it and is not
 * presented as a live provider result.
 */
public final class AttractionProvenanceCodec {

    /** The bundled demonstration data set; not fetched from any service. */
    public static final String CODE_SEED = "seed";

    /** Fetched from the OpenStreetMap Overpass API. */
    public static final String CODE_OPENSTREETMAP = "osm";

    private AttractionProvenanceCodec() {
        // Utility class.
    }

    public static String toCode(AttractionProvenance provenance) {
        return provenance == AttractionProvenance.OPENSTREETMAP ? CODE_OPENSTREETMAP : CODE_SEED;
    }

    public static AttractionProvenance fromCode(@Nullable String code) {
        return CODE_OPENSTREETMAP.equals(code)
                ? AttractionProvenance.OPENSTREETMAP
                : AttractionProvenance.SEED;
    }
}
