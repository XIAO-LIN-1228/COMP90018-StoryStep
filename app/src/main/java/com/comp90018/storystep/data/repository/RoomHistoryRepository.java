package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.LocalSessionHistoryMapper;
import com.comp90018.storystep.data.local.RoutePointDao;
import com.comp90018.storystep.data.local.RoutePointEntity;
import com.comp90018.storystep.data.local.SessionDao;
import com.comp90018.storystep.data.local.SessionEntity;
import com.comp90018.storystep.data.local.SessionHistoryRow;
import com.comp90018.storystep.data.local.Transactions;
import com.comp90018.storystep.data.local.VisitDao;
import com.comp90018.storystep.data.local.VisitEntity;
import com.comp90018.storystep.domain.geo.RouteSegments;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.SessionHistoryDetail;
import com.comp90018.storystep.domain.model.SessionHistoryEntry;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.repository.ActiveSessionNotDeletableException;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionNotFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The user's exploration history, backed by Room.
 *
 * <p>Reads and deletes the same tables {@code RoomSessionStore} writes, and deliberately shares no
 * state with it: the two contracts are used at different times by different callers, and a session
 * that is being recorded is not one that can appear here.
 *
 * <p>Deletion is wrapped in a transaction even though the foreign keys already cascade. Removing
 * one session is a check-then-delete — the row has to be read to refuse an unfinished session —
 * and clearing reports how many rows went, a number that has to describe what was actually
 * removed rather than what was there a moment earlier.
 *
 * <p>Queries run on the I/O executor and results are dispatched on the main executor, matching the
 * {@code RepositoryCallback} contract.
 */
public final class RoomHistoryRepository implements HistoryRepository {

    private final Transactions transactions;
    private final SessionDao sessionDao;
    private final RoutePointDao routePointDao;
    private final VisitDao visitDao;
    private final Executor ioExecutor;
    private final Executor mainExecutor;
    private final com.comp90018.storystep.domain.repository.SessionArtifacts artifacts;

    /** Main-thread only. */
    private final List<Observer> observers = new ArrayList<>(2);

    public RoomHistoryRepository(Transactions transactions,
                                 SessionDao sessionDao,
                                 RoutePointDao routePointDao,
                                 VisitDao visitDao,
                                 Executor ioExecutor,
                                 Executor mainExecutor) {
        this(transactions, sessionDao, routePointDao, visitDao, ioExecutor, mainExecutor,
                com.comp90018.storystep.domain.repository.SessionArtifacts.NONE);
    }

