package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

/**
 * One blocking forecast fetch, of either series.
 *
 * <p>Like {@link WeatherSource}: no Retrofit type, so the cache above it is tested against a fake,
 * and every failure arrives as a {@link ForecastUnavailableException} with a reason the UI can
 * act on. Implementations block and are called on an I/O executor.
 *
 * @param <T> the domain series produced
 */
public interface ForecastSource<T> {

    /**
     * @param cellCentre      the weather-area centre to ask about, never the user's position
     * @param fetchedAtMillis the wall clock to stamp the result with
     */
    T fetch(GeoPoint cellCentre, long fetchedAtMillis) throws ForecastUnavailableException;
}
