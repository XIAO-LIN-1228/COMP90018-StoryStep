package com.comp90018.storystep.data.local;

import java.util.ArrayList;
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
        return 1;
    }

    public int size() {
        return rows.size();
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
        return target;
    }
}
