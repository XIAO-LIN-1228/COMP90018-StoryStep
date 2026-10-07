package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;

import org.junit.Test;

public class PlacesPhotosTest {

    private static PlacesSearchDto parse(String json) {
        return new Gson().fromJson(json, PlacesSearchDto.class);
    }

    private static final String MATCHED = "{\"places\":[{\"id\":\"places/A\","
            + "\"displayName\":{\"text\":\"Heartattack and Vine\"},"
            + "\"photos\":[{\"name\":\"places/A/photos/P1\","
            + "\"authorAttributions\":[{\"displayName\":\"Jane Walker\"}]}]}]}";

    @Test
    public void readsTheFirstPhotographWhenGoogleMatchedTheRightPlace() {
        assertEquals("places/A/photos/P1",
                PlacesPhotos.photoName(parse(MATCHED), "Heartattack and Vine"));
        assertEquals("Jane Walker", PlacesPhotos.attribution(parse(MATCHED)));
    }

    @Test
    public void aDifferentPlaceIsRefusedRatherThanShownAsThisOne() {
        // A text search returns its best guess for any query; this one is not the cafe asked for.
        String other = "{\"places\":[{\"id\":\"places/B\","
                + "\"displayName\":{\"text\":\"State Library Victoria\"},"
                + "\"photos\":[{\"name\":\"places/B/photos/P9\"}]}]}";
        assertNull(PlacesPhotos.photoName(parse(other), "Southbank Library"));
    }

    @Test
    public void nothingToShowIsNull() {
        assertNull(PlacesPhotos.photoName(null, "Anything"));
        assertNull(PlacesPhotos.photoName(parse("{\"places\":[]}"), "Anything"));
        assertNull(PlacesPhotos.photoName(parse(MATCHED), null));
        String noPhotos = "{\"places\":[{\"id\":\"places/A\","
                + "\"displayName\":{\"text\":\"Heartattack and Vine\"}}]}";
        assertNull(PlacesPhotos.photoName(parse(noPhotos), "Heartattack and Vine"));
        assertNull(PlacesPhotos.attribution(parse(noPhotos)));
    }

    @Test
    public void theMediaUrlCarriesTheHandleTheWidthAndTheKey() {
        String url = PlacesPhotos.urlFor("places/A/photos/P1", "SECRET");
        assertTrue(url, url.startsWith("https://places.googleapis.com/v1/places/A/photos/P1/media"));
        assertTrue(url, url.contains("maxWidthPx=1280"));
        assertTrue(url, url.contains("key=SECRET"));
    }
}
