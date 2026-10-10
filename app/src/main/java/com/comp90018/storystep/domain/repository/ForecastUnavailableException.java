package com.comp90018.storystep.domain.repository;

import java.util.Objects;

/**
 * Raised when no usable forecast can be produced, carrying why.
 *
 * <p>The first five reasons share their names with {@link WeatherUnavailableException.Reason}
 * and the network retry policy's failures, so a retrying source maps them the same way.
 */
public class ForecastUnavailableException extends RuntimeException {

    public enum Reason {

        /** The device reports no network. */
        NO_NETWORK,

        /** The request did not answer within the client timeout. */
        TIMEOUT,

        /** The service answered with an error status. */
        HTTP_ERROR,

        /** The service asked the client to slow down (HTTP 429). */
        RATE_LIMITED,

        /** The response could not be parsed into a consistent hourly series. */
        MALFORMED,

        /**
         * The response parsed but cannot support advice: too few ensemble members, missing
         * values in the hours needed, or not enough hours for any departure. Not retried.
         */
        UNUSABLE
    }

    private final Reason reason;
    private final int httpStatus;
    private final long retryAfterMillis;

    public ForecastUnavailableException(Reason reason) {
        this(reason, null, -1, -1L);
    }

    public ForecastUnavailableException(Reason reason, Throwable cause) {
        this(reason, cause, -1, -1L);
    }

    public ForecastUnavailableException(Reason reason,
                                        Throwable cause,
                                        int httpStatus,
                                        long retryAfterMillis) {
        super("Forecast unavailable: " + reason, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
        this.httpStatus = httpStatus;
        this.retryAfterMillis = retryAfterMillis;
    }

    public Reason getReason() {
        return reason;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
