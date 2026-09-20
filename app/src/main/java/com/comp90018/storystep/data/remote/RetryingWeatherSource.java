package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

import java.util.Objects;

/**
 * Adds bounded retry and backoff to weather network requests.
 */
public final class RetryingWeatherSource implements WeatherSource {

    private final WeatherSource delegate;
    private final NetworkRetryPolicy policy;
    private final Sleeper sleeper;

    public RetryingWeatherSource(WeatherSource delegate) {
        this(delegate, new NetworkRetryPolicy(), Sleeper.THREAD);
    }

    public RetryingWeatherSource(WeatherSource delegate,
                                 NetworkRetryPolicy policy,
                                 Sleeper sleeper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public WeatherSnapshot fetch(GeoPoint center,
                                 String areaKey,
                                 long fetchedAtMillis) {
        int attempt = 1;

        while (true) {
            try {
                return delegate.fetch(center, areaKey, fetchedAtMillis);
            } catch (WeatherUnavailableException failure) {
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