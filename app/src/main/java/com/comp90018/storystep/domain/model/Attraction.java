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
    private final boolean indoor;
    private final GeoPoint location;

    public Attraction(String id,
                      String name,
                      AttractionCategory category,
                      String description,
                      boolean indoor,
                      GeoPoint location) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.category = Objects.requireNonNull(category, "category");
        this.description = Objects.requireNonNull(description, "description");
        this.indoor = indoor;
        this.location = Objects.requireNonNull(location, "location");
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

    /** True when the attraction is primarily indoors; used later by weather-aware ranking. */
    public boolean isIndoor() {
        return indoor;
    }

    public GeoPoint getLocation() {
        return location;
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
                && location.equals(that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, description, indoor, location);
    }
}
