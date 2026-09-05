package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;

/**
 * One blocking weather fetch.
 *
 * <p>Deliberately narrower than {@link WeatherApiService}: no Retrofit type, no {@code Call}, no
 * DTO. That is what lets the caching repository be tested against a fake that simply returns a
 * snapshot or throws — the whole cache-first, coalescing, stale-fallback policy is exercised in
 * JVM tests with no HTTP stack involved. {@link RetrofitWeatherSource} is then the only class that
 * has to be tested against a real socket.
 *
 * <p>Implementations block the calling thread and are invoked on an I/O executor. Every failure
 * arrives as a {@link WeatherUnavailableException} carrying a reason the UI can act on; nothing
 * else escapes.
 */
public interface WeatherSource {

    /**
     * @param center          the grid-cell centre to ask about, never the user's exact position
     * @param areaKey         the cache key this observation will be stored under
     * @param fetchedAtMillis the wall clock to stamp the result with
     * @return a validated snapshot
     * @throws WeatherUnavailableException with a typed reason when no usable observation resulted
     */
    WeatherSnapshot fetch(GeoPoint center, String areaKey, long fetchedAtMillis)
            throws WeatherUnavailableException;
}
