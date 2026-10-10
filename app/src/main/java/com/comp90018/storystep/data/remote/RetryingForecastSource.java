package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.Objects;

/**
 * Adds the shared bounded retry and backoff to a forecast source.
 *
 * @param <T> the domain series produced
 */
public final class RetryingForecastSource<T> implements ForecastSource<T> {

    private final ForecastSource<T> delegate;
    private final NetworkRetryPolicy policy;
    private final Sleeper sleeper;

    public RetryingForecastSource(ForecastSource<T> delegate) {
        this(delegate, new NetworkRetryPolicy(), Sleeper.THREAD);
    }

    public RetryingForecastSource(ForecastSource<T> delegate,
                                  NetworkRetryPolicy policy,
                                  Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public T fetch(GeoPoint cellCentre, long fetchedAtMillis) {
        int attempt = 1;
        while (true) {
            try {
                return delegate.fetch(cellCentre, fetchedAtMillis);
            } catch (ForecastUnavailableException failure) {
                long delayMillis = policy.delayAfter(
                        attempt,
                        toPolicyFailure(failure.getReason()),
                        failure.getHttpStatus(),
                        failure.getRetryAfterMillis());
                if (delayMillis == NetworkRetryPolicy.DO_NOT_RETRY || !waitFor(delayMillis)) {
                    throw failure;
                }
                attempt++;
            }
        }
    }

    /** Unusable data is a content problem, like a malformed body: asking again will not fix it. */
    static NetworkRetryPolicy.Failure toPolicyFailure(ForecastUnavailableException.Reason reason) {
        switch (reason) {
            case NO_NETWORK:
                return NetworkRetryPolicy.Failure.NO_NETWORK;
            case TIMEOUT:
                return NetworkRetryPolicy.Failure.TIMEOUT;
            case HTTP_ERROR:
                return NetworkRetryPolicy.Failure.HTTP_ERROR;
            case RATE_LIMITED:
                return NetworkRetryPolicy.Failure.RATE_LIMITED;
            case MALFORMED:
            case UNUSABLE:
            default:
                return NetworkRetryPolicy.Failure.MALFORMED;
        }
    }

    private boolean waitFor(long delayMillis) {
        try {
            sleeper.sleep(delayMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
