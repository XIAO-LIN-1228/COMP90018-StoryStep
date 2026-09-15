package com.comp90018.storystep.domain.attraction;

/**
 * Decides when a cached area should be refetched and when it should stop being served.
 *
 * <p>Pure and configurable so the boundaries can be tested against a fake clock instead of by
 * waiting. The refresh interval comes from the plan's cache-freshness policy: attractions refresh
 * after twenty-four hours.
 *
 * <p>The expiry is set far out at thirty days on purpose. Expiry here does not mean "throw the
 * data away" — nothing is deleted — it means "do not answer with this without going to the network
 * first". Attractions barely change, and a user who has been offline for a fortnight is much
 * better served by a fortnight-old list than by an empty screen, so the point at which the cache
 * stops being an acceptable answer sits a long way past the point at which it is worth refreshing.
 */
public final class AttractionFreshnessPolicy {

    private static final long DEFAULT_REFRESH_AFTER_MILLIS = 24L * 60L * 60L * 1000L;
    private static final long DEFAULT_EXPIRE_AFTER_MILLIS = 30L * 24L * 60L * 60L * 1000L;

    private final long refreshAfterMillis;
    private final long expireAfterMillis;

    public AttractionFreshnessPolicy(long refreshAfterMillis, long expireAfterMillis) {
        if (refreshAfterMillis <= 0L) {
            throw new IllegalArgumentException(
                    "refreshAfterMillis must be positive: " + refreshAfterMillis);
        }
        if (expireAfterMillis < refreshAfterMillis) {
            throw new IllegalArgumentException(
                    "expireAfterMillis must not precede refreshAfterMillis: "
                            + expireAfterMillis + " < " + refreshAfterMillis);
        }
        this.refreshAfterMillis = refreshAfterMillis;
        this.expireAfterMillis = expireAfterMillis;
    }

    /** Twenty-four hours to refresh, thirty days to expire. */
    public static AttractionFreshnessPolicy defaultPolicy() {
        return new AttractionFreshnessPolicy(
                DEFAULT_REFRESH_AFTER_MILLIS, DEFAULT_EXPIRE_AFTER_MILLIS);
    }

    /**
     * Classifies an area fetched at {@code fetchedAtMillis}, as of {@code nowWallClockMillis}.
     *
     * <p>Boundaries are inclusive of freshness: an area fetched exactly one refresh interval ago
     * is still {@code FRESH}. A fetch time in the future — a device whose clock moved backwards
     * after the write — is treated as fresh rather than as impossibly old, because the alternative
     * is discarding good data over a clock adjustment.
     */
    public AttractionFreshness classify(long fetchedAtMillis, long nowWallClockMillis) {
        long age = nowWallClockMillis - fetchedAtMillis;
        if (age <= refreshAfterMillis) {
            return AttractionFreshness.FRESH;
        }
        if (age <= expireAfterMillis) {
            return AttractionFreshness.STALE;
        }
        return AttractionFreshness.EXPIRED;
    }

    /** True when a network refresh should be attempted; a stale area is still served meanwhile. */
    public boolean shouldRefresh(long fetchedAtMillis, long nowWallClockMillis) {
        return classify(fetchedAtMillis, nowWallClockMillis) != AttractionFreshness.FRESH;
    }

    public long getRefreshAfterMillis() {
        return refreshAfterMillis;
    }

    public long getExpireAfterMillis() {
        return expireAfterMillis;
    }
}