    /** With the artifacts a session owns outside the database, discarded when it is deleted. */
    public RoomHistoryRepository(Transactions transactions,
                                 SessionDao sessionDao,
                                 RoutePointDao routePointDao,
                                 VisitDao visitDao,
                                 Executor ioExecutor,
                                 Executor mainExecutor,
                                 com.comp90018.storystep.domain.repository.SessionArtifacts artifacts) {
        this.artifacts = requireNonNull(artifacts, "artifacts");
        this.transactions = requireNonNull(transactions, "transactions");
        this.sessionDao = requireNonNull(sessionDao, "sessionDao");
        this.routePointDao = requireNonNull(routePointDao, "routePointDao");
        this.visitDao = requireNonNull(visitDao, "visitDao");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> callback) {
        requireNonNull(callback, "callback");
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<SessionHistoryRow> rows = sessionDao.findFinished();
                    final List<SessionHistoryEntry> entries =
                            LocalSessionHistoryMapper.toEntries(rows);
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(entries);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void loadVisitedAttractionIds(RepositoryCallback<java.util.Set<String>> callback) {
        requireNonNull(callback, "callback");
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final java.util.Set<String> ids =
                            new java.util.HashSet<>(visitDao.visitedAttractionIds());
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(ids);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void loadDetail(String sessionId, RepositoryCallback<SessionHistoryDetail> callback) {
        requireNonNull(callback, "callback");
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    SessionEntity stored = sessionDao.findById(sessionId);
                    // An unfinished session is not history. Reported the same way an unknown id
                    // is, because from this screen's point of view they are the same thing:
                    // nothing it can show.
                    if (stored == null || !stored.finished) {
                        fail(callback, new SessionNotFoundException(sessionId));
                        return;
                    }
                    List<VisitEntity> visitRows = visitDao.findBySession(sessionId);
                    List<Visit> visits = new ArrayList<>(visitRows.size());
                    for (VisitEntity row : visitRows) {
                        visits.add(new Visit(row.sessionId, row.attractionId,
                                row.arrivalWallClockMillis, row.attractionName, row.story,
                                row.unread));
                    }
                    List<RoutePointEntity> pointRows = routePointDao.findBySession(sessionId);
                    List<RoutePoint> points = LocalSessionHistoryMapper.toRoutePoints(pointRows);
                    final SessionHistoryDetail detail = new SessionHistoryDetail(
                            LocalSessionHistoryMapper.toEntry(stored, visits.size()),
                            visits,
                            RouteSegments.split(points),
                            LocalSessionHistoryMapper.toMovement(stored),
                            LocalSessionHistoryMapper.toWeather(stored));
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(detail);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void delete(String sessionId, RepositoryCallback<Boolean> callback) {
        requireNonNull(callback, "callback");
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean[] removed = new boolean[1];
                    transactions.runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            SessionEntity stored = sessionDao.findById(sessionId);
                            if (stored == null) {
                                // Already gone. Idempotent by design: a double tap and a stale id
                                // both land here, and neither is something to report as an error.
                                removed[0] = false;
                                return;
                            }
                            if (!stored.finished) {
                                throw new ActiveSessionNotDeletableException(sessionId);
                            }
                            // Route points and visits go with the session through the foreign
                            // key's cascade; nothing else does.
                            removed[0] = sessionDao.deleteById(sessionId) > 0;
                        }
                    });
                    if (removed[0]) {
                        artifacts.discard(sessionId);
                    }
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (removed[0]) {
                                publishChanged();
                            }
                            callback.onSuccess(removed[0]);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void clearAll(RepositoryCallback<Integer> callback) {
        requireNonNull(callback, "callback");

        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final int[] removed = new int[1];
                    final List<String> removedSessionIds = new ArrayList<>();

                    transactions.runInTransaction(new Runnable() {
                        @Override
                        public void run() {
                            List<SessionHistoryRow> finishedSessions =
                                    sessionDao.findFinished();

                            for (SessionHistoryRow session : finishedSessions) {
                                removedSessionIds.add(session.id);
                            }

                            removed[0] = sessionDao.deleteAllFinished();
                        }
                    });

                    /*
                     * Delete photographs only for finished sessions that belonged
                     * to history. An unfinished active session and its photographs
                     * must remain untouched.
                     */
                    for (String sessionId : removedSessionIds) {
                        artifacts.discard(sessionId);
                    }

                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (removed[0] > 0) {
                                publishChanged();
                            }

                            callback.onSuccess(removed[0]);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void publish(String sessionId,
                        String title,
                        String placeName,
                        List<String> photoFiles,
                        RepositoryCallback<Boolean> callback) {
        requireNonNull(callback, "callback");
        if (sessionId == null || title == null || title.trim().isEmpty()) {
            fail(callback, new IllegalArgumentException("a published walk needs an id and a title"));
            return;
        }
        String column = com.comp90018.storystep.data.local.LocalSessionHistoryMapper
                .joinPhotoFiles(photoFiles);
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int written = sessionDao.publish(sessionId, title.trim(), placeName, column);
                    if (written == 0) {
                        fail(callback, new SessionNotFoundException(sessionId));
                        return;
                    }
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            publishChanged();
                            callback.onSuccess(true);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void addObserver(Observer observer) {
        requireNonNull(observer, "observer");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    private void publishChanged() {
        // Copied before iterating: an observer may remove itself while being notified.
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onHistoryChanged();
        }
    }

    private <T> void fail(RepositoryCallback<T> callback, Throwable error) {
        mainExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onError(error);
            }
        });
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
