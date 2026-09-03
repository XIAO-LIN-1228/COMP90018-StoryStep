package com.comp90018.storystep.ui.exploration;

import android.os.Handler;
import android.os.Looper;

/**
 * A main-looper repeating tick, used only to redraw the elapsed-time label.
 *
 * <p>Separated from the ViewModel so the dashboard's state logic stays testable on the JVM: a test
 * supplies a ticker it calls by hand instead of waiting a real second per assertion.
 */
final class HandlerTicker implements ExplorationViewModel.Ticker {

    private final Handler handler = new Handler(Looper.getMainLooper());

    private Runnable scheduled;

    @Override
    public void start(Runnable onTick, long intervalMillis) {
        stop();
        scheduled = new Runnable() {
            @Override
            public void run() {
                onTick.run();
                handler.postDelayed(this, intervalMillis);
            }
        };
        handler.postDelayed(scheduled, intervalMillis);
    }

    @Override
    public void stop() {
        if (scheduled != null) {
            handler.removeCallbacks(scheduled);
            scheduled = null;
        }
    }
}
