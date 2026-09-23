package com.comp90018.storystep.domain.theme;

import java.util.*;

/** Arrival is derived from committed visits, never a second independently written counter. */
public final class ThemeProgress {
    public enum Status { PENDING, ARRIVED, SKIPPED }
    public final String sessionId;
    public final ThemeDefinition theme;
    public final boolean finished;
    private final Set<String> arrived, skipped;
    private final Map<String, Integer> answers;

    public ThemeProgress(String sessionId, ThemeDefinition theme, boolean finished,
            Set<String> arrived, Set<String> skipped, Map<String, Integer> answers) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.theme = Objects.requireNonNull(theme); this.finished = finished;
        this.arrived = Collections.unmodifiableSet(new HashSet<>(arrived));
        this.skipped = Collections.unmodifiableSet(new HashSet<>(skipped));
        this.answers = Collections.unmodifiableMap(new HashMap<>(answers));
    }
    public Status status(String id) {
        theme.stop(id);
        return arrived.contains(id) ? Status.ARRIVED : skipped.contains(id) ? Status.SKIPPED : Status.PENDING;
    }
    public Integer answer(String id) { theme.stop(id); return answers.get(id); }
    public int arrivedCount() { return count(Status.ARRIVED); }
    public int skippedCount() { return count(Status.SKIPPED); }
    public int pendingCount() { return count(Status.PENDING); }
    public boolean allVisited() { return arrivedCount() == theme.stops.size(); }
    public ThemeStop nextPending() {
        for (ThemeStop stop : theme.stops) { if (status(stop.id) == Status.PENDING) { return stop; } }
        return null;
    }
    private int count(Status status) {
        int count = 0;
        for (ThemeStop stop : theme.stops) { if (status(stop.id) == status) { count++; } }
        return count;
    }
}
