package com.comp90018.storystep.domain.theme;

import java.util.*;

/** Immutable versioned content, copied into the journey before collection starts. */
public final class ThemeDefinition {
    public final String id, title, description;
    public final int version;
    public final List<ThemeStop> stops;

    public ThemeDefinition(String id, String title, String description, int version, List<ThemeStop> stops) {
        if (id == null || id.trim().isEmpty() || title == null || title.trim().isEmpty()
                || description == null || version < 1 || stops == null || stops.isEmpty() || stops.size() > 50) {
            throw new IllegalArgumentException("invalid theme");
        }
        Set<String> ids = new HashSet<>();
        for (ThemeStop stop : stops) {
            if (stop == null || !ids.add(stop.id)) { throw new IllegalArgumentException("duplicate/missing stop"); }
        }
        this.id = id; this.title = title; this.description = description; this.version = version;
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
    }

    public ThemeStop stop(String id) {
        for (ThemeStop stop : stops) { if (stop.id.equals(id)) { return stop; } }
        throw new IllegalArgumentException("unknown theme stop");
    }
}
