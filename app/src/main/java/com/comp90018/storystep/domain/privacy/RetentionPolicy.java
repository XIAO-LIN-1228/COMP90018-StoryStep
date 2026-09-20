package com.comp90018.storystep.domain.privacy;

/**
 * How long copies of provider data are kept on the device.
 *
 * <p>This policy covers fetched content only — cached attractions, fetched-area bookkeeping and
 * weather observations. The user's own record (journeys, route points, visits, saved places and
 * photographs) is never removed on a timer; only an explicit user action removes it. See
 * {@code docs/decisions/D-06-data-retention.md}.
 *
 * <p>Pure and configurable, so the boundary can be tested against a fake clock rather than by
 * waiting thirty days. Same shape as the freshness policies it sits beside, but a different
 * question: freshness decides what to show, retention decides what to keep.
 */
public final class RetentionPolicy {

    private static final long DEFAULT_KEEP_FOR_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    private final long keepForMillis;

    public RetentionPolicy(long keepForMillis) {
        if (keepForMillis <= 0L) {
            throw new IllegalArgumentException("keepForMillis must be positive: " + keepForMillis);
        }
        this.keepForMillis = keepForMillis;
    }

    /** Thirty days, the D-06 proposal. */
    public static RetentionPolicy defaultPolicy() {
        return new RetentionPolicy(DEFAULT_KEEP_FOR_MILLIS);
    }

    /**
     * The fetch time at or before which a cached row is no longer kept.
     *
     * <p>A row fetched exactly at the cutoff is kept: the window is "older than", not "at least
     * as old as", so a row does not disappear on the exact millisecond it turns thirty days old.
     * Callers delete strictly below this value.
     *
     * <p>A negative result — a device whose clock sits before the epoch plus the window — is
     * clamped to zero rather than allowed to wrap into deleting nothing or everything.
     */
    public long cutoffFor(long nowMillis) {
        long cutoff = nowMillis - keepForMillis;
        return cutoff < 0L ? 0L : cutoff;
    }

    public long getKeepForMillis() {
        return keepForMillis;
    }
}
