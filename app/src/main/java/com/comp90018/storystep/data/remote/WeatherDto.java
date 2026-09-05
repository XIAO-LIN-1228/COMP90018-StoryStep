package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/**
 * Wire model for the Open-Meteo current-conditions response.
 *
 * <p>Shape of the payload this app requests:
 *
 * <pre>
 * {
 *   "latitude": -37.8,
 *   "longitude": 144.95,
 *   "current": {
 *     "time": "2026-09-05T04:00",
 *     "interval": 900,
 *     "temperature_2m": 14.3,
 *     "weather_code": 61
 *   },
 *   "current_units": { "temperature_2m": "°C" }
 * }
 * </pre>
 *
 * <p>Every field is nullable, including the nested block. A provider is free to omit anything, and
 * {@link RemoteWeatherMapper} — not this class and not Gson — decides what an incomplete record
 * means. Gson leaves absent fields null and does not run constructors, so no default written here
 * would be reliable anyway.
 */
public class WeatherDto {

    @SerializedName("latitude")
    @Nullable
    public Double latitude;

    @SerializedName("longitude")
    @Nullable
    public Double longitude;

    @SerializedName("current")
    @Nullable
    public Current current;

    @SerializedName("current_units")
    @Nullable
    public CurrentUnits currentUnits;

    /** The observation itself. */
    public static class Current {

        /**
         * Provider-local observation time, ISO-8601 without an offset, for example
         * {@code 2026-09-05T04:00}. Requested with {@code timezone=auto}, so it is local to the
         * queried coordinates; {@link RemoteWeatherMapper} pairs it with {@link #utcOffsetSeconds}
         * to recover an instant.
         */
        @SerializedName("time")
        @Nullable
        public String time;

        @SerializedName("temperature_2m")
        @Nullable
        public Double temperatureCelsius;

        /** WMO weather interpretation code. See {@link RemoteWeatherMapper} for the mapping. */
        @SerializedName("weather_code")
        @Nullable
        public Integer weatherCode;
    }

    /**
     * Units the provider actually used.
     *
     * <p>The request asks for Celsius, but the answer is checked rather than assumed: silently
     * treating a Fahrenheit number as Celsius is exactly the kind of unit error that renders as a
     * plausible-looking temperature and is never noticed.
     */
    public static class CurrentUnits {

        @SerializedName("temperature_2m")
        @Nullable
        public String temperature;
    }

    /** Seconds to add to the local {@link Current#time} to reach UTC's opposite; see the mapper. */
    @SerializedName("utc_offset_seconds")
    @Nullable
    public Integer utcOffsetSeconds;
}
