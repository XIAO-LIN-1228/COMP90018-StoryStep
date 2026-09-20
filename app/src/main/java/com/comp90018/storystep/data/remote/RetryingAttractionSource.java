package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import java.util.List;
import java.util.Objects;

/**
 * Adds bounded retry and backoff to attraction network requests.
 */
public final class RetryingAttractionSource implements AttractionSource {

    private final AttractionSource delegate;
    private final NetworkRetryPolicy policy;
    private final Sleeper sleeper;

    public RetryingAttractionSource(AttractionSource delegate) {
        this(delegate, new NetworkRetryPolicy(), Sleeper.THREAD);
    }

    public RetryingAttractionSource(AttractionSource delegate,
                                    NetworkRetryPolicy policy,
                                    Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public List<Attraction> fetchNearby(GeoPoint center,
                                        double radiusMeters,
                                        int limit) {
        int attempt = 1;

        while (true) {
            try {
                return delegate.fetchNearby(center, radiusMeters, limit);
            } catch (AttractionUnavailableException failure) {
                long delayMillis = policy.delayAfter(
                        attempt,
                        NetworkRetryPolicy.Failure.valueOf(
                                failure.getReason().name()),
                        failure.getHttpStatus(),
                        failure.getRetryAfterMillis());

                if (delayMillis == NetworkRetryPolicy.DO_NOT_RETRY
                        || !waitFor(delayMillis)) {
                    throw failure;
                }

                attempt++;
            }
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