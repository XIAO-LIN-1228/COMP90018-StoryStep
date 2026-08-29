package com.comp90018.storystep.ui;

import android.content.res.Resources;

import com.comp90018.storystep.R;

/**
 * Formats an already-calculated distance for display. The calculation itself lives in
 * {@link com.comp90018.storystep.domain.geo.GeoCalculator}; this class only chooses a unit and a
 * localized string resource.
 */
public final class DistanceText {

    private static final double METERS_IN_KILOMETER = 1000.0;

    private DistanceText() {
        // Utility class.
    }

    public static String format(Resources resources, double distanceMeters) {
        double distance = Math.max(0.0, distanceMeters);
        if (distance < METERS_IN_KILOMETER) {
            return resources.getString(
                    R.string.distance_meters_format, Math.round(distance));
        }
        return resources.getString(
                R.string.distance_kilometers_format, distance / METERS_IN_KILOMETER);
    }
}
