package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Picks the one photograph worth showing out of a Commons geosearch result.
 *
 * <p>Pure, so the choice can be tested without a socket. Photographs only - a JPEG of a decent
 * size - and never the things that also get geotagged next to a landmark: maps, plans, logos,
 * plaques and signs. Among what is left, the largest wins: on Commons, resolution is the best
 * free proxy for a photograph someone took with care.
 */
public final class CommonsPhotos {

    /** Anything narrower than this is a snapshot, not a picture of the place. */
    static final int MIN_WIDTH_PX = 900;

    /** Without a word in common with the place's name, a file must be practically on top of it. */
    static final double UNNAMED_RADIUS_M = 25;

    /** Words too common in Melbourne file names to tie a photograph to one place. */
    private static final java.util.Set<String> GENERIC_WORDS = new java.util.HashSet<>(
            java.util.Arrays.asList("melbourne", "victoria", "australia", "university", "museum",
                    "building", "memorial", "gallery", "theatre", "theater", "garden", "gardens",
                    "park", "street", "centre", "center", "carlton", "parkville", "statue",
                    "sculpture", "monument", "house", "hall", "church", "bridge", "station"));

    private static final Pattern NOT_A_PHOTOGRAPH = Pattern.compile(
            "map|plan|logo|diagram|plaque|sign|panorama|collage|\\.svg|\\.png|\\.gif",
            Pattern.CASE_INSENSITIVE);

    private CommonsPhotos() {
    }

    /** The scaled URL of the best photograph, or null when the result holds none worth showing. */
    @Nullable
    public static String pick(@Nullable CommonsGeosearchDto response) {
        if (response == null || response.query == null || response.query.pages == null) {
            return null;
        }
        String best = null;
        long bestPixels = 0;
        for (CommonsGeosearchDto.Page page : response.query.pages.values()) {
            long pixels = worth(page);
            if (pixels > bestPixels) {
                bestPixels = pixels;
                best = url(page);
            }
        }
        return best;
    }

    /**
     * Assigns the area query's photographs to places: for each place (by index) still without
     * a picture, the best photograph taken within {@code radiusMetres} of it. A photograph can
     * serve more than one place when memorials stand together; the places decide, not the file.
     */
    public static Map<Integer, String> assign(@Nullable CommonsGeosearchDto response,
                                              List<GeoPoint> places,
                                              double radiusMetres) {
        return assign(response, places, java.util.Collections.<String>nCopies(places.size(), null),
                radiusMetres);
    }

    /**
     * As above, but a file farther than {@link #UNNAMED_RADIUS_M} from a place must share a
     * distinctive word with the place's name: in a dense precinct the nearest big photograph
     * is often of the building next door.
     */
    public static Map<Integer, String> assign(@Nullable CommonsGeosearchDto response,
                                              List<GeoPoint> places,
                                              List<String> names,
                                              double radiusMetres) {
        Map<Integer, String> chosen = new HashMap<>();
        if (response == null || response.query == null || response.query.pages == null) {
            return chosen;
        }
        Map<Integer, Long> bestPixels = new HashMap<>();
        for (CommonsGeosearchDto.Page page : response.query.pages.values()) {
            long pixels = worth(page);
            if (pixels == 0 || page.coordinates == null || page.coordinates.isEmpty()) {
                continue;
            }
            CommonsGeosearchDto.Coordinate at = page.coordinates.get(0);
            GeoPoint taken = new GeoPoint(at.lat, at.lon);
            for (int i = 0; i < places.size(); i++) {
                GeoPoint place = places.get(i);
                if (place == null) {
                    continue;
                }
                double distance = GeoCalculator.distanceMeters(place, taken);
                boolean named = sharesAWord(names.get(i), page.title);
                if (distance > (named ? radiusMetres : UNNAMED_RADIUS_M)) {
                    continue;
                }
                // A named file outranks any unnamed one; among equals, the larger.
                long score = named ? pixels + (1L << 40) : pixels;
                Long current = bestPixels.get(i);
                if (current == null || score > current) {
                    bestPixels.put(i, score);
                    chosen.put(i, url(page));
                }
            }
        }
        return chosen;
    }

    /**
     * How much a file is worth as the place's picture, or zero when it is not one: a JPEG at
     * least {@link #MIN_WIDTH_PX} wide whose name does not say map, plan, logo or plaque.
     * Larger is better; a portrait frame counts half, because it crops badly into a card.
     */
    private static long worth(@Nullable CommonsGeosearchDto.Page page) {
        if (page == null || page.imageinfo == null || page.imageinfo.isEmpty()) {
            return 0;
        }
        CommonsGeosearchDto.ImageInfo info = page.imageinfo.get(0);
        if (info == null || info.mime == null
                || !info.mime.toLowerCase(Locale.ROOT).equals("image/jpeg")
                || info.width < MIN_WIDTH_PX) {
            return 0;
        }
        if (page.title != null && NOT_A_PHOTOGRAPH.matcher(page.title).find()) {
            return 0;
        }
        if (url(page) == null) {
            return 0;
        }
        long pixels = (long) info.width * info.height;
        return info.height > info.width ? pixels / 2 : pixels;
    }

    /** The scaled URL the API gave, or one built from the file title when it gave none. */
    @Nullable
    private static String url(CommonsGeosearchDto.Page page) {
        CommonsGeosearchDto.ImageInfo info = page.imageinfo.get(0);
        if (info.thumburl != null) {
            return info.thumburl;
        }
        return page.title == null ? null : OsmImageUrls.fromCommonsTag(page.title);
    }

    /** Whether a distinctive word (five letters or more, not a place-name commonplace) is shared. */
    static boolean sharesAWord(@Nullable String placeName, @Nullable String fileTitle) {
        if (placeName == null || fileTitle == null) {
            return false;
        }
        String title = fileTitle.toLowerCase(Locale.ROOT);
        for (String word : placeName.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() >= 5 && !GENERIC_WORDS.contains(word) && title.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** {@code lat|lon} as geosearch wants it, to six decimals. */
    public static String coordinate(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.6f|%.6f", latitude, longitude);
    }
}
