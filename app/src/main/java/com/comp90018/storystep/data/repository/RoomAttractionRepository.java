package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.AttractionDao;
import com.comp90018.storystep.data.local.AttractionEntity;
import com.comp90018.storystep.data.local.LocalAttractionMapper;
import com.comp90018.storystep.domain.geo.NearbyAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Durable {@link AttractionRepository} reading the Room cache.
 *
 * <p>Queries run on the I/O executor and answer on the main executor, matching the
 * {@code RepositoryCallback} contract. Filtering and ordering are delegated to
 * {@link NearbyAttractions} rather than expressed in SQL: the radius test is a great-circle
 * distance, which SQLite cannot compute, and approximating it with a bounding box would change
 * which places appear at the boundary the nearby tests pin down.
 *
 * <p>The remote source of REQ-18 sits in front of this cache later; nothing above this interface
 * changes when it does.
 */
public final class RoomAttractionRepository implements AttractionRepository {

    private final AttractionDao dao;
    private final Clock clock;
    private final Executor ioExecutor;
    private final Executor mainExecutor;
    private final List<Attraction> seed;

    /** Set once the seed has been confirmed present, so later queries skip the check. */
    private volatile boolean seeded;

    public RoomAttractionRepository(AttractionDao dao,
                                    Clock clock,
                                    Executor ioExecutor,
                                    Executor mainExecutor) {
        this(dao, clock, ioExecutor, mainExecutor, SampleAttractions.get());
    }

    public RoomAttractionRepository(AttractionDao dao,
                                    Clock clock,
                                    Executor ioExecutor,
                                    Executor mainExecutor,
                                    List<Attraction> seed) {
        this.dao = requireNonNull(dao, "dao");
        this.clock = requireNonNull(clock, "clock");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
        this.seed = requireNonNull(seed, "seed");
    }

    @Override
    public void loadNearbyAttractions(GeoPoint origin,
                                      double radiusMeters,
                                      RepositoryCallback<List<Attraction>> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureSeeded();
                    List<Attraction> all =
                            LocalAttractionMapper.toDomainList(dao.findAll());
                    List<Attraction> nearby =
                            NearbyAttractions.within(all, origin, radiusMeters);
                    succeed(callback, nearby);
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    @Override
    public void loadAttraction(String attractionId, RepositoryCallback<Attraction> callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }
        ioExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ensureSeeded();
                    AttractionEntity entity = dao.findById(attractionId);
                    if (entity == null) {
                        fail(callback, new AttractionNotFoundException(attractionId));
                        return;
                    }
                    succeed(callback, LocalAttractionMapper.toDomain(entity));
                } catch (RuntimeException error) {
                    fail(callback, error);
                }
            }
        });
    }

    /**
     * Writes the bundled demonstration set once, on the I/O thread that is already running a query.
     *
     * <p>Idempotent twice over: the {@code seeded} flag skips the work within a process, and the
     * insert ignores ids that already exist, so a relaunch — or two queries racing on the two I/O
     * threads — cannot produce duplicate rows. Existing rows are never overwritten, because a row
     * fetched from a real service must not be replaced by the bundled copy of the same place.
     */
    private void ensureSeeded() {
        if (seeded) {
            return;
        }
        synchronized (this) {
            if (seeded) {
                return;
            }
            if (dao.count() == 0) {
                long now = clock.wallClockMillis();
                List<AttractionEntity> rows = LocalAttractionMapper.toEntities(seed);
                for (AttractionEntity row : rows) {
                    row.provenance = AttractionEntity.PROVENANCE_SEED;
                    row.fetchedAtMillis = now;
                }
                dao.insertMissing(rows);
            }
            seeded = true;
        }
    }

    private <T> void succeed(RepositoryCallback<T> callback, T result) {
        mainExecutor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(result);
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
