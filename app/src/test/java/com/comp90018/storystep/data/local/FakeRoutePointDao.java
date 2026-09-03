package com.comp90018.storystep.data.local;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * In-memory {@link RoutePointDao} that honours the real table's unique
 * {@code (session_id, sequence)} index by ignoring a repeated pair, which is the behaviour that
 * makes a retried append safe.
 */
public final class FakeRoutePointDao implements RoutePointDao {

    private final List<RoutePointEntity> rows = new ArrayList<>();

    /** Set to make the next insert throw. */
    public RuntimeException failWrites;

    @Override
    public void insertAll(List<RoutePointEntity> points) {
        if (failWrites != null) {
            throw failWrites;
        }
        for (RoutePointEntity point : points) {
            if (exists(point.sessionId, point.sequence)) {
                continue;
            }
            rows.add(copy(point));
        }
    }

    @Override
    public List<RoutePointEntity> findBySession(String sessionId) {
        List<RoutePointEntity> found = new ArrayList<>();
        for (RoutePointEntity row : rows) {
            if (row.sessionId.equals(sessionId)) {
                found.add(copy(row));
            }
        }
        found.sort((first, second) -> Integer.compare(first.sequence, second.sequence));
        return found;
    }

    @Override
    public int countBySession(String sessionId) {
        return findBySession(sessionId).size();
    }

    @Override
    public int deleteBySession(String sessionId) {
        int removed = 0;
        for (Iterator<RoutePointEntity> each = rows.iterator(); each.hasNext(); ) {
            if (each.next().sessionId.equals(sessionId)) {
                each.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Mirrors the {@code ON DELETE CASCADE} the real schema declares. */
    public void cascadeDelete(String sessionId) {
        deleteBySession(sessionId);
    }

    public int totalRows() {
        return rows.size();
    }

    private boolean exists(String sessionId, int sequence) {
        for (RoutePointEntity row : rows) {
            if (row.sessionId.equals(sessionId) && row.sequence == sequence) {
                return true;
            }
        }
        return false;
    }

    private static RoutePointEntity copy(RoutePointEntity source) {
        RoutePointEntity target = new RoutePointEntity();
        target.rowId = source.rowId;
        target.sessionId = source.sessionId;
        target.sequence = source.sequence;
        target.segment = source.segment;
        target.latitude = source.latitude;
        target.longitude = source.longitude;
        target.accuracyMeters = source.accuracyMeters;
        target.wallClockMillis = source.wallClockMillis;
        return target;
    }
}
