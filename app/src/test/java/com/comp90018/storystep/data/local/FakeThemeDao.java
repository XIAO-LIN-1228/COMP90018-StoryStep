package com.comp90018.storystep.data.local;

import java.util.*;

/** Transaction rollback and foreign keys are deliberately tested with real SQLite on device. */
public final class FakeThemeDao implements ThemeDao {
    public final Map<String, ThemeRunEntity> runs = new HashMap<>();
    private final List<ThemeStopEntity> stops = new ArrayList<>();
    public RuntimeException failure;
    private void check() { if (failure != null) { throw failure; } }
    public void insertRun(ThemeRunEntity run) { check(); runs.put(run.sessionId, run); }
    public void insertStops(List<ThemeStopEntity> rows) { check(); stops.addAll(rows); }
    public ThemeRunEntity findRun(String id) { check(); return runs.get(id); }
    public List<ThemeStopEntity> findStops(String id) {
        check(); List<ThemeStopEntity> result = new ArrayList<>();
        for (ThemeStopEntity row : stops) { if (row.sessionId.equals(id)) { result.add(row); } }
        return result;
    }
    public int setSkipped(String id, String stopId, boolean skipped) {
        for (ThemeStopEntity row : findStops(id)) {
            if (row.stopId.equals(stopId)) { row.skipped = skipped; return 1; }
        }
        return 0;
    }
    public int answer(String id, String stopId, int answer) {
        for (ThemeStopEntity row : findStops(id)) {
            if (row.stopId.equals(stopId)) { row.answer = answer; return 1; }
        }
        return 0;
    }
}
