package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.Objects;

/** The Retrofit-backed hourly forecast: one request, typed failures. */
public final class RetrofitHourlyForecastSource implements ForecastSource<HourlyForecast> {

    private final ForecastApiService service;

    public RetrofitHourlyForecastSource(ForecastApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public HourlyForecast fetch(GeoPoint cellCentre, long fetchedAtMillis) {
        HourlyForecastDto body = ForecastCalls.execute(service.getHourlyForecast(
                cellCentre.getLatitude(),
                cellCentre.getLongitude(),
                ForecastApiService.HOURLY_FIELDS,
                ForecastApiService.DAILY_FIELDS,
                ForecastApiService.FORECAST_HOURS,
                ForecastApiService.FORECAST_DAYS,
                ForecastApiService.TIMEZONE,
                ForecastApiService.TIME_FORMAT));
        HourlyForecast forecast = RemoteForecastMapper.toHourly(body, fetchedAtMillis);
        if (forecast == null) {
            throw new ForecastUnavailableException(ForecastUnavailableException.Reason.MALFORMED);
        }
        return forecast;
    }
}
