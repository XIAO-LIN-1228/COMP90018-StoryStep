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
 * <p>Times are requested as UTC Unix seconds, so no date string is parsed. Each value describes
 * the hour that <em>ends</em> at its timestamp. {@code timezone=auto} makes the provider report
 * the location's own zone, which the page shows times in, and the daily sunrise and sunset decide
 * the day and night icons.
 */
public interface ForecastApiService {

    /** Root for every call on this interface; Retrofit requires the trailing slash. */
    String BASE_URL = "https://api.open-meteo.com/";

    /** Gusts, mean wind and feels-like feed the severe-weather flags (D-08 §4.6). */
    String HOURLY_FIELDS = "precipitation_probability,weather_code,wind_gusts_10m,"
            + "wind_speed_10m,apparent_temperature";

    String DAILY_FIELDS = "sunrise,sunset";

    /** Today plus two: enough sunrises and sunsets to cover 34 hours from any time of day. */
    int FORECAST_DAYS = 3;

    /**
     * The page lists 24 upcoming hours; departure advice also reads this forecast for severe
     * weather, so it reaches as far as the ensemble: a day of departures plus an eight-hour walk.
     */
    int FORECAST_HOURS = 34;

    /** The location's own timezone, reported back as {@code timezone} and {@code utc_offset_seconds}. */
    String TIMEZONE = "auto";
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
            @Query("daily") String daily,
            @Query("forecast_hours") int forecastHours,
            @Query("forecast_days") int forecastDays,
            @Query("timezone") String timezone,
            @Query("timeformat") String timeFormat);
}
