package com.comp90018.storystep.domain.forecast;

/**
 * What the sky is doing in an hour, for the forecast and departure pages' icons.
 *
 * <p>Finer than the five-way {@code WeatherCondition} the recommendation engine uses, which only
 * asks whether the walker wants to be indoors: here partly cloudy, overcast and fog are told apart,
 * and thunderstorms are not just rain. The mapping follows the WMO weather interpretation codes
 * Open-Meteo reports (decision D-08).
 */
public enum SkyCondition {
    CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN;

    /**
     * <ul>
     *   <li>0 clear; 1 mainly clear and 2 partly cloudy; 3 overcast; 45, 48 fog</li>
     *   <li>51-57 drizzle, 61-67 rain (freezing included), 80-82 showers: rain</li>
     *   <li>71-77 snow and grains, 85-86 snow showers: snow</li>
     *   <li>95-99 thunderstorm, with or without hail</li>
     * </ul>
     * A missing or unrecognised code is {@link #UNKNOWN}, never clear.
     */
    public static SkyCondition fromWmo(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        int value = code;
        if (value == 0) {
            return CLEAR;
        }
        if (value == 1 || value == 2) {
            return PARTLY_CLOUDY;
        }
        if (value == 3) {
            return OVERCAST;
        }
        if (value == 45 || value == 48) {
            return FOG;
        }
        if ((value >= 51 && value <= 67) || (value >= 80 && value <= 82)) {
            return RAIN;
        }
        if ((value >= 71 && value <= 77) || value == 85 || value == 86) {
            return SNOW;
        }
        if (value >= 95 && value <= 99) {
            return THUNDERSTORM;
        }
        return UNKNOWN;
    }
}
