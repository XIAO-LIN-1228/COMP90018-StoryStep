package com.comp90018.storystep.data.remote;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit contract for Open-Meteo's ensemble API: hourly precipitation from every member of
 * three global ensembles, for departure-window advice (decision D-08).
 *
 * <p>The body is taken as a {@link JsonObject} because member columns are named by member number
 * and model ({@code precipitation_member07_ncep_gefs025}) and their count varies by model, which a
 * fixed DTO cannot bind. {@link RemoteForecastMapper} reads it.
 */
public interface EnsembleApiService {

    /** Root for every call on this interface; Retrofit requires the trailing slash. */
    String BASE_URL = "https://ensemble-api.open-meteo.com/";

    String HOURLY_FIELDS = "precipitation";

    /** Sunrise and sunset, which decide whether a walk stays in daylight. */
    String DAILY_FIELDS = "sunrise,sunset";

    /** ICON-EPS (hourly), ECMWF IFS ENS and NCEP GEFS (both three-hourly). */
    String MODELS = "icon_seamless,ecmwf_ifs025,gfs025";

    /** 24 hours of departures plus an eight-hour walk after the last one, with the first row past. */
    int FORECAST_HOURS = 34;

    /**
     * @param latitude  grid-cell centre latitude, never the user's exact position
     * @param longitude grid-cell centre longitude
     */
    @GET("v1/ensemble")
    Call<JsonObject> getEnsemble(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("hourly") String hourly,
            @Query("daily") String daily,
            @Query("models") String models,
            @Query("forecast_hours") int forecastHours,
            @Query("forecast_days") int forecastDays,
            @Query("timezone") String timezone,
            @Query("timeformat") String timeFormat);
}
