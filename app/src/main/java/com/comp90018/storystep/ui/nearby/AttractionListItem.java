package com.comp90018.storystep.ui.nearby;

import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import androidx.annotation.Nullable;

import java.util.Objects;

/**
 * One row of the nearby list. The distance is already calculated by the ViewModel; the view
 * layer only formats it.
 *
 * <p>{@code saved} takes part in {@link #equals(Object)} because the adapter's {@code DiffUtil}
 * decides whether to rebind from it: a row whose only change is its bookmark would otherwise keep
 * drawing the old icon.
 */
public final class AttractionListItem {

    private final String id;
    private final String name;
    private final AttractionCategory category;
    private final double distanceMeters;
    private final boolean saved;
    @Nullable private final GeoPoint location;

    public AttractionListItem(String id,
                              String name,
                              AttractionCategory category,
                              double distanceMeters,
                              boolean saved) {
        this(id, name, category, distanceMeters, saved, null);
    }

    public AttractionListItem(String id,
                              String name,
                              AttractionCategory category,
                              double distanceMeters,
                              boolean saved,
                              @Nullable GeoPoint location) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.distanceMeters = distanceMeters;
        this.saved = saved;
        this.location = location;
    }

    /** Where the place is, for pinning it on the map; absent for rows built without one. */
    @Nullable
    public GeoPoint getLocation() {
        return location;
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

    /** Whether the user has kept this place. Part of the row's identity for diffing, below. */
    public boolean isSaved() {
        return saved;
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
                && saved == that.saved
                && id.equals(that.id)
                && name.equals(that.name)
                && category == that.category;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, distanceMeters, saved);
    }
}
