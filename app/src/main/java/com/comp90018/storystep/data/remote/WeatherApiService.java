package com.comp90018.storystep.data.remote;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit contract for Open-Meteo's current-conditions endpoint.
 *
 * <p>Open-Meteo was selected for the weather half of decision D-02: it is HTTPS, free for
 * non-commercial use and needs no API key and no account. That last point is not convenience — it
 * removes the entire class of credential-handling risk this project would otherwise have to
 * manage, and it means the repository can be checked out and run by a reviewer with no setup.
 *
 * <p>Attribution and terms: <a href="https://open-meteo.com/en/license">open-meteo.com</a>.
 *
 * <p>The coordinates sent here are a {@code WeatherAreaBucket} centre, never the user's exact
 * position.
 */
public interface WeatherApiService {

    /** Root for every call on this interface; Retrofit requires the trailing slash. */
    String BASE_URL = "https://api.open-meteo.com/";

    /**
     * The fields this app asks for. Requested explicitly rather than taking the endpoint default,
     * so the response stays small and the parsed shape cannot change under us.
     */
    String CURRENT_FIELDS = "temperature_2m,weather_code";

    /**
     * @param latitude    grid-cell centre latitude
     * @param longitude   grid-cell centre longitude
     * @param current     comma-separated field list; pass {@link #CURRENT_FIELDS}
     * @param temperature unit for {@code temperature_2m}; this app always asks for {@code celsius}
     * @param timezone    {@code auto} resolves the zone from the coordinates, which is what makes
     *                    the returned observation time interpretable
     */
    @GET("v1/forecast")
    Call<WeatherDto> getCurrentWeather(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude,
            @Query("current") String current,
            @Query("temperature_unit") String temperature,
            @Query("timezone") String timezone);
}
