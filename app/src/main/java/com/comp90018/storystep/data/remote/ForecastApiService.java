package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit contract for Open-Meteo's hourly forecast: the chance of rain and the condition for
 * each of the next hours, as the forecast page lists them (decision D-08).
 *
 * <p>Separate from {@link WeatherApiService} so the current-conditions request, its cache and the
 * home weather card stay exactly as they are.
 *
 * <p>Times are requested as UTC Unix seconds, so no timezone string is parsed. Each value
 * describes the hour that <em>ends</em> at its timestamp.
 */
public interface ForecastApiService {

    /** Root for every call on this interface; Retrofit requires the trailing slash. */
    String BASE_URL = "https://api.open-meteo.com/";

    String HOURLY_FIELDS = "precipitation_probability,weather_code,is_day";

    /**
     * Enough rows for 24 upcoming hours: the first row is the hour that ended at the start of the
     * current hour, and the hour in progress is the second.
     */
    int FORECAST_HOURS = 26;

    String TIMEZONE = "GMT";
    String TIME_FORMAT = "unixtime";

    /**
     * @param latitude  grid-cell centre latitude, never the user's exact position
     * @param longitude grid-cell centre longitude
     */
    @GET("v1/forecast")
    Call<HourlyForecastDto> getHourlyForecast(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("hourly") String hourly,
            @Query("forecast_hours") int forecastHours,
            @Query("timezone") String timezone,
            @Query("timeformat") String timeFormat);
}
