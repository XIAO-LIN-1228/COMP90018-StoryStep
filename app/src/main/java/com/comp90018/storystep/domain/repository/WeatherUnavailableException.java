package com.comp90018.storystep.domain.repository;

import java.util.Objects;

/**
 * Raised when no weather observation can be produced, carrying why.
 *
 * <p>The reason is typed rather than left as a message because the screens act on it differently:
 * a timeout deserves a retry button, a malformed response deserves a plain "unavailable", and a
 * configuration error must not be retried in a loop. A single untyped failure would collapse all
 * of those into one unhelpful state.
 *
 * <p>This is only raised when there is nothing to show at all. A failed refresh that still has a
 * usable cached observation is a success carrying stale data, not an error.
 */
public class WeatherUnavailableException extends RuntimeException {

    public enum Reason {

        /** The device reports no network, and nothing usable is cached. */
        NO_NETWORK,

        /** The request was made but did not answer within the client timeout. */
        TIMEOUT,

        /** The service answered with an error status. */
        HTTP_ERROR,

        /** The service answered, but the payload was missing required fields or unusable. */
        MALFORMED,

        /** The service asked us to slow down. Back off; do not retry immediately. */
        RATE_LIMITED,

        /** The request never happened and nothing was cached, for example at a cold offline start. */
        NO_CACHE
    }

    private final Reason reason;

    public WeatherUnavailableException(Reason reason) {
        this(reason, null);
    }

    public WeatherUnavailableException(Reason reason, Throwable cause) {
        super("Weather unavailable: " + reason, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason getReason() {
        return reason;
    }
}
