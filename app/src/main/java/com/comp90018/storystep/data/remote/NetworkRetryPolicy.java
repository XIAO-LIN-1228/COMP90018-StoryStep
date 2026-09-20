package com.comp90018.storystep.data.remote;

/**
 * Bounded retry and backoff rules shared by attraction and weather requests.
 */
public final class NetworkRetryPolicy {

    public enum Failure {
        NO_NETWORK,
        TIMEOUT,
        HTTP_ERROR,
        MALFORMED,
        RATE_LIMITED,
        NO_CACHE
    }

    public static final long DO_NOT_RETRY = -1L;

    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_DELAY_MILLIS = 500L;
    private static final long DEFAULT_RATE_LIMIT_DELAY_MILLIS = 2_000L;
    private static final long MIN_RATE_LIMIT_DELAY_MILLIS = 1_000L;
    private static final long MAX_RATE_LIMIT_DELAY_MILLIS = 5_000L;

    /**
     * Returns the delay before the next request, or DO_NOT_RETRY.
     *
     * @param failedAttempt one-based number of the request that just failed
     */
    public long delayAfter(int failedAttempt,
                           Failure failure,
                           int httpStatus,
                           long retryAfterMillis) {
        if (failedAttempt >= MAX_ATTEMPTS) {
            return DO_NOT_RETRY;
        }

        switch (failure) {
            case TIMEOUT:
            case NO_NETWORK:
                return BASE_DELAY_MILLIS << (failedAttempt - 1);

            case HTTP_ERROR:
                return httpStatus >= 500 && httpStatus <= 599
                        ? BASE_DELAY_MILLIS << (failedAttempt - 1)
                        : DO_NOT_RETRY;

            case RATE_LIMITED:
                if (failedAttempt > 1) {
                    return DO_NOT_RETRY;
                }

                long requested = retryAfterMillis >= 0L
                        ? retryAfterMillis
                        : DEFAULT_RATE_LIMIT_DELAY_MILLIS;

                return Math.min(
                        MAX_RATE_LIMIT_DELAY_MILLIS,
                        Math.max(MIN_RATE_LIMIT_DELAY_MILLIS, requested));

            case MALFORMED:
            case NO_CACHE:
            default:
                return DO_NOT_RETRY;
        }
    }
}