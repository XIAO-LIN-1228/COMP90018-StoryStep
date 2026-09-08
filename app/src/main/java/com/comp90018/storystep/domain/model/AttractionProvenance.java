package com.comp90018.storystep.domain.model;

/**
 * Where an attraction record came from.
 *
 * <p>Carried on the model rather than inferred from an id prefix, because two things depend on it
 * and neither may guess. Attribution is a licence obligation — a place fetched from OpenStreetMap
 * must be shown with its credit — and a bundled demonstration record must never be presented as a
 * live result from a service.
 *
 * <p>This is an enum of sources, not of labels. The user-visible credit text lives in the UI layer
 * along with every other string.
 */
public enum AttractionProvenance {

    /** The bundled demonstration set. Not fetched from any service. */
    SEED,

    /** Fetched from the OpenStreetMap Overpass API. Requires visible ODbL attribution. */
    OPENSTREETMAP;

    /** True when a licence requires this record's source to be credited where it is displayed. */
    public boolean requiresAttribution() {
        return this == OPENSTREETMAP;
    }
}
