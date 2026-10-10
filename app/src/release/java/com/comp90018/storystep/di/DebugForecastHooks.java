package com.comp90018.storystep.di;

import android.content.Context;

import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.time.Clock;

/**
 * Release build: no forecast scenarios. The debug build's class of the same name can replace the
 * departure-advice ensemble with simulated weather for demonstration and manual checks.
 */
final class DebugForecastHooks {

    private DebugForecastHooks() {
    }

    static ForecastRepository wrap(Context context, ForecastRepository real, Clock clock) {
        return real;
    }
}
