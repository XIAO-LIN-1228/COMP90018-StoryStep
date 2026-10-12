package com.comp90018.storystep.ui.forecast;

import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.SkyCondition;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

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
     * A departure time that also says its day when it is not on the reference instant's day:
     * "2:00 PM" today, "Tomorrow 7:00 AM" after midnight. Departures span a whole day, so a bare
     * time after midnight reads as one that has already passed.
     */
    public static String dayTime(Resources resources, long millis, long referenceMillis,
                                 ForecastPlace place) {
        return withDay(resources, time(millis, place), millis, referenceMillis, place);
    }

    /**
     * An hour's interval that also says its day when the hour starts on a later day than the
     * reference instant: "11:00 PM–12:00 AM" today, "Tomorrow 12:00 AM–1:00 AM" after midnight.
     * The hourly list spans a whole day, so a bare interval after midnight reads as one already
     * past.
     */
    public static String dayInterval(Resources resources, long startMillis, long endMillis,
                                     long referenceMillis, ForecastPlace place) {
        return withDay(resources,
                unbreakable(interval(resources, startMillis, endMillis, place)),
                startMillis, referenceMillis, place);
    }

    /**
     * Keeps an interval on one line, so a label too wide for the row wraps after its day
     * ("Tomorrow" / "2:00 AM–3:00 AM") rather than leaving "AM" on a line of its own: spaces
     * become no-break spaces and a word joiner follows the dash.
     */
    static String unbreakable(String interval) {
        return interval.replace(' ', ' ').replace("–", "–⁠");
    }

    /** Prefixes {@code text} with "Tomorrow" or a weekday when {@code millis} is on a later day. */
    private static String withDay(Resources resources, String text, long millis,
                                  long referenceMillis, ForecastPlace place) {
        int days = dayOffset(referenceMillis, millis, zone(place));
        if (days == 0) {
            return text;
        }
        if (days == 1) {
            return resources.getString(R.string.forecast_tomorrow_time, text);
        }
        DateFormat weekday = new java.text.SimpleDateFormat("EEE", Locale.ENGLISH);
        weekday.setTimeZone(zone(place));
        return resources.getString(R.string.forecast_day_time,
                weekday.format(new Date(millis)), text);
    }

    /** Calendar days from the reference instant's local date to the instant's, in the zone. */
    static int dayOffset(long referenceMillis, long millis, TimeZone zone) {
        return (int) (localDay(millis, zone) - localDay(referenceMillis, zone));
    }

    private static long localDay(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), 86_400_000L);
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
     *                 icon. Only clear and partly cloudy have night versions: an overcast, foggy
     *                 or wet sky looks the same either way.
     */
    @DrawableRes
    public static int icon(SkyCondition sky, Boolean daylight) {
        if (sky == null) {
            return R.drawable.ic_weather_unknown;
        }
        boolean night = Boolean.FALSE.equals(daylight);
        switch (sky) {
            case CLEAR:
                return night ? R.drawable.ic_weather_clear_night : R.drawable.ic_weather_clear;
            case PARTLY_CLOUDY:
                return night ? R.drawable.ic_weather_partly_cloudy_night
                        : R.drawable.ic_weather_partly_cloudy;
            case OVERCAST:
                return R.drawable.ic_weather_cloudy;
            case FOG:
                return R.drawable.ic_weather_fog;
            case RAIN:
                return R.drawable.ic_weather_rain;
            case SNOW:
                return R.drawable.ic_weather_snow;
            case THUNDERSTORM:
                return R.drawable.ic_weather_thunderstorm;
            case UNKNOWN:
            default:
                return R.drawable.ic_weather_unknown;
        }
    }

    /** Spoken description of the icon. Never "Clear" for a sky that was not reported. */
    @StringRes
    public static int skyDescription(SkyCondition sky) {
        if (sky == null) {
            return R.string.weather_condition_unknown;
        }
        switch (sky) {
            case CLEAR:
                return R.string.weather_clear;
            case PARTLY_CLOUDY:
                return R.string.sky_partly_cloudy;
            case OVERCAST:
                return R.string.sky_overcast;
            case FOG:
                return R.string.sky_fog;
            case RAIN:
                return R.string.weather_rain;
            case SNOW:
                return R.string.weather_snow;
            case THUNDERSTORM:
                return R.string.sky_thunderstorm;
            case UNKNOWN:
            default:
                return R.string.weather_condition_unknown;
        }
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
