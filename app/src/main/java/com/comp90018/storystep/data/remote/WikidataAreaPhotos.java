package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.GeoPoint;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assigns the photographed items of an area query to places.
 *
 * <p>Pure, so the matching can be tested without a socket. An item is the place when their
 * names agree and they stand within {@link #NAMED_RADIUS_M} of each other, or - failing a name -
 * when the item is within {@link #UNNAMED_RADIUS_M}, close enough that it can only be the same
 * thing. Names are compared loosely: case, punctuation and the words "the" and "of" do not
 * count, and one name may be the other with words added.
 */
public final class WikidataAreaPhotos {

    static final double NAMED_RADIUS_M = 250;
    static final double UNNAMED_RADIUS_M = 15;

    private static final Pattern POINT = Pattern.compile(
            "Point\\(\\s*(-?[0-9.]+)\\s+(-?[0-9.]+)\\s*\\)");

    private WikidataAreaPhotos() {
    }

    /** Place index to image URL, for every place (by index) the results can picture. */
    public static Map<Integer, String> assign(@Nullable SparqlResultsDto results,
                                              List<String> names,
                                              List<GeoPoint> places) {
        Map<Integer, String> chosen = new HashMap<>();
        if (results == null || results.results == null || results.results.bindings == null) {
            return chosen;
        }
        Map<Integer, Double> bestDistance = new HashMap<>();
        Map<Integer, Boolean> bestNamed = new HashMap<>();
        for (Map<String, SparqlResultsDto.Binding> row : results.results.bindings) {
            GeoPoint at = point(row.get("coord"));
            String url = image(row.get("image"));
            String label = row.get("itemLabel") == null ? null : row.get("itemLabel").value;
            if (at == null || url == null) {
                continue;
            }
            for (int i = 0; i < places.size(); i++) {
                GeoPoint place = places.get(i);
                if (place == null) {
                    continue;
                }
                double distance = GeoCalculator.distanceMeters(place, at);
                boolean named = sameName(names.get(i), label);
                if (!(named ? distance <= NAMED_RADIUS_M : distance <= UNNAMED_RADIUS_M)) {
                    continue;
                }
                // Close but called something else is something else.
                if (!named && CommonsPhotos.namesSomethingElse(names.get(i), label)) {
                    continue;
                }
                // A name match beats proximity; among equals, the nearer item.
                Boolean currentNamed = bestNamed.get(i);
                Double current = bestDistance.get(i);
                boolean better = current == null
                        || (named && !currentNamed)
                        || (named == currentNamed && distance < current);
                if (better) {
                    bestDistance.put(i, distance);
                    bestNamed.put(i, named);
                    chosen.put(i, url);
                }
            }
        }
        return chosen;
    }

    static boolean sameName(@Nullable String a, @Nullable String b) {
        if (a == null || b == null) {
            return false;
        }
        String x = normalise(a);
        String y = normalise(b);
        if (x.length() < 5 || y.length() < 5) {
            return false;
        }
        // "Melbourne Skydeck" and "Eureka Skydeck" are the same thing under two names.
        return x.equals(y) || x.contains(y) || y.contains(x) || CommonsPhotos.sharesAWord(a, b);
    }

    private static String normalise(String name) {
        String lower = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\b(the|of|and|a)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return lower;
    }

    @Nullable
    private static GeoPoint point(@Nullable SparqlResultsDto.Binding binding) {
        if (binding == null || binding.value == null) {
            return null;
        }
        Matcher m = POINT.matcher(binding.value);
        if (!m.find()) {
            return null;
        }
        try {
            // WKT is longitude first.
            return new GeoPoint(Double.parseDouble(m.group(2)), Double.parseDouble(m.group(1)));
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    /** The P18 value as a scaled, https Commons URL. */
    @Nullable
    private static String image(@Nullable SparqlResultsDto.Binding binding) {
        if (binding == null || binding.value == null) {
            return null;
        }
        String marker = "Special:FilePath/";
        int at = binding.value.indexOf(marker);
        if (at < 0) {
            return null;
        }
        String file = binding.value.substring(at + marker.length());
        try {
            file = java.net.URLDecoder.decode(file, "UTF-8");
        } catch (java.io.UnsupportedEncodingException | IllegalArgumentException ignored) {
            // Keep it as given.
        }
        return OsmImageUrls.fromCommonsTag("File:" + file);
    }
}
