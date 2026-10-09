package com.comp90018.storystep.ui.forecast;

import android.content.res.Resources;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.ui.HistoryText;
import com.comp90018.storystep.ui.WeatherText;

/** Labels, icons and messages for the forecast and departure pages. */
public final class ForecastText {

    private ForecastText() {
    }

    /** "2:00 PM–3:00 PM", in the phone's timezone. */
    public static String interval(Resources resources, long startMillis, long endMillis) {
        return resources.getString(R.string.forecast_interval,
                HistoryText.formatTime(startMillis), HistoryText.formatTime(endMillis));
    }

    @DrawableRes
    public static int icon(WeatherCondition condition) {
        if (condition == null) {
            return R.drawable.ic_weather_unknown;
        }
        switch (condition) {
            case CLEAR:
                return R.drawable.ic_weather_clear;
            case CLOUDY:
                return R.drawable.ic_weather_cloudy;
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
