package com.comp90018.storystep.domain.repository;

import java.util.Objects;

/**
 * Raised when no attractions can be produced for an area, carrying why.
 *
 * <p>The reason is typed rather than left as a message because the nearby screen acts on it
 * differently: a timeout deserves a retry button, a rate limit deserves an explicit "the map
 * service is busy" rather than a retry loop, and a malformed response deserves a plain
 * "unavailable". Before this existed the screen had one untyped error state that collapsed all of
 * them into the same unhelpful line.
 *
 * <p>Mirrors {@link WeatherUnavailableException}, and for the same reason: these are the same six
 * things that can go wrong on any of this app's network paths, and two different vocabularies for
 * them would mean two different sets of UI states to keep honest.
 *
 * <p>This is only raised when there is nothing to show at all. A failed refresh that still has a
 * presentable cache is a success carrying stale data, not an error.
 */
public class AttractionUnavailableException extends RuntimeException {

    public enum Reason {

        /** The device reports no network, and nothing usable is cached. */
        NO_NETWORK,

        /** The request was made but did not answer within the client timeout. */
        TIMEOUT,

        /** The service answered with an error status. */
        HTTP_ERROR,

        /** The service answered, but the payload was unusable or held no valid record. */
        MALFORMED,

        /** The service asked us to slow down. Back off; do not retry immediately. */
        RATE_LIMITED,

        /** The request never happened and nothing was cached, for example at a cold offline start. */
        NO_CACHE
    }

    private final Reason reason;

    public AttractionUnavailableException(Reason reason) {
        this(reason, null);
    }

    public AttractionUnavailableException(Reason reason, Throwable cause) {
        super("Attractions unavailable: " + reason, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason getReason() {
        return reason;
    }
}
