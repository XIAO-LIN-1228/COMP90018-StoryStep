package com.comp90018.storystep.platform.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link LocationSource} backed by the platform {@link LocationManager}.
 *
 * <p>The framework API is used deliberately rather than Google Play Services, so the app needs no
 * additional provider dependency and still runs on a device or emulator image without Play
 * Services. Accuracy is therefore a little worse than a fused provider would give; the quality
 * policy in {@code domain.geo} is what protects the calculations, not the provider choice.
 *
 * <p>GPS and network providers are both registered when enabled. The two deliver independently, so
 * downstream filtering must expect interleaved and occasionally out-of-order samples.
 */
public final class AndroidLocationSource implements LocationSource {

    /** Requested interval; the platform treats it as a hint and may deliver more slowly. */
    private static final long UPDATE_INTERVAL_MILLIS = 2_000L;

    /** Zero, so a stationary user keeps receiving fixes; noise is rejected in the domain layer. */
    private static final float MIN_DISTANCE_METERS = 0.0f;

    private final Context applicationContext;
    private final LocationManager locationManager;

    private Listener listener;
    private LocationListener platformListener;

    public AndroidLocationSource(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.locationManager =
                (LocationManager) applicationContext.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isAvailable() {
        return hasAnyLocationPermission() && !enabledProviders().isEmpty();
    }

    @SuppressLint("MissingPermission") // Guarded by hasAnyLocationPermission() immediately above.
    @Override
    public void startUpdates(Listener newListener) {
        if (newListener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        // Replacing a listener must not leave the previous platform registration running.
        stopUpdates();
        this.listener = newListener;

        if (!hasAnyLocationPermission()) {
            newListener.onLocationUnavailable(Reason.PERMISSION_DENIED);
            return;
        }
        List<String> providers = enabledProviders();
        if (providers.isEmpty()) {
            newListener.onLocationUnavailable(Reason.PROVIDER_DISABLED);
            return;
        }

        platformListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                deliver(location);
            }

            @Override
            public void onProviderDisabled(String provider) {
                // Only a problem once no provider at all is left; one of the two going away is
                // normal, for example when a device loses GPS lock indoors.
                if (enabledProviders().isEmpty()) {
                    notifyUnavailable(Reason.PROVIDER_DISABLED);
                }
            }

            @Override
            public void onProviderEnabled(String provider) {
                // Nothing to do: updates resume on their own.
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
                // Deprecated and never called on modern platforms; accuracy is carried per sample.
            }
        };

        for (String provider : providers) {
            locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MILLIS,
                    MIN_DISTANCE_METERS,
                    platformListener,
                    Looper.getMainLooper());
        }

        // Registration succeeded but nothing has arrived yet, so the screen shows "waiting" rather
        // than an empty list. Precise-versus-approximate is reported by the permission layer that
        // made the request, not here, so it cannot overwrite this transient waiting state.
        newListener.onLocationUnavailable(Reason.NO_RECENT_FIX);
    }

    @Override
    public void stopUpdates() {
        if (platformListener != null) {
            locationManager.removeUpdates(platformListener);
            platformListener = null;
        }
        // Cleared after removal so a callback already queued on the main looper finds no listener
        // and is dropped rather than reaching a screen that has stopped.
        listener = null;
    }

    private void deliver(Location location) {
        Listener current = listener;
        if (current == null || location == null) {
            return;
        }
        GeoPoint position;
        try {
            position = new GeoPoint(location.getLatitude(), location.getLongitude());
        } catch (IllegalArgumentException invalidCoordinate) {
            // A provider can emit a non-finite or out-of-range coordinate; drop it rather than
            // letting it reach the calculations.
            return;
        }
        float accuracy = location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
        long monotonicMillis = location.getElapsedRealtimeNanos() / 1_000_000L;
        current.onLocationChanged(new LocationSample(
                position,
                accuracy,
                location.getTime(),
                monotonicMillis,
                LocationOrigin.DEVICE));
    }

    private void notifyUnavailable(Reason reason) {
        Listener current = listener;
        if (current != null) {
            current.onLocationUnavailable(reason);
        }
    }

    private List<String> enabledProviders() {
        List<String> enabled = new ArrayList<>(2);
        if (locationManager == null) {
            return enabled;
        }
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    enabled.add(provider);
                }
            } catch (IllegalArgumentException | SecurityException unavailable) {
                // The device does not expose this provider; treat it as simply not enabled.
            }
        }
        return enabled;
    }

    private boolean hasAnyLocationPermission() {
        return hasPreciseLocationPermission() || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean hasPreciseLocationPermission() {
        return isGranted(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private boolean isGranted(String permission) {
        return ContextCompat.checkSelfPermission(applicationContext, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}