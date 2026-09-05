package com.comp90018.storystep.data.repository;

import android.database.sqlite.SQLiteConstraintException;

import com.comp90018.storystep.data.AttractionCategoryCodec;
import com.comp90018.storystep.data.local.RoutePointDao;
import com.comp90018.storystep.data.local.RoutePointEntity;
import com.comp90018.storystep.data.local.SessionDao;
import com.comp90018.storystep.data.local.SessionEntity;
import com.comp90018.storystep.data.local.Transactions;
import com.comp90018.storystep.data.local.VisitDao;
import com.comp90018.storystep.data.local.VisitEntity;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.DuplicateVisitException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.time.Clock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Durable {@link SessionStore} backed by Room.
 *
 * <p>Every method does its work on the I/O executor and answers on the main executor, which is the
 * threading contract {@code RepositoryCallback} documents and the coordinator relies on: it is a
 * main-thread object and must never be handed a result on a database thread.
 *
 * <p>The at-most-one-unfinished rule is enforced inside a transaction rather than by checking
 * first and inserting after. Two rapid Start presses reach the same check at the same time
 * otherwise, and the second one would create the ambiguous second session the rule exists to
 * prevent.
 *
 * <p>Restored sessions are always presented as paused. Nothing in this class resumes collection,
 * and nothing about opening the app does either.
 */
public final class RoomSessionStore implements SessionStore {

