package com.comp90018.storystep.di;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;

/**
 * Debug build only: lets departure advice run on simulated weather, so its behaviour in rain can
 * be seen on a dry day. Off unless a scenario is chosen; the release build has no such hook.
 *
 * <p>Choose a scenario (then reopen "Best time to leave"):
 *
 * <pre>
 * adb shell am broadcast -n com.comp90018.storystep/.di.DebugForecastReceiver --es scenario afternoon_showers
 * </pre>
 *
 * and turn it off with {@code --es scenario off}. Scenarios are listed in
 * {@link ForecastScenarios}. The ensemble and the hourly forecast are both simulated, so the
 * rain forecast page and the departure icons agree with the departure chances.
 */
final class DebugForecastHooks {

    static final String PREFS = "debug_forecast";
    static final String KEY_SCENARIO = "scenario";

    private DebugForecastHooks() {
    }

    static ForecastRepository wrap(Context context, ForecastRepository real, Clock clock) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Handler main = new Handler(Looper.getMainLooper());
        return new ForecastRepository() {
            @Override
            public void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback) {
                String scenario = prefs.getString(KEY_SCENARIO, ForecastScenarios.OFF);
                HourlyForecast simulated =
                        ForecastScenarios.hourly(scenario, clock.wallClockMillis());
                if (simulated == null) {
                    real.hourly(origin, callback);
                } else {
                    main.post(() -> callback.onSuccess(simulated));
                }
            }

            @Override
            public void ensemble(GeoPoint origin,
                                 RepositoryCallback<EnsemblePrecipitation> callback) {
                String scenario = prefs.getString(KEY_SCENARIO, ForecastScenarios.OFF);
                EnsemblePrecipitation simulated =
                        ForecastScenarios.build(scenario, clock.wallClockMillis());
                if (simulated == null) {
                    real.ensemble(origin, callback);
                } else {
                    main.post(() -> callback.onSuccess(simulated));
                }
            }
        };
    }
}
