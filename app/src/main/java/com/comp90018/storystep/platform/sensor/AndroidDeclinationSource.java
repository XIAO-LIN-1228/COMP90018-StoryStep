package com.comp90018.storystep.platform.sensor;

import android.hardware.GeomagneticField;

import com.comp90018.storystep.domain.model.GeoPoint;

/**
 * Declination from the platform's world magnetic model.
 *
 * <p>Altitude is passed as zero. Declination varies with height far too slowly for the difference
 * to matter at walking altitudes — well under a degree over any elevation a pedestrian reaches —
 * and the app has no reliable altitude to offer anyway. The approximation is recorded here rather
 * than hidden because the heading accuracy claims in the verification checklist depend on it.
 */
public final class AndroidDeclinationSource implements DeclinationSource {

    private static final float SEA_LEVEL_METERS = 0.0f;

    @Override
    public float declinationDegrees(GeoPoint at, long wallClockMillis) {
        GeomagneticField field = new GeomagneticField(
                (float) at.getLatitude(),
                (float) at.getLongitude(),
                SEA_LEVEL_METERS,
                wallClockMillis);
        return field.getDeclination();
    }
}
