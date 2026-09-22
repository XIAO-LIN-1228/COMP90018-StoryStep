package com.comp90018.storystep.domain.theme;

import static org.junit.Assert.*;
import com.comp90018.storystep.data.repository.ThemeCatalog;
import com.comp90018.storystep.data.local.ThemeJson;
import java.util.*;
import org.junit.Test;

public class ThemeProgressTest {
    private final ThemeDefinition theme = ThemeCatalog.parkville();
    @Test public void committedArrivalWinsOverSkipAndUnrelatedVisitsDoNotCount() {
        ThemeProgress p = new ThemeProgress("s", theme, false,
                new HashSet<>(Arrays.asList("old-arts", "unrelated")),
                new HashSet<>(Arrays.asList("old-arts", "old-quad")), Collections.emptyMap());
        assertEquals(1, p.arrivedCount()); assertEquals(1, p.skippedCount());
        assertEquals(1, p.pendingCount()); assertFalse(p.allVisited());
        assertEquals("system-garden", p.nextPending().id);
    }
    @Test public void skippingEverythingNeverClaimsCompletion() {
        Set<String> ids = new HashSet<>(); for (ThemeStop s : theme.stops) { ids.add(s.id); }
        ThemeProgress p = new ThemeProgress("s", theme, true, Collections.emptySet(), ids, Collections.emptyMap());
        assertFalse(p.allVisited()); assertNull(p.nextPending()); assertEquals(3, p.skippedCount());
    }
    @Test public void completionRequiresEveryDistinctStation() {
        Set<String> ids = new HashSet<>(); for (ThemeStop s : theme.stops) { ids.add(s.id); }
        ThemeProgress p = new ThemeProgress("s", theme, true, ids, Collections.emptySet(), Collections.emptyMap());
        ids.clear(); assertTrue(p.allVisited()); assertEquals(3, p.arrivedCount());
    }
    @Test public void jsonSnapshotPreservesContentCoordinatesQuestionsAndVersion() {
        ThemeDefinition copy = ThemeJson.decode(ThemeJson.encode(theme));
        assertEquals(theme.id, copy.id); assertEquals(theme.version, copy.version);
        for (ThemeStop s : theme.stops) {
            ThemeStop c = copy.stop(s.id);
            assertEquals(s.story, c.story); assertEquals(s.sourceUrl, c.sourceUrl);
            assertEquals(s.latitude, c.latitude, 0); assertEquals(s.longitude, c.longitude, 0);
            assertEquals(s.options, c.options); assertEquals(s.correctAnswer, c.correctAnswer);
        }
    }
    @Test public void duplicateStopsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ThemeDefinition("x", "X", "X", 1,
                Arrays.asList(theme.stops.get(0), theme.stops.get(0))));
    }
    @Test public void invalidContentAndCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ThemeStop("x", "X", "Story", "http://example.com",
                0, 0, "Question", Arrays.asList("A", "B"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ThemeStop("x", "X", "Story", "https://example.com",
                Double.NaN, 0, "Question", Arrays.asList("A", "B"), 0));
        assertThrows(IllegalArgumentException.class, () -> new ThemeStop("x", "X", "Story", "https://example.com",
                0, 0, "Question", Arrays.asList("A", "B"), 2));
    }
    @Test public void corruptedSnapshotDoesNotProduceInventedContent() {
        assertThrows(RuntimeException.class, () -> ThemeJson.decode("{}"));
        assertThrows(RuntimeException.class, () -> ThemeJson.decode("not-json"));
    }
    @Test public void unknownStationAndMutatingDefinitionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> theme.stop("unknown"));
        assertThrows(UnsupportedOperationException.class, () -> theme.stops.clear());
        assertThrows(UnsupportedOperationException.class, () -> theme.stops.get(0).options.clear());
    }
}
