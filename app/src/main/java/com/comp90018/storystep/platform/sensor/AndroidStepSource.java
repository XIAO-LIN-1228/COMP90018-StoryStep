package com.comp90018.storystep.platform.sensor;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * The platform step counter, registered only while a walk is running.
 *
 * <p>{@code TYPE_STEP_COUNTER} is a low-power hardware counter: it costs almost nothing to
 * listen to and reports on its own schedule rather than at a sampling rate. From API 29 it is
 * behind {@code ACTIVITY_RECOGNITION}; without that permission it simply reports unavailable,
 * and the walk goes on without a step count, which is what every other sensor here does too.
 */
public final class AndroidStepSource implements StepSource, SensorEventListener {

    private final Context context;
    @Nullable private final SensorManager sensorManager;
    @Nullable private final Sensor counter;
    @Nullable private Listener listener;

    public AndroidStepSource(Context context) {
        this.context = context.getApplicationContext();
        this.sensorManager = ContextCompat.getSystemService(this.context, SensorManager.class);
        this.counter = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    @Override
    public boolean isAvailable() {
        return counter != null && permitted();
    }

    private boolean permitted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return true;
        }
        return ContextCompat.checkSelfPermission(context,
                "android.permission.ACTIVITY_RECOGNITION") == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void startUpdates(Listener listener) {
        if (!isAvailable() || sensorManager == null) {
            return;
        }
        this.listener = listener;
        sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void stopUpdates() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        listener = null;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener != null && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER
                && event.values != null && event.values.length > 0) {
            listener.onStepCount((long) event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // The counter has no useful accuracy states.
    }
}
