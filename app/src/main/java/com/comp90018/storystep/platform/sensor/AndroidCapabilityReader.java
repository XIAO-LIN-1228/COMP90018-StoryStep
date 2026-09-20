package com.comp90018.storystep.platform.sensor;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.LocationManager;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

/** Read-only capability checks. Opening Profile never registers a sensor or requests a fix. */
public final class AndroidCapabilityReader {
    private final Context context;

    public AndroidCapabilityReader(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean preciseLocationGranted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean approximateLocationGranted() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public boolean locationEnabled() {
        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return manager != null && LocationManagerCompat.isLocationEnabled(manager);
    }

    public boolean accelerometerAvailable() { return available(Sensor.TYPE_ACCELEROMETER); }
    public boolean magnetometerAvailable() { return available(Sensor.TYPE_MAGNETIC_FIELD); }

    private boolean available(int type) {
        SensorManager manager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        return manager != null && manager.getDefaultSensor(type) != null;
    }
}
