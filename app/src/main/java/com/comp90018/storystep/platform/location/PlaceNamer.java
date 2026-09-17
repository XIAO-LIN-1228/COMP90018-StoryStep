package com.comp90018.storystep.platform.location;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Names where a walk was, after the fact: "Parkville, Australia".
 *
 * <p>Reverse geocoding is the one place the app asks the platform to turn a coordinate into
 * words, and it is asked once, when a walk is published, never while one is recorded. The
 * platform geocoder needs a backend the device may not have; then, or offline, the answer is
 * simply null and the walk is published without a place. Blocking; call from a background
 * executor.
 */
public final class PlaceNamer {

    private PlaceNamer() {
    }

    @Nullable
    public static String name(Context context, @Nullable GeoPoint where) {
        if (where == null || !Geocoder.isPresent()) {
            return null;
        }
        try {
            List<Address> found = new Geocoder(context, Locale.getDefault())
                    .getFromLocation(where.getLatitude(), where.getLongitude(), 1);
            if (found == null || found.isEmpty()) {
                return null;
            }
            Address address = found.get(0);
            String local = address.getSubLocality() != null ? address.getSubLocality()
                    : address.getLocality() != null ? address.getLocality()
                    : address.getSubAdminArea();
            String country = address.getCountryName();
            if (local == null) {
                return country;
            }
            return country == null ? local : local + ", " + country;
        } catch (IOException | IllegalArgumentException unavailable) {
            return null;
        }
    }
}
