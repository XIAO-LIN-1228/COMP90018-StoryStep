package com.comp90018.storystep.data.local;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory {@link SessionDao}.
 *
 * <p>Stores copies rather than the caller's objects, because the real DAO writes rows to SQLite:
 * a fake that handed back the same mutable instance would let a test pass while the production
 * store forgot to call {@code update}.
 */
public final class FakeSessionDao implements SessionDao {

    private final Map<String, SessionEntity> rows = new LinkedHashMap<>();

    /** Set to make the next write throw, standing in for a full disk or a locked database. */
    public RuntimeException failWrites;

    /** Session ids whose rows were deleted, for asserting the cascade's ownership. */
    public final List<String> deleted = new ArrayList<>();

    /**
     * Notified for every session this fake removes.
     *
     * <p>The real schema deletes route points and visits through a foreign key, which no in-memory
     * map can do on its own. Wiring this to the child fakes is what lets a JVM test see the same
     * ownership the database enforces — but only the instrumented DAO test proves the real cascade
     * actually runs.
     */
    public interface CascadeListener {
        void onSessionDeleted(String sessionId);
    }

    public CascadeListener cascadeListener;

    @Override
    public void insert(SessionEntity session) {
        failIfRequested();
        if (rows.containsKey(session.id)) {
            throw new IllegalStateException("duplicate session id: " + session.id);
        }
        rows.put(session.id, copy(session));
    }

    @Override
    public void update(SessionEntity session) {
        failIfRequested();
        if (!rows.containsKey(session.id)) {
            throw new IllegalStateException("no such session: " + session.id);
        }
        rows.put(session.id, copy(session));
    }

    @Override
    public SessionEntity findById(String sessionId) {
        SessionEntity stored = rows.get(sessionId);
        return stored == null ? null : copy(stored);
    }

    @Override
    public SessionEntity findUnfinished() {
        SessionEntity newest = null;
        for (SessionEntity row : rows.values()) {
            if (row.finished) {
                continue;
            }
            if (newest == null || row.startWallClockMillis >= newest.startWallClockMillis) {
                newest = row;
            }
        }
        return newest == null ? null : copy(newest);
    }

    @Override
    public int countUnfinished() {
        int count = 0;
        for (SessionEntity row : rows.values()) {
            if (!row.finished) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteById(String sessionId) {
        failIfRequested();
        if (rows.remove(sessionId) == null) {
            return 0;
        }
        deleted.add(sessionId);
        cascade(sessionId);
        return 1;
    }

    @Override
    public List<SessionHistoryRow> findFinished() {
        List<SessionEntity> finished = new ArrayList<>();
        for (SessionEntity row : rows.values()) {
            if (row.finished) {
                finished.add(row);
            }
        }
        // Mirrors the DAO's total order: newest end first, then newest start, then id.
        Collections.sort(finished, new Comparator<SessionEntity>() {
            @Override
            public int compare(SessionEntity left, SessionEntity right) {
                int byEnd = Long.compare(right.endWallClockMillis, left.endWallClockMillis);
                if (byEnd != 0) {
                    return byEnd;
                }
                int byStart = Long.compare(right.startWallClockMillis, left.startWallClockMillis);
                return byStart != 0 ? byStart : left.id.compareTo(right.id);
            }
        });
        List<SessionHistoryRow> projected = new ArrayList<>(finished.size());
        for (SessionEntity row : finished) {
            SessionHistoryRow historyRow = new SessionHistoryRow();
            historyRow.id = row.id;
            historyRow.startWallClockMillis = row.startWallClockMillis;
            historyRow.endWallClockMillis = row.endWallClockMillis;
            historyRow.activeDurationMillis = row.activeDurationMillis;
            historyRow.distanceMeters = row.distanceMeters;
            historyRow.segmentCount = row.segmentCount;
            historyRow.interrupted = row.interrupted;
            historyRow.destinationName = row.destinationName;
            historyRow.title = row.title;
            historyRow.placeName = row.placeName;
            historyRow.photoFiles = row.photoFiles;
            historyRow.steps = row.steps;
            historyRow.ascentMeters = row.ascentMeters;
            historyRow.visitCount = visitCounts.containsKey(row.id)
                    ? visitCounts.get(row.id) : 0;
            projected.add(historyRow);
        }
        return projected;
    }

    @Override
    public int saveWalkTotals(String sessionId, Long steps, Double ascentMeters) {
        SessionEntity row = rows.get(sessionId);
        if (row == null) {
            return 0;
        }
        row.steps = steps;
        row.ascentMeters = ascentMeters;
        return 1;
    }

    @Override
    public int publish(String sessionId, String title, String placeName, String photoFiles) {
        SessionEntity row = rows.get(sessionId);
        if (row == null || !row.finished) {
            return 0;
        }
        row.title = title;
        row.placeName = placeName;
        row.photoFiles = photoFiles;
        return 1;
    }

    @Override
    public int countFinished() {
        int count = 0;
        for (SessionEntity row : rows.values()) {
            if (row.finished) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int deleteAllFinished() {
        failIfRequested();
        int removed = 0;
        for (String sessionId : new ArrayList<>(rows.keySet())) {
            if (rows.get(sessionId).finished) {
                rows.remove(sessionId);
                deleted.add(sessionId);
                cascade(sessionId);
                removed++;
            }
        }
        return removed;
    }

    /**
     * The visit count {@link #findFinished()} reports per session.
     *
     * <p>Set by the test, because the real query counts rows in another table that this fake has
     * no view of.
     */
    public final Map<String, Integer> visitCounts = new LinkedHashMap<>();

    public int size() {
        return rows.size();
    }

    private void cascade(String sessionId) {
        if (cascadeListener != null) {
            cascadeListener.onSessionDeleted(sessionId);
        }
    }

    private void failIfRequested() {
        if (failWrites != null) {
            throw failWrites;
        }
    }

    private static SessionEntity copy(SessionEntity source) {
        SessionEntity target = new SessionEntity();
        target.id = source.id;
        target.state = source.state;
        target.pauseReason = source.pauseReason;
        target.startWallClockMillis = source.startWallClockMillis;
        target.endWallClockMillis = source.endWallClockMillis;
        target.activeDurationMillis = source.activeDurationMillis;
        target.distanceMeters = source.distanceMeters;
        target.segment = source.segment;
        target.segmentCount = source.segmentCount;
        target.finished = source.finished;
        target.lastCheckpointWallClockMillis = source.lastCheckpointWallClockMillis;
        target.destinationId = source.destinationId;
        target.destinationName = source.destinationName;
        target.destinationCategory = source.destinationCategory;
        target.destinationDescription = source.destinationDescription;
        target.destinationIndoor = source.destinationIndoor;
        target.destinationLatitude = source.destinationLatitude;
        target.destinationLongitude = source.destinationLongitude;
        target.weatherAreaKey = source.weatherAreaKey;
        target.weatherCondition = source.weatherCondition;
        target.weatherTemperatureCelsius = source.weatherTemperatureCelsius;
        target.weatherObservedAtMillis = source.weatherObservedAtMillis;
        target.weatherFetchedAtMillis = source.weatherFetchedAtMillis;
        target.movementState = source.movementState;
        target.movementStationaryMillis = source.movementStationaryMillis;
        target.movementMovingMillis = source.movementMovingMillis;
        target.interrupted = source.interrupted;
        return target;
    }
}
