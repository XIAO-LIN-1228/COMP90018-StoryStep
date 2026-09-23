package com.comp90018.storystep.data.remote;

import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodes Google's encoded-polyline format, which Valhalla uses at six decimal places.
 *
 * <p>Pure, and tested against a known shape: a wrong precision or a missed sign shows up as a
 * route drawn in the wrong hemisphere, which is the kind of thing a unit test should catch
 * rather than a person looking at a map.
 */
public final class EncodedPolyline {

    private EncodedPolyline() {
    }

    /** Decodes with the given number of decimal places: six for Valhalla, five for most others. */
    public static List<GeoPoint> decode(String encoded, int precision) {
        List<GeoPoint> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        double factor = Math.pow(10, precision);
        int index = 0;
        int lat = 0;
        int lon = 0;
        while (index < encoded.length()) {
            lat += delta(encoded, index);
            index = next(encoded, index);
            if (index > encoded.length()) {
                break;
            }
            lon += delta(encoded, index);
            index = next(encoded, index);
            points.add(new GeoPoint(lat / factor, lon / factor));
        }
        return points;
    }

    /** One zig-zag encoded value starting at {@code index}. */
    private static int delta(String encoded, int index) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (index >= encoded.length()) {
                return 0;
            }
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        return (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
    }

    /** Where the value starting at {@code index} ends. */
    private static int next(String encoded, int index) {
        int b;
        do {
            if (index >= encoded.length()) {
                return index + 1;
            }
            b = encoded.charAt(index++) - 63;
        } while (b >= 0x20);
        return index;
    }
}
