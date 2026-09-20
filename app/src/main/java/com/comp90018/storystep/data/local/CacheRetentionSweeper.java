package com.comp90018.storystep.data.local;

import com.comp90018.storystep.domain.privacy.RetentionPolicy;
import com.comp90018.storystep.domain.time.Clock;

/**
 * Removes provider copies the device has kept past the D-06 retention window.
 *
 * <p>Only fetched content is in scope: cached attractions that did not ship with the app, the
 * fetched-area bookkeeping that accounts for them, and cached weather observations. Journeys,
 * route points, visits, saved places and photographs are the user's own record and are removed
 * only when the user asks. See {@code docs/decisions/D-06-data-retention.md}.
 *
 * <p>Synchronous, and the caller owns the thread. It is invoked once per process start on the
 * I/O executor.
 */
public final class CacheRetentionSweeper {

    private final Transactions transactions;
    private final AttractionDao attractions;
    private final AttractionAreaDao areas;
    private final WeatherCacheDao weather;
    private final RetentionPolicy policy;
    private final Clock clock;

    public CacheRetentionSweeper(Transactions transactions,
                                 AttractionDao attractions,
                                 AttractionAreaDao areas,
                                 WeatherCacheDao weather,
                                 RetentionPolicy policy,
                                 Clock clock) {
        this.transactions = requireNonNull(transactions, "transactions");
        this.attractions = requireNonNull(attractions, "attractions");
        this.areas = requireNonNull(areas, "areas");
        this.weather = requireNonNull(weather, "weather");
        this.policy = requireNonNull(policy, "policy");
        this.clock = requireNonNull(clock, "clock");
    }

    /**
     * Removes every expired provider copy.
     *
     * <p>In one transaction, so the records and the area bookkeeping that accounts for them never
     * disagree: an area left claiming it had been fetched while its records were gone would serve
     * an empty list and call it complete.
     *
     * @return how many rows were removed across all three caches
     */
    public int sweep() {
        long cutoff = policy.cutoffFor(clock.wallClockMillis());
        final int[] removed = new int[1];
        transactions.runInTransaction(() -> {
            removed[0] = attractions.deleteFetchedBefore(AttractionEntity.PROVENANCE_SEED, cutoff)
                    + areas.deleteFetchedBefore(cutoff)
                    + weather.deleteFetchedBefore(cutoff);
        });
        return removed[0];
    }

    private static <T> T requireNonNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
