package com.comp90018.storystep.ui.forecast;

import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.ui.WeatherText;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;

/** Labels, icons and messages for the forecast and departure pages. */
public final class ForecastText {

    private ForecastText() {
    }

    /** "2:00 PM–3:00 PM", in the forecast location's timezone. */
    public static String interval(Resources resources, long startMillis, long endMillis,
                                  ForecastPlace place) {
        return resources.getString(R.string.forecast_interval,
                time(startMillis, place), time(endMillis, place));
    }

    /** "2:00 PM", in the forecast location's timezone. */
    public static String time(long millis, ForecastPlace place) {
        DateFormat format = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH);
        format.setTimeZone(zone(place));
        return format.format(new Date(millis));
    }

    /**
     * The location's zone: its IANA identifier when the device knows it, which follows daylight
     * saving across the day, otherwise the fixed offset the provider reported.
     */
    static TimeZone zone(ForecastPlace place) {
        String id = place.getTimezoneId();
        if (!id.isEmpty()) {
            TimeZone named = TimeZone.getTimeZone(id);
            if (named.getID().equals(id)) {
                return named;
            }
        }
        return new SimpleTimeZone(place.getUtcOffsetSeconds() * 1000, "forecast");
    }

    /**
     * "Times are Melbourne time (UTC+11)" when the phone is set to a different offset from the
     * forecast location right now; null when they agree and no note is needed.
     */
    @Nullable
    public static String zoneNote(Resources resources, ForecastPlace place, long nowMillis,
                                  TimeZone phone) {
        int offset = zone(place).getOffset(nowMillis);
        if (offset == phone.getOffset(nowMillis)) {
            return null;
        }
        String id = place.getTimezoneId();
        String city = id.isEmpty() ? "" : id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        String utc = utcOffset(offset);
        return city.isEmpty()
                ? resources.getString(R.string.forecast_zone_note_offset, utc)
                : resources.getString(R.string.forecast_zone_note, city, utc);
    }

    /** "UTC+11", "UTC+5:30", "UTC-3", "UTC". */
    static String utcOffset(int offsetMillis) {
        int minutes = offsetMillis / 60_000;
        if (minutes == 0) {
            return "UTC";
        }
        String sign = minutes > 0 ? "+" : "-";
        int abs = Math.abs(minutes);
        return abs % 60 == 0
                ? "UTC" + sign + abs / 60
                : String.format(Locale.ROOT, "UTC%s%d:%02d", sign, abs / 60, abs % 60);
    }

    /**
     * @param daylight whether the hour is in daylight; null when unknown, which shows the day
     *                 icon. Only clear and cloudy differ by night.
     */
    @DrawableRes
    public static int icon(WeatherCondition condition, Boolean daylight) {
        if (condition == null) {
            return R.drawable.ic_weather_unknown;
        }
        boolean night = Boolean.FALSE.equals(daylight);
        switch (condition) {
            case CLEAR:
                return night ? R.drawable.ic_weather_clear_night : R.drawable.ic_weather_clear;
            case CLOUDY:
                return night ? R.drawable.ic_weather_cloudy_night : R.drawable.ic_weather_cloudy;
            case RAIN:
                return R.drawable.ic_weather_rain;
            case SNOW:
                return R.drawable.ic_weather_snow;
            case UNKNOWN:
            default:
                return R.drawable.ic_weather_unknown;
        }
    }

    /** Spoken description of the icon; the same words as the weather card. */
    @StringRes
    public static int conditionDescription(WeatherCondition condition) {
        return WeatherText.conditionLabel(
                condition == null ? WeatherCondition.UNKNOWN : condition);
    }

    /** What went wrong, in words a walker can act on. Never a raw exception message. */
    @StringRes
    public static int error(ForecastUnavailableException.Reason reason) {
        if (reason == null) {
            return R.string.forecast_error_generic;
        }
        switch (reason) {
            case NO_NETWORK:
                return R.string.forecast_error_offline;
            case RATE_LIMITED:
                return R.string.forecast_error_busy;
            case UNUSABLE:
                return R.string.advice_error_unusable;
            case TIMEOUT:
            case HTTP_ERROR:
            case MALFORMED:
            default:
                return R.string.forecast_error_generic;
        }
    }
}
