package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.google.gson.JsonObject;

import java.util.Objects;

/** The Retrofit-backed ensemble precipitation: one request for all three models, typed failures. */
public final class RetrofitEnsembleSource implements ForecastSource<EnsemblePrecipitation> {

    private final EnsembleApiService service;

    public RetrofitEnsembleSource(EnsembleApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public EnsemblePrecipitation fetch(GeoPoint cellCentre, long fetchedAtMillis) {
        JsonObject body = ForecastCalls.execute(service.getEnsemble(
                cellCentre.getLatitude(),
                cellCentre.getLongitude(),
                EnsembleApiService.HOURLY_FIELDS,
                EnsembleApiService.DAILY_FIELDS,
                EnsembleApiService.MODELS,
                EnsembleApiService.FORECAST_HOURS,
                ForecastApiService.FORECAST_DAYS,
                ForecastApiService.TIMEZONE,
                ForecastApiService.TIME_FORMAT));
        EnsemblePrecipitation ensemble = RemoteForecastMapper.toEnsemble(body, fetchedAtMillis);
        if (ensemble == null) {
            throw new ForecastUnavailableException(ForecastUnavailableException.Reason.MALFORMED);
        }
        return ensemble;
    }
}
