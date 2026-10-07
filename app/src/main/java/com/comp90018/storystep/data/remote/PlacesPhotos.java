package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Reads a photograph out of a Places text-search result, and builds the URL that serves it.
 *
 * <p>Pure, so the matching and the URL shape are tested without a key or a socket.
 *
 * <p>A text search returns its best guess for any query, so a result counts only when the name
 * Google matched really is the name asked for - the same rule the Wikimedia results face, and
 * for the same reason: a photograph of the wrong cafe is worse than no photograph.
 */
public final class PlacesPhotos {

    /** Long edge of the served image; the same width the Commons photographs are asked for. */
    static final int MAX_WIDTH_PX = 1280;

    private PlacesPhotos() {
    }

    /**
     * The URL of the first photograph of the place, or null.
     *
     * <p>The URL carries the key, which is why it is built at the moment a screen asks rather
     * than stored: Google's terms do not allow the photograph to be kept, and a stored URL is
     * a stored key as well.
     */
    @Nullable
    public static String photoUrl(@Nullable PlacesSearchDto response, String expectedName,
                                  String apiKey) {
        String photoName = photoName(response, expectedName);
        return photoName == null ? null : urlFor(photoName, apiKey);
    }

    /** The photo handle - "places/X/photos/Y" - for the place, when it is really that place. */
    @Nullable
    public static String photoName(@Nullable PlacesSearchDto response, String expectedName) {
        if (response == null || response.places == null || response.places.isEmpty()
                || expectedName == null) {
            return null;
        }
        PlacesSearchDto.Place place = response.places.get(0);
        if (place == null || place.photos == null || place.photos.isEmpty()) {
            return null;
        }
        String matched = place.displayName == null ? null : place.displayName.text;
        if (!CommonsPhotos.sharesAWord(expectedName, matched)) {
            return null;
        }
        PlacesSearchDto.Photo photo = place.photos.get(0);
        return photo == null ? null : photo.name;
    }

    /** Whoever took the first photograph, for the credit Google's terms require. */
    @Nullable
    public static String attribution(@Nullable PlacesSearchDto response) {
        if (response == null || response.places == null || response.places.isEmpty()) {
            return null;
        }
        PlacesSearchDto.Place place = response.places.get(0);
        if (place == null || place.photos == null || place.photos.isEmpty()) {
            return null;
        }
        PlacesSearchDto.Photo photo = place.photos.get(0);
        if (photo == null || photo.authorAttributions == null
                || photo.authorAttributions.isEmpty()) {
            return null;
        }
        return photo.authorAttributions.get(0).displayName;
    }

    /** The media URL for a photo handle. */
    public static String urlFor(String photoName, String apiKey) {
        return String.format(Locale.ROOT,
                "https://places.googleapis.com/v1/%s/media?maxWidthPx=%d&key=%s",
                photoName, MAX_WIDTH_PX, apiKey);
    }
}