    private final Transactions transactions;
    private final SessionDao sessionDao;
    private final RoutePointDao routePointDao;
    private final VisitDao visitDao;
    private final Clock clock;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    public RoomSessionStore(Transactions transactions,
                            SessionDao sessionDao,
                            RoutePointDao routePointDao,
                            VisitDao visitDao,
                            Clock clock,
                            Executor ioExecutor,
                            Executor mainExecutor) {
        this.transactions = requireNonNull(transactions, "transactions");
        this.sessionDao = requireNonNull(sessionDao, "sessionDao");
        this.routePointDao = requireNonNull(routePointDao, "routePointDao");
        this.visitDao = requireNonNull(visitDao, "visitDao");
        this.clock = requireNonNull(clock, "clock");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void create(RepositoryCallback<SessionSummary> callback) {
        run(callback, new Work<SessionSummary>() {
            @Override
            public SessionSummary perform() {
                final SessionSummary[] created = new SessionSummary[1];
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity unfinished = sessionDao.findUnfinished();
                        if (unfinished != null) {
                            throw new IllegalStateException(
                                    "an unfinished session already exists: " + unfinished.id);
                        }
                        long startedAt = clock.wallClockMillis();
                        SessionEntity entity = new SessionEntity();
                        entity.id = UUID.randomUUID().toString();
                        entity.state = SessionState.ACTIVE.name();
                        entity.pauseReason = null;
                        entity.startWallClockMillis = startedAt;
                        entity.endWallClockMillis = 0L;
                        entity.activeDurationMillis = 0L;
                        entity.distanceMeters = 0.0;
                        entity.segment = 1;
                        entity.segmentCount = 0;
                        entity.finished = false;
                        entity.lastCheckpointWallClockMillis = startedAt;
                        sessionDao.insert(entity);
                        created[0] = toSummary(entity);
                    }
                });
                return created[0];
            }
        });
    }

    @Override
    public void checkpoint(SessionSummary summary, RepositoryCallback<Void> callback) {
        if (summary == null || summary.getId() == null) {
            fail(callback, new IllegalArgumentException("summary must carry a session id"));
            return;
        }
        final SessionSummary snapshot = summary;
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = requireSession(snapshot.getId());
                        applySummary(stored, snapshot);
                        stored.lastCheckpointWallClockMillis = clock.wallClockMillis();
                        sessionDao.update(stored);
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void appendRoute(String sessionId,
                            int firstSequence,
                            List<RoutePoint> newPoints,
                            double totalDistanceMeters,
                            int segmentCount,
                            RepositoryCallback<Void> callback) {
        if (firstSequence < 0) {
            fail(callback, new IllegalArgumentException(
                    "firstSequence must not be negative: " + firstSequence));
            return;
        }
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        if (newPoints == null) {
            fail(callback, new IllegalArgumentException("newPoints must not be null"));
            return;
        }
        final List<RoutePoint> points = new ArrayList<>(newPoints);
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = requireSession(sessionId);
                        // The caller numbers the points, because only the caller knows which of
                        // them it has had confirmed. A retry therefore re-offers the same numbers,
                        // and the unique index ignores them instead of duplicating the trace. A
                        // number chosen here from the stored count would advance on every attempt
                        // and append the same points again under fresh sequences.
                        int nextSequence = firstSequence;
                        List<RoutePointEntity> rows = new ArrayList<>(points.size());
                        for (RoutePoint point : points) {
                            rows.add(toEntity(sessionId, nextSequence++, point));
                        }
                        if (!rows.isEmpty()) {
                            routePointDao.insertAll(rows);
                        }
                        stored.distanceMeters = totalDistanceMeters;
                        stored.segmentCount = segmentCount;
                        stored.lastCheckpointWallClockMillis = clock.wallClockMillis();
                        sessionDao.update(stored);
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void saveDestination(String sessionId,
                                Attraction destination,
                                RepositoryCallback<Void> callback) {
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        final Attraction target = destination;
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = requireSession(sessionId);
                        applyDestination(stored, target);
                        sessionDao.update(stored);
                    }
                });
                return null;
            }
        });
    }


    @Override
    public void saveSessionContext(String sessionId,
                                   MovementSnapshot movement,
                                   WeatherSnapshot weather,
                                   RepositoryCallback<Void> callback) {
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        final MovementSnapshot recordedMovement =
                movement == null ? MovementSnapshot.unavailable() : movement;
        final WeatherSnapshot recordedWeather = weather;
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = sessionDao.findById(sessionId);
                        if (stored == null) {
                            // The session was discarded while the capture was in flight. Nothing
                            // to degrade, and inventing a row here would resurrect a walk the user
                            // deliberately threw away.
                            return;
                        }
                        applyMovement(stored, recordedMovement);
                        if (recordedWeather != null) {
                            applyWeather(stored, recordedWeather);
                        }
                        sessionDao.update(stored);
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void finish(SessionSummary summary, RepositoryCallback<Void> callback) {
        if (summary == null || summary.getId() == null) {
            fail(callback, new IllegalArgumentException("summary must carry a session id"));
            return;
        }
        final SessionSummary snapshot = summary;
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = requireSession(snapshot.getId());
                        if (stored.finished) {
                            // Idempotent: a retried finish after a successful save must neither
                            // fail nor move the end time of a session that already ended.
                            return;
                        }
                        applySummary(stored, snapshot);
                        stored.state = SessionState.COMPLETED.name();
                        stored.pauseReason = null;
                        stored.finished = true;
                        stored.endWallClockMillis = clock.wallClockMillis();
                        stored.lastCheckpointWallClockMillis = stored.endWallClockMillis;
                        sessionDao.update(stored);
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void discard(String sessionId, RepositoryCallback<Void> callback) {
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        SessionEntity stored = sessionDao.findById(sessionId);
                        if (stored == null) {
                            // Idempotent for an already-removed id: the caller wanted the session
                            // gone, and it is.
                            return;
                        }
                        if (stored.finished) {
                            throw new IllegalStateException(
                                    "a completed session is history, not a discardable draft: "
                                            + sessionId);
                        }
                        // Route points and visits cascade; independently saved attractions are not
                        // owned by a session and are untouched.
                        sessionDao.deleteById(sessionId);
                    }
                });
                return null;
            }
        });
    }

    @Override
    public void recordVisit(Visit visit, RepositoryCallback<Visit> callback) {
        if (visit == null) {
            fail(callback, new IllegalArgumentException("visit must not be null"));
            return;
        }
        run(callback, new Work<Visit>() {
            @Override
            public Visit perform() {
                final Visit[] recorded = new Visit[1];
                transactions.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        requireSession(visit.getSessionId());
                        // Checked inside the transaction rather than relying only on the unique
                        // index: it turns the common case into a clear typed answer, while the
                        // index below still catches a race this check cannot see.
                        if (visitDao.find(visit.getSessionId(), visit.getAttractionId()) != null) {
                            throw new DuplicateVisitException(
                                    visit.getSessionId(), visit.getAttractionId());
                        }
                        try {
                            visitDao.insert(toEntity(visit));
                        } catch (SQLiteConstraintException conflict) {
                            throw new DuplicateVisitException(
                                    visit.getSessionId(), visit.getAttractionId(), conflict);
                        }
                        recorded[0] = visit;
                    }
                });
                return recorded[0];
            }
        });
    }

    @Override
    public void findUnreadVisit(String sessionId, RepositoryCallback<Visit> callback) {
        if (sessionId == null) {
            fail(callback, new IllegalArgumentException("sessionId must not be null"));
            return;
        }
        run(callback, new Work<Visit>() {
            @Override
            public Visit perform() {
                VisitEntity stored = visitDao.findFirstUnread(sessionId);
                return stored == null ? null : toDomain(stored);
            }
        });
    }

    @Override
    public void findVisit(String sessionId,
                          String attractionId,
                          RepositoryCallback<Visit> callback) {
        if (sessionId == null || attractionId == null) {
            fail(callback, new IllegalArgumentException("sessionId and attractionId are required"));
            return;
        }
        run(callback, new Work<Visit>() {
            @Override
            public Visit perform() {
                VisitEntity stored = visitDao.find(sessionId, attractionId);
                return stored == null ? null : toDomain(stored);
            }
        });
    }

    @Override
    public void markVisitRead(String sessionId,
                              String attractionId,
                              RepositoryCallback<Void> callback) {
        if (sessionId == null || attractionId == null) {
            fail(callback, new IllegalArgumentException("sessionId and attractionId are required"));
            return;
        }
        run(callback, new Work<Void>() {
            @Override
            public Void perform() {
                // No row is not a failure. Reading a story is a presentation fact, and a story
                // opened from content that has no visit behind it simply has nothing to mark.
                visitDao.markRead(sessionId, attractionId);
                return null;
            }
        });
    }

    @Override
    public void findUnfinished(RepositoryCallback<RestoredSession> callback) {
        run(callback, new Work<RestoredSession>() {
            @Override
            public RestoredSession perform() {
                SessionEntity stored = sessionDao.findUnfinished();
                if (stored == null) {
                    return null;
                }
                List<RoutePointEntity> rows = routePointDao.findBySession(stored.id);
                List<RoutePoint> points = new ArrayList<>(rows.size());
                for (RoutePointEntity row : rows) {
                    points.add(toDomain(row));
                }
                // No last sample: the fix before the interruption must not be joined to the first
                // fix after it, and there is no live position until the user resumes.
                RouteSnapshot route = new RouteSnapshot(
                        stored.distanceMeters, points, stored.segmentCount, null);
                return new RestoredSession(toSummary(stored), route, toDestination(stored));
            }
        });
    }

    // --- mapping -----------------------------------------------------------------------------

    private static void applySummary(SessionEntity entity, SessionSummary summary) {
        entity.state = summary.getState() == null ? null : summary.getState().name();
        entity.pauseReason =
                summary.getPauseReason() == null ? null : summary.getPauseReason().name();
        entity.activeDurationMillis = summary.getActiveDurationMillis();
        entity.segment = summary.getSegment();
        // Sticky, and set here so every write path records it. `finish` clears `pause_reason`, so
        // without this the finished row would keep no evidence that it was ever interrupted and
        // the history label would be unrecoverable.
        if (summary.getPauseReason() == PauseReason.PROCESS_INTERRUPTED) {
            entity.interrupted = true;
        }
        if (summary.getStartWallClockMillis() > 0L) {
            entity.startWallClockMillis = summary.getStartWallClockMillis();
        }
    }


    private static void applyMovement(SessionEntity entity, MovementSnapshot movement) {
        MovementState state = movement.getState();
        entity.movementState = state == null ? MovementState.UNAVAILABLE.name() : state.name();
        entity.movementStationaryMillis = movement.getStationaryMillis();
        entity.movementMovingMillis = movement.getMovingMillis();
    }

    private static void applyWeather(SessionEntity entity, WeatherSnapshot weather) {
        entity.weatherAreaKey = weather.getAreaKey();
        WeatherCondition condition = weather.getCondition();
        entity.weatherCondition = condition == null ? null : condition.name();
        entity.weatherTemperatureCelsius = weather.getTemperatureCelsius();
        entity.weatherObservedAtMillis = weather.getObservedAtMillis();
        entity.weatherFetchedAtMillis = weather.getFetchedAtMillis();
    }

    private static void applyDestination(SessionEntity entity, Attraction destination) {
        if (destination == null) {
            entity.destinationId = null;
            entity.destinationName = null;
            entity.destinationCategory = null;
            entity.destinationDescription = null;
            entity.destinationIndoor = false;
            entity.destinationLatitude = 0.0;
            entity.destinationLongitude = 0.0;
            return;
        }
        entity.destinationId = destination.getId();
        entity.destinationName = destination.getName();
        entity.destinationCategory = AttractionCategoryCodec.toCode(destination.getCategory());
        entity.destinationDescription = destination.getDescription();
        entity.destinationIndoor = destination.isIndoor();
        entity.destinationLatitude = destination.getLocation().getLatitude();
        entity.destinationLongitude = destination.getLocation().getLongitude();
    }

    private static Attraction toDestination(SessionEntity entity) {
        if (entity.destinationId == null || entity.destinationId.isEmpty()) {
            return null;
        }
        return new Attraction(
                entity.destinationId,
                entity.destinationName == null ? "" : entity.destinationName,
                AttractionCategoryCodec.fromCode(
                        entity.destinationCategory, AttractionCategory.CULTURAL_SITE),
                entity.destinationDescription == null ? "" : entity.destinationDescription,
                entity.destinationIndoor,
                new GeoPoint(entity.destinationLatitude, entity.destinationLongitude));
    }

    private static SessionSummary toSummary(SessionEntity entity) {
        SessionState state = parseState(entity.state);
        PauseReason reason = parsePauseReason(entity.pauseReason);
        // A summary carries a reason exactly when it is paused. A stored row can disagree — a
        // state this build cannot read is presented as paused, and rows written by an older or
        // newer schema may hold either half — so the pair is made consistent here rather than
        // letting the model's own check turn a recoverable session into a crash on launch.
        if (state == SessionState.PAUSED && reason == null) {
            reason = PauseReason.PROCESS_INTERRUPTED;
        } else if (state != SessionState.PAUSED) {
            reason = null;
        }
        return new SessionSummary(
                entity.id,
                state,
                reason,
                entity.startWallClockMillis,
                entity.activeDurationMillis,
                Math.max(1, entity.segment));
    }

    /**
     * An unreadable state is treated as paused, never as active.
     *
     * <p>A row written by a future version of the app could carry a name this build does not know.
     * Guessing {@code ACTIVE} would start collecting for a session the user never resumed; paused
     * is the state that always requires them to ask.
     */
    private static SessionState parseState(String name) {
        if (name == null) {
            return SessionState.PAUSED;
        }
        try {
            return SessionState.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return SessionState.PAUSED;
        }
    }

    private static PauseReason parsePauseReason(String name) {
        if (name == null) {
            return null;
        }
        try {
            return PauseReason.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return PauseReason.PROCESS_INTERRUPTED;
        }
    }

    private static RoutePointEntity toEntity(String sessionId, int sequence, RoutePoint point) {
        RoutePointEntity entity = new RoutePointEntity();
        entity.sessionId = sessionId;
        entity.sequence = sequence;
        entity.segment = point.getSegment();
        entity.latitude = point.getPosition().getLatitude();
        entity.longitude = point.getPosition().getLongitude();
        entity.accuracyMeters = point.getAccuracyMeters();
        entity.wallClockMillis = point.getWallClockMillis();
        return entity;
    }

    private static RoutePoint toDomain(RoutePointEntity entity) {
        return new RoutePoint(
                new GeoPoint(entity.latitude, entity.longitude),
                entity.accuracyMeters,
                entity.wallClockMillis,
                Math.max(1, entity.segment));
    }

    private static VisitEntity toEntity(Visit visit) {
        VisitEntity entity = new VisitEntity();
        entity.sessionId = visit.getSessionId();
        entity.attractionId = visit.getAttractionId();
        entity.arrivalWallClockMillis = visit.getArrivalWallClockMillis();
        entity.attractionName = visit.getAttractionName();
        entity.story = visit.getStory();
        entity.unread = visit.isUnread();
        return entity;
    }

    private static Visit toDomain(VisitEntity entity) {
        return new Visit(entity.sessionId, entity.attractionId, entity.arrivalWallClockMillis,
                entity.attractionName, entity.story, entity.unread);
    }

    private SessionEntity requireSession(String sessionId) {
        SessionEntity stored = sessionDao.findById(sessionId);
        if (stored == null) {
            throw new IllegalArgumentException("unknown session: " + sessionId);
        }
        return stored;
    }

    // --- threading ---------------------------------------------------------------------------

    /** One unit of database work, run on the I/O executor. */
    private interface Work<T> {
        T perform();
    }

    private <T> void run(RepositoryCallback<T> callback, Work<T> work) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    T result = work.perform();
                    mainExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(result);
                        }
                    });
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
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
