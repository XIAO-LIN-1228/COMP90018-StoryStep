package com.comp90018.storystep.platform.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * The barometer, registered only while a walk is running.
 *
 * <p>Most mid-range phones have none, so this is the clearest case of the graceful-degradation
 * rule: no barometer means no climb figure, and everything else about the walk is unchanged.
 * Sampled slowly - a metre of climb takes seconds, and pressure is smoothed anyway.
 */
public final class AndroidPressureSource implements PressureSource, SensorEventListener {

    /** One reading a second is plenty for a figure that moves by metres. */
    private static final int SAMPLING_PERIOD_MICROS = 1_000_000;

    @Nullable private final SensorManager sensorManager;
    @Nullable private final Sensor barometer;
    @Nullable private Listener listener;

    public AndroidPressureSource(Context context) {
        this.sensorManager = ContextCompat.getSystemService(
                context.getApplicationContext(), SensorManager.class);
        this.barometer = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    @Override
    public boolean isAvailable() {
        return barometer != null;
    }

    @Override
    public void startUpdates(Listener listener) {
        if (!isAvailable() || sensorManager == null) {
            return;
        }
        this.listener = listener;
        sensorManager.registerListener(this, barometer, SAMPLING_PERIOD_MICROS);
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
        if (listener != null && event.sensor.getType() == Sensor.TYPE_PRESSURE
                && event.values != null && event.values.length > 0) {
            listener.onPressure(event.values[0]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Barometer accuracy states carry nothing this app can act on.
    }
}
