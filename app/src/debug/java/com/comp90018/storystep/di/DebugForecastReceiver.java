package com.comp90018.storystep.di;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Debug build only: stores the forecast scenario chosen over adb. See {@link DebugForecastHooks}. */
public final class DebugForecastReceiver extends BroadcastReceiver {

    private static final String TAG = "DebugForecast";

    @Override
    public void onReceive(Context context, Intent intent) {
        String scenario = intent.getStringExtra(DebugForecastHooks.KEY_SCENARIO);
        if (scenario == null || !ForecastScenarios.isKnown(scenario)) {
            Log.w(TAG, "Unknown scenario " + scenario + "; known: " + ForecastScenarios.NAMES);
            return;
        }
        context.getSharedPreferences(DebugForecastHooks.PREFS, Context.MODE_PRIVATE)
                .edit().putString(DebugForecastHooks.KEY_SCENARIO, scenario).apply();
        Log.i(TAG, "Forecast scenario: " + scenario);
    }
}
