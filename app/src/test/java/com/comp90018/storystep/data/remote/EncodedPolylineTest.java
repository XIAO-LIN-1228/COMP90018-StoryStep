package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.Test;

import java.util.List;

public class EncodedPolylineTest {

    @Test
    public void decodesTheDocumentedFivePlaceExample() {
        // Google's own example: (38.5,-120.2), (40.7,-120.95), (43.252,-126.453).
        List<GeoPoint> points = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5);
        assertEquals(3, points.size());
        assertEquals(38.5, points.get(0).getLatitude(), 0.00001);
        assertEquals(-120.2, points.get(0).getLongitude(), 0.00001);
        assertEquals(43.252, points.get(2).getLatitude(), 0.00001);
        assertEquals(-126.453, points.get(2).getLongitude(), 0.00001);
    }

    @Test
    public void sixPlacesPutsAMelbourneWalkInMelbourne() {
        // The same shape encoded at six places is a tenth of the value at five: decoding it
        // with the wrong precision lands the walk in the wrong hemisphere, which is the bug
        // this test exists to catch.
        List<GeoPoint> points = EncodedPolyline.decode("_p~iF~ps|U_ulLnnqC", 6);
        assertEquals(2, points.size());
        assertEquals(3.85, points.get(0).getLatitude(), 0.0001);
        assertEquals(-12.02, points.get(0).getLongitude(), 0.0001);
    }

    @Test
    public void emptyAndMalformedInputDecodeToNothingRatherThanThrowing() {
        assertTrue(EncodedPolyline.decode(null, 6).isEmpty());
        assertTrue(EncodedPolyline.decode("", 6).isEmpty());
        // A truncated value: the decoder stops rather than running off the end.
        EncodedPolyline.decode("_p~iF~ps|U_ul", 6);
    }
}
