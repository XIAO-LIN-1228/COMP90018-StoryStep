package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wire model for the Open-Meteo hourly forecast response.
 *
 * <pre>
 * {
 *   "hourly": {
 *     "time": [1791586800, 1791590400, ...],
 *     "precipitation_probability": [0, 10, ...],
 *     "weather_code": [0, 2, ...],
 *     "is_day": [1, 0, ...]
 *   }
 * }
 * </pre>
 *
 * <p>Every field is nullable, and so is every list element. {@link RemoteForecastMapper} decides
 * what an incomplete record means.
 */
public class HourlyForecastDto {

    @SerializedName("hourly")
    @Nullable
    public Hourly hourly;

    public static class Hourly {

        @SerializedName("time")
        @Nullable
        public List<Long> time;

        @SerializedName("precipitation_probability")
        @Nullable
        public List<Integer> precipitationProbability;

        @SerializedName("weather_code")
        @Nullable
        public List<Integer> weatherCode;

        @SerializedName("is_day")
        @Nullable
        public List<Integer> isDay;
    }
}
