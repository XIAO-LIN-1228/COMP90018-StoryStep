package com.comp90018.storystep.ui;

import android.content.res.Resources;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.ui.weather.WeatherUiState;

/**
 * Formats a {@link WeatherUiState} into the one line the weather card shows.
 *
 * <p>Chooses a localized string and a unit, nothing else: the decision about whether an
 * observation is current lives in {@code WeatherFreshnessPolicy}, and this class only renders the
 * answer.
 *
 * <p>The rule it does enforce is that a stale observation never renders like a fresh one. A
 * temperature with no age beside it is a claim about right now, and that claim has to be earned.
 */
public final class WeatherText {

    private static final long MINUTE_MILLIS = 60L * 1000L;
    private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;

    private WeatherText() {
        // Utility class.
    }

    /**
     * @param nowMillis the current wall clock, so the age is measured rather than assumed
     */
    public static String format(Resources resources, WeatherUiState state, long nowMillis) {
        return format(resources, state, nowMillis, true);
    }

    /**
     * @param withSource whether to name the provider on the line itself. The explore header
     *                   credits Open-Meteo in the sheet's footer instead, so its one line stays
     *                   one line; everywhere else the reading carries its own credit.
     */
    public static String format(Resources resources,
                                WeatherUiState state,
                                long nowMillis,
                                boolean withSource) {
        switch (state.getStatus()) {
            case LOADING:
                return resources.getString(R.string.weather_loading);
            case UNAVAILABLE:
                // Offline with nothing cached is worth naming: the user can act on it, whereas
                // "unavailable" tells them only that something went wrong somewhere.
                return resources.getString(
                        state.getReason() == WeatherUnavailableException.Reason.NO_CACHE
                                ? R.string.weather_offline
                                : R.string.weather_unavailable);
            case CONTENT:
            default:
                return formatContent(resources, state, nowMillis, withSource);
        }
    }

    private static String formatContent(Resources resources,
                                        WeatherUiState state,
                                        long nowMillis,
                                        boolean withSource) {
        String temperature = resources.getString(R.string.weather_temperature,
                Math.round(state.getTemperatureCelsius()));
        String condition = resources.getString(conditionLabel(state.getCondition()));
        String reading = resources.getString(R.string.weather_reading, temperature, condition);
        if (state.isStale()) {
            reading = append(resources, reading,
                    formatAge(resources, state.getObservedAtMillis(), nowMillis));
        }
        if (state.getAreaLabel() == WeatherUiState.AreaLabel.REFERENCE_AREA) {
            // Before a fix arrives this is the demonstration area's weather, and presenting it
            // without that label would be the same mistake as showing reference-area distances as
            // distances from the user.
            reading = append(resources, reading,
                    resources.getString(R.string.weather_reference_area));
        }
        return withSource
                ? append(resources, reading, resources.getString(R.string.weather_source))
                : reading;
    }

    /**
     * A stored observation, labelled with when it was observed.
     *
     * <p>Always labelled, unlike {@link #format}, which labels only stale readings. This one is
     * being shown next to a walk that happened days ago: the temperature is a fact about that
     * afternoon, and presenting it without a time would read as a claim about now.
     */
    public static String formatRecorded(Resources resources, WeatherSnapshot snapshot) {
        String temperature = resources.getString(R.string.weather_temperature,
                Math.round(snapshot.getTemperatureCelsius()));
        String condition = resources.getString(conditionLabel(snapshot.getCondition()));
        String reading = resources.getString(R.string.weather_reading, temperature, condition);
        return append(resources, append(resources, reading, resources.getString(R.string.weather_recorded_at,
                HistoryText.formatTime(snapshot.getObservedAtMillis()))), resources.getString(R.string.weather_source));
    }

    private static String append(Resources resources, String reading, String suffix) {
        return resources.getString(R.string.weather_reading_suffixed, reading, suffix);
    }

    private static String formatAge(Resources resources, long observedAtMillis, long nowMillis) {
        long age = Math.max(0L, nowMillis - observedAtMillis);
        if (age < HOUR_MILLIS) {
            return resources.getString(
                    R.string.weather_age_minutes, (int) (age / MINUTE_MILLIS));
        }
        return resources.getString(R.string.weather_age_hours, (int) (age / HOUR_MILLIS));
    }

    /** The word for a condition; also used by the hourly forecast rows. */
    public static int conditionLabel(WeatherCondition condition) {
        switch (condition) {
            case CLEAR:
                return R.string.weather_clear;
            case CLOUDY:
                return R.string.weather_cloudy;
            case RAIN:
                return R.string.weather_rain;
            case SNOW:
                return R.string.weather_snow;
            case UNKNOWN:
            default:
                // Never "Clear". A condition the app did not recognise is not good weather.
                return R.string.weather_condition_unknown;
        }
    }
}
