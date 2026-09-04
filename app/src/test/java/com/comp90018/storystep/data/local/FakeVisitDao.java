package com.comp90018.storystep.data.local;

import com.comp90018.storystep.domain.repository.DuplicateVisitException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * In-memory {@link VisitDao} that honours the real table's unique
 * {@code (session_id, attraction_id)} index by throwing on a repeated pair.
 *
 * <p>Throwing rather than ignoring is the point: the exactly-once guarantee REQ-13 relies on comes
 * from the constraint, so a fake that quietly swallowed a duplicate would hide the very behaviour
 * the tests exist to check.
 *
 * <p>It raises the domain's duplicate type rather than SQLite's, because these are JVM tests with
 * no Android runtime behind them. The store maps one to the other, so the caller sees the same
 * outcome either way; the real constraint itself is exercised by the instrumented DAO tests.
 */
public final class FakeVisitDao implements VisitDao {

    private final List<VisitEntity> rows = new ArrayList<>();

    /** Set to make the next insert throw, standing in for a storage failure. */
    public RuntimeException failWrites;

    @Override
    public long insert(VisitEntity visit) {
        if (failWrites != null) {
            throw failWrites;
        }
        if (find(visit.sessionId, visit.attractionId) != null) {
            throw new DuplicateVisitException(visit.sessionId, visit.attractionId);
        }
        VisitEntity stored = copy(visit);
        stored.rowId = rows.size() + 1L;
        rows.add(stored);
        return stored.rowId;
    }

    @Override
    public List<VisitEntity> findBySession(String sessionId) {
        List<VisitEntity> found = new ArrayList<>();
        for (VisitEntity row : rows) {
            if (row.sessionId.equals(sessionId)) {
                found.add(copy(row));
            }
        }
        found.sort((first, second) -> Long.compare(
                first.arrivalWallClockMillis, second.arrivalWallClockMillis));
        return found;
    }

    @Override
    public int countBySession(String sessionId) {
        return findBySession(sessionId).size();
    }

    @Override
    public VisitEntity findFirstUnread(String sessionId) {
        for (VisitEntity row : findBySession(sessionId)) {
            if (row.unread) {
                return row;
            }
        }
        return null;
    }

    @Override
    public VisitEntity find(String sessionId, String attractionId) {
        for (VisitEntity row : rows) {
            if (row.sessionId.equals(sessionId) && row.attractionId.equals(attractionId)) {
                return copy(row);
            }
        }
        return null;
    }

    @Override
    public int markRead(String sessionId, String attractionId) {
        for (VisitEntity row : rows) {
            if (row.sessionId.equals(sessionId) && row.attractionId.equals(attractionId)) {
                row.unread = false;
                return 1;
            }
        }
        return 0;
    }

    @Override
    public int deleteBySession(String sessionId) {
        int removed = 0;
        for (Iterator<VisitEntity> iterator = rows.iterator(); iterator.hasNext(); ) {
            if (iterator.next().sessionId.equals(sessionId)) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    private static VisitEntity copy(VisitEntity source) {
        VisitEntity copy = new VisitEntity();
        copy.rowId = source.rowId;
        copy.sessionId = source.sessionId;
        copy.attractionId = source.attractionId;
        copy.arrivalWallClockMillis = source.arrivalWallClockMillis;
        copy.attractionName = source.attractionName;
        copy.story = source.story;
        copy.unread = source.unread;
        return copy;
    }
}
