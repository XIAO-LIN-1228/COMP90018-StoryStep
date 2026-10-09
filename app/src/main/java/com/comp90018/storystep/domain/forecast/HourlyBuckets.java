package com.comp90018.storystep.domain.forecast;

/**
 * Validation and overlap rules shared by both hourly series.
 *
 * <p>Open-Meteo stamps each hourly value at the <em>end</em> of the hour it describes: the value
 * at 14:00 covers 13:00 to 14:00. Getting that backwards shifts every figure by an hour, so the
 * rule lives here once rather than in each caller.
 */
final class HourlyBuckets {

    static final long HOUR_SECONDS = 3_600L;
    static final long HOUR_MILLIS = HOUR_SECONDS * 1_000L;

    private HourlyBuckets() {
    }

    /** Rejects an empty series, or one that is not strictly one hour apart. */
    static void requireHourly(long[] endEpochSeconds) {
        if (endEpochSeconds.length == 0) {
            throw new IllegalArgumentException("no hours");
        }
        for (int i = 1; i < endEpochSeconds.length; i++) {
            if (endEpochSeconds[i] - endEpochSeconds[i - 1] != HOUR_SECONDS) {
                throw new IllegalArgumentException("hours are not consecutive at index " + i);
            }
        }
    }

    static long startMillis(long endEpochSecond) {
        return (endEpochSecond - HOUR_SECONDS) * 1_000L;
    }

    static long endMillis(long endEpochSecond) {
        return endEpochSecond * 1_000L;
    }

    /**
     * Whether the hour ending at {@code endEpochSecond} shares any time with the trip
     * {@code [tripStartMillis, tripEndMillis)}. Touching at an edge is not overlap, so a one-hour
     * walk from 14:00 covers exactly the 14:00-15:00 hour.
     */
    static boolean overlaps(long endEpochSecond, long tripStartMillis, long tripEndMillis) {
        return startMillis(endEpochSecond) < tripEndMillis
                && endMillis(endEpochSecond) > tripStartMillis;
    }

    /**
     * Which native block of {@code blockHours} hours the hour ending at {@code endEpochSecond}
     * belongs to. Blocks end on multiples of {@code blockHours} UTC hours (00:00, 03:00, ...), as
     * three-hourly ensemble output does, so the hours ending 01:00, 02:00 and 03:00 share a block.
     */
    static long block(long endEpochSecond, int blockHours) {
        return Math.floorDiv(endEpochSecond / HOUR_SECONDS - 1, blockHours);
    }

    /** Start of a block, in milliseconds. */
    static long blockStartMillis(long block, int blockHours) {
        return block * blockHours * HOUR_MILLIS;
    }

    /** End of a block, in milliseconds. */
    static long blockEndMillis(long block, int blockHours) {
        return (block + 1) * blockHours * HOUR_MILLIS;
    }
}
