package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * An attraction the user has deliberately kept, together with when they kept it.
 *
 * <p>The attraction is carried by value rather than by identifier. A saved place must stay
 * readable when the attraction cache is refreshed, evicted or replaced by a real service, so the
 * saved record owns its own copy of the content — the same reason a session snapshots its
 * destination and a visit snapshots its story.
 */
public final class SavedAttraction {

    private final Attraction attraction;
    private final long savedAtMillis;

    public SavedAttraction(Attraction attraction, long savedAtMillis) {
        this.attraction = Objects.requireNonNull(attraction, "attraction");
        this.savedAtMillis = savedAtMillis;
    }

    public Attraction getAttraction() {
        return attraction;
    }

    public String getId() {
        return attraction.getId();
    }

    /** When the user saved this place, in epoch milliseconds. Drives most-recent-first order. */
    public long getSavedAtMillis() {
        return savedAtMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SavedAttraction)) {
            return false;
        }
        SavedAttraction that = (SavedAttraction) other;
        return savedAtMillis == that.savedAtMillis && attraction.equals(that.attraction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attraction, savedAtMillis);
    }
}
