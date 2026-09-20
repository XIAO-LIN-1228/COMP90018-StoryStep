package com.comp90018.storystep.domain.repository;

import java.util.Objects;

/**
 * Raised when no weather observation can be produced, carrying why.
 */
public class WeatherUnavailableException extends RuntimeException {

    public enum Reason {

        /** The device reports no network, and nothing usable is cached. */
        NO_NETWORK,

        /** The request did not answer within the client timeout. */
        TIMEOUT,

        /** The service answered with an error status. */
        HTTP_ERROR,

        /** The service response was unusable. */
        MALFORMED,

        /** The service asked the client to slow down. */
        RATE_LIMITED,

        /** No network request or cached result was available. */
        NO_CACHE
    }

    private final Reason reason;
    private final int httpStatus;
    private final long retryAfterMillis;

    public WeatherUnavailableException(Reason reason) {
        this(reason, null, -1, -1L);
    }

    public WeatherUnavailableException(Reason reason, Throwable cause) {
        this(reason, cause, -1, -1L);
    }

    public WeatherUnavailableException(Reason reason,
                                       Throwable cause,
                                       int httpStatus,
                                       long retryAfterMillis) {
        super("Weather unavailable: " + reason, cause);
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