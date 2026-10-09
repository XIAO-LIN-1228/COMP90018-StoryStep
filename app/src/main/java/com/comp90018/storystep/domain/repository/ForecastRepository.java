package com.comp90018.storystep.domain.repository;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Forecasts for the forecast page and departure advice (decision D-08).
 *
 * <p>Both calls key by weather area, as current weather does, so the provider is told a grid-cell
 * centre rather than the origin itself. Results arrive on the main thread; failures arrive as
 * {@link ForecastUnavailableException}.
 */
public interface ForecastRepository {

    /** Open-Meteo's own hourly chance of rain and condition for the origin's area. */
    void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback);

    /** Hourly precipitation from every member of the three ensembles, for the origin's area. */
    void ensemble(GeoPoint origin, RepositoryCallback<EnsemblePrecipitation> callback);
}
