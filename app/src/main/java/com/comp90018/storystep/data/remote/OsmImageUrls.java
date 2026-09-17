package com.comp90018.storystep.data.remote;

import androidx.annotation.Nullable;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;

/**
 * Turns OpenStreetMap's photo tags into one https URL the app can load.
 *
 * <p>OSM records a photograph in two ways: {@code image}, which is usually a bare URL but is
 * sometimes a Wikimedia Commons file reference, and {@code wikimedia_commons}, which names a
 * Commons file ({@code File:...}) or a category ({@code Category:...}). A category is a folder,
 * not a picture, so it yields nothing. Commons files resolve through {@code Special:FilePath},
 * which redirects to a scaled rendition; the width parameter keeps a list of thumbnails from
 * pulling full-resolution originals.
 *
 * <p>Only https results are returned. A plain-http URL is upgraded rather than dropped, since
 * cleartext image traffic is neither allowed by the app's network policy nor worth the exception.
 */
public final class OsmImageUrls {

    private static final String COMMONS_FILE_PATH =
            "https://commons.wikimedia.org/wiki/Special:FilePath/";
    private static final int THUMBNAIL_WIDTH = 1280;

    private OsmImageUrls() {
    }

    /** The best photo URL the tags support, or null when there is none. */
    @Nullable
    public static String fromTags(@Nullable Map<String, String> tags) {
        if (tags == null) {
            return null;
        }
        String fromImage = fromImageTag(tags.get("image"));
        if (fromImage != null) {
            return fromImage;
        }
        return fromCommonsTag(tags.get("wikimedia_commons"));
    }

    @Nullable
    static String fromImageTag(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        // Multi-valued tags are semicolon-separated; the first entry is the primary picture.
        int separator = trimmed.indexOf(';');
        if (separator >= 0) {
            trimmed = trimmed.substring(0, separator).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            return trimmed;
        }
        if (lower.startsWith("http://")) {
            return "https://" + trimmed.substring("http://".length());
        }
        if (lower.startsWith("file:")) {
            return commonsFile(trimmed.substring("file:".length()));
        }
        return null;
    }

    @Nullable
    static String fromCommonsTag(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("file:")) {
            return commonsFile(trimmed.substring("file:".length()));
        }
        return null;
    }

    @Nullable
    private static String commonsFile(String fileName) {
        String name = fileName.trim().replace(' ', '_');
        if (name.isEmpty()) {
            return null;
        }
        try {
            return COMMONS_FILE_PATH
                    + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
                    + "?width=" + THUMBNAIL_WIDTH;
        } catch (UnsupportedEncodingException impossible) {
            return null;
        }
    }
}
