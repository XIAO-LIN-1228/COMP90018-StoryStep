package com.comp90018.storystep.domain.weather;

import com.comp90018.storystep.domain.model.WeatherSnapshot;

/**
 * Decides when a stored observation should be refreshed and when it should stop being shown.
 *
 * <p>Pure and configurable so the boundaries can be tested against a fake clock instead of by
 * waiting. The defaults come from the plan's cache-freshness policy: weather refreshes after
 * thirty minutes.
 *
 * <p>Age is measured from when this app fetched the observation, not from when the provider says
 * it was observed. Those differ — a provider may serve an observation that is already twenty
 * minutes old — and only the fetch time says anything about how hard this app has been asking.
 * The observation time is still carried on the snapshot and shown to the user.
 */
public final class WeatherFreshnessPolicy {

    private static final long DEFAULT_REFRESH_AFTER_MILLIS = 30L * 60L * 1000L;
    private static final long DEFAULT_EXPIRE_AFTER_MILLIS = 6L * 60L * 60L * 1000L;

    private final long refreshAfterMillis;
    private final long expireAfterMillis;

    public WeatherFreshnessPolicy(long refreshAfterMillis, long expireAfterMillis) {
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

    /** Thirty minutes to refresh, six hours to expire. */
    public static WeatherFreshnessPolicy defaultPolicy() {
        return new WeatherFreshnessPolicy(
                DEFAULT_REFRESH_AFTER_MILLIS, DEFAULT_EXPIRE_AFTER_MILLIS);
    }

    /**
     * Classifies a snapshot at the given wall-clock instant.
     *
     * <p>Boundaries are inclusive of freshness: a snapshot exactly at the refresh interval is
     * still {@code FRESH}. A snapshot with a fetch time in the future — a device whose clock moved
     * backwards after the write — is treated as fresh rather than as impossibly old, because the
     * alternative is discarding good data over a clock adjustment.
     */
    public WeatherFreshness classify(WeatherSnapshot snapshot, long nowWallClockMillis) {
        if (snapshot == null) {
            return WeatherFreshness.EXPIRED;
        }
        long age = nowWallClockMillis - snapshot.getFetchedAtMillis();
        if (age <= refreshAfterMillis) {
            return WeatherFreshness.FRESH;
        }
        if (age <= expireAfterMillis) {
            return WeatherFreshness.STALE;
        }
        return WeatherFreshness.EXPIRED;
    }

    /** True when a network refresh should be attempted; a stale snapshot still gets displayed. */
    public boolean shouldRefresh(WeatherSnapshot snapshot, long nowWallClockMillis) {
        return classify(snapshot, nowWallClockMillis) != WeatherFreshness.FRESH;
    }

    public long getRefreshAfterMillis() {
        return refreshAfterMillis;
    }

    public long getExpireAfterMillis() {
        return expireAfterMillis;
    }
}
