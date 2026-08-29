package com.comp90018.storystep.ui.nearby;

import com.comp90018.storystep.domain.model.AttractionCategory;

import java.util.Objects;

/**
 * One row of the nearby list. The distance is already calculated by the ViewModel; the view
 * layer only formats it.
 */
public final class AttractionListItem {

    private final String id;
    private final String name;
    private final AttractionCategory category;
    private final double distanceMeters;

    public AttractionListItem(String id,
                              String name,
                              AttractionCategory category,
                              double distanceMeters) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.distanceMeters = distanceMeters;
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

    public double getDistanceMeters() {
        return distanceMeters;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AttractionListItem)) {
            return false;
        }
        AttractionListItem that = (AttractionListItem) other;
        return Double.compare(distanceMeters, that.distanceMeters) == 0
                && id.equals(that.id)
                && name.equals(that.name)
                && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, distanceMeters);
    }
}
