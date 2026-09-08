package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * A point of interest the user can discover. This is the application-owned model: Room entities
 * and remote DTOs are mapped into it, so no persistence or service schema leaks into the UI.
 */
public final class Attraction {

    private final String id;
    private final String name;
    private final AttractionCategory category;
    private final String description;
    private final IndoorClassification indoor;
    private final GeoPoint location;
    private final AttractionProvenance provenance;
    private final String sourceRef;

    public Attraction(String id,
                      String name,
                      AttractionCategory category,
                      String description,
                      IndoorClassification indoor,
                      GeoPoint location,
                      AttractionProvenance provenance,
                      String sourceRef) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.description = Objects.requireNonNull(description, "description");
        this.indoor = Objects.requireNonNull(indoor, "indoor");
        this.location = Objects.requireNonNull(location, "location");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.sourceRef = sourceRef;
    }

    /**
     * A bundled demonstration record: {@link AttractionProvenance#SEED} and no source reference.
     *
     * <p>A convenience for the seed set and for tests, so neither has to repeat two constants that
     * are the same for every hand-written record.
     */
    public static Attraction seeded(String id,
                                    String name,
                                    AttractionCategory category,
                                    String description,
                                    IndoorClassification indoor,
                                    GeoPoint location) {
        return new Attraction(id, name, category, description, indoor, location,
                AttractionProvenance.SEED, null);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public AttractionCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    /** True when the attraction has readable content of its own; fetched records often do not. */
    public boolean hasDescription() {
        return !description.isEmpty();
    }

    /** Indoors, outdoors or unknown; used by weather-aware ranking, which must respect unknown. */
    public IndoorClassification getIndoor() {
        return indoor;
    }

    public GeoPoint getLocation() {
        return location;
    }

    /** Which source this record came from; drives attribution and seed-versus-live labelling. */
    public AttractionProvenance getProvenance() {
        return provenance;
    }

    /**
     * An optional reference into the source's wider data, such as {@code Q1747074} or
     * {@code en:Royal Exhibition Building}.
     *
     * <p>Kept rather than discarded so that a later enrichment step can look up real article text
     * for records the provider gave no description for, without another schema migration. Nothing
     * reads it yet.
     *
     * @return the reference, or {@code null} when the source offered none
     */
    public String getSourceRef() {
        return sourceRef;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Attraction)) {
            return false;
        }
        Attraction that = (Attraction) other;
        return indoor == that.indoor
                && id.equals(that.id)
                && name.equals(that.name)
                && category == that.category
                && description.equals(that.description)
                && location.equals(that.location)
                && provenance == that.provenance
                && Objects.equals(sourceRef, that.sourceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, description, indoor, location, provenance,
                sourceRef);
    }

    @Override
    public String toString() {
        return "Attraction{" + id + ", " + name + ", " + provenance + '}';
    }
}
