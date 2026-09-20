package com.comp90018.storystep.data.repository;

import com.comp90018.storystep.data.local.LocalSavedAttractionMapper;
import com.comp90018.storystep.data.local.SavedAttractionDao;
import com.comp90018.storystep.data.local.SavedAttractionEntity;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.SavedAttraction;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;
import com.comp90018.storystep.domain.time.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Durable {@link SavedAttractionRepository} backed by Room.
 *
 * <p>Every method does its work on the I/O executor and answers on the main executor, which is the
 * threading contract {@code RepositoryCallback} documents.
 *
 * <p>The set of saved identifiers is cached in memory and touched only on the main thread, so
 * {@link #addObserver} can answer a newly opened screen immediately instead of leaving a bookmark
 * icon in the wrong state until a query returns. The cache is only ever updated from a write that
 * actually succeeded, so a failed save never makes a screen claim a place is kept when the database
 * says otherwise.
 */
public final class RoomSavedAttractionRepository implements SavedAttractionRepository {

    private final SavedAttractionDao dao;
    private final Clock clock;
    private final Executor ioExecutor;
    private final Executor mainExecutor;

    private final List<Observer> observers = new ArrayList<>(3);

    /** Main-thread only. The identifiers currently believed to be saved. */
    private Set<String> savedIds = Collections.emptySet();

    /** Whether the cache has ever been filled from the database. */
    private boolean idsLoaded;

    /** Guards against a screen-open storm firing one identical query per screen. */
    private boolean idsLoading;

    public RoomSavedAttractionRepository(SavedAttractionDao dao,
                                         Clock clock,
                                         Executor ioExecutor,
                                         Executor mainExecutor) {
        this.dao = requireNonNull(dao, "dao");
        this.clock = requireNonNull(clock, "clock");
        this.ioExecutor = requireNonNull(ioExecutor, "ioExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    // --- reads ---------------------------------------------------------------------------------

    @Override
    public void loadSaved(RepositoryCallback<List<SavedAttraction>> callback) {
        requireNonNull(callback, "callback");
        ioExecutor.execute(() -> {
            try {
                List<SavedAttractionEntity> rows = dao.findAllByRecency();
                List<SavedAttraction> saved = LocalSavedAttractionMapper.toDomainList(rows);
                Set<String> ids = new LinkedHashSet<>();
                for (SavedAttraction record : saved) {
                    ids.add(record.getId());
                }
                mainExecutor.execute(() -> {
                    publishIds(ids);
                    callback.onSuccess(saved);
                });
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    @Override
    public void loadSavedIds(RepositoryCallback<Set<String>> callback) {
        requireNonNull(callback, "callback");
        ioExecutor.execute(() -> {
            try {
                Set<String> ids = new LinkedHashSet<>(dao.findAllIds());
                mainExecutor.execute(() -> {
                    publishIds(ids);
                    callback.onSuccess(Collections.unmodifiableSet(ids));
                });
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    @Override
    public void loadSavedById(String attractionId, RepositoryCallback<SavedAttraction> callback) {
        requireNonNull(callback, "callback");
        if (attractionId == null) {
            fail(callback, new IllegalArgumentException("attractionId must not be null"));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                SavedAttractionEntity row = dao.findById(attractionId);
                SavedAttraction saved =
                        row == null ? null : LocalSavedAttractionMapper.toDomain(row);
                // Deliberately does not touch the identifier cache. One row says nothing about
                // the rest of the table, and publishing from here would narrow the set every
                // screen shares to whatever this single lookup happened to find.
                mainExecutor.execute(() -> callback.onSuccess(saved));
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    // --- writes --------------------------------------------------------------------------------

    @Override
    public void save(Attraction attraction, RepositoryCallback<Boolean> callback) {
        requireNonNull(callback, "callback");
        if (attraction == null) {
            fail(callback, new IllegalArgumentException("attraction must not be null"));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                long rowId = dao.insertIgnoring(
                        LocalSavedAttractionMapper.toEntity(attraction, clock.wallClockMillis()));
                boolean inserted = rowId != -1L;
                mainExecutor.execute(() -> {
                    // Added even when the insert was ignored: the row exists either way, and a
                    // cache that disagreed with the table would show the wrong bookmark.
                    publishIds(plus(savedIds, attraction.getId()));
                    callback.onSuccess(inserted);
                });
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    @Override
    public void unsave(String attractionId, RepositoryCallback<Boolean> callback) {
        requireNonNull(callback, "callback");
        if (attractionId == null) {
            fail(callback, new IllegalArgumentException("attractionId must not be null"));
            return;
        }
        ioExecutor.execute(() -> {
            try {
                boolean removed = dao.deleteById(attractionId) > 0;
                mainExecutor.execute(() -> {
                    publishIds(minus(savedIds, attractionId));
                    callback.onSuccess(removed);
                });
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    @Override
    public void clearAll(RepositoryCallback<Integer> callback) {
        requireNonNull(callback, "callback");
        ioExecutor.execute(() -> {
            try {
                int removed = dao.deleteAll();
                mainExecutor.execute(() -> {
                    publishIds(Collections.<String>emptySet());
                    callback.onSuccess(removed);
                });
            } catch (RuntimeException error) {
                fail(callback, error);
            }
        });
    }

    // --- observers -----------------------------------------------------------------------------

    @Override
    public void addObserver(Observer observer) {
        requireNonNull(observer, "observer");
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
        observer.onSavedIdsChanged(savedIds);
        if (!idsLoaded && !idsLoading) {
            idsLoading = true;
            refreshIds();
        }
    }

    @Override
    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    /**
     * Fills the cache from the database the first time a screen asks for it.
     *
     * <p>A failure here is not surfaced anywhere: the screens are already showing the empty set,
     * which is the honest answer while nothing is known, and the next explicit load reports its own
     * error through its own callback.
     */
    private void refreshIds() {
        ioExecutor.execute(() -> {
            try {
                Set<String> ids = new LinkedHashSet<>(dao.findAllIds());
                mainExecutor.execute(() -> {
                    idsLoading = false;
                    publishIds(ids);
                });
            } catch (RuntimeException ignored) {
                mainExecutor.execute(() -> idsLoading = false);
            }
        });
    }

    /** Replaces the cache and notifies observers, but only when something actually changed. */
    private void publishIds(Set<String> ids) {
        Set<String> updated = Collections.unmodifiableSet(new LinkedHashSet<>(ids));
        boolean changed = !idsLoaded || !updated.equals(savedIds);
        savedIds = updated;
        idsLoaded = true;
        if (!changed) {
            return;
        }
        // Copied before iterating: an observer may remove itself while being notified.
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onSavedIdsChanged(updated);
        }
    }

    private static Set<String> plus(Set<String> ids, String id) {
        Set<String> updated = new LinkedHashSet<>(ids);
        updated.add(id);
        return updated;
    }

    private static Set<String> minus(Set<String> ids, String id) {
        Set<String> updated = new LinkedHashSet<>(ids);
        updated.remove(id);
        return updated;
    }

    private <T> void fail(RepositoryCallback<T> callback, Throwable error) {
        mainExecutor.execute(() -> callback.onError(error));
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
