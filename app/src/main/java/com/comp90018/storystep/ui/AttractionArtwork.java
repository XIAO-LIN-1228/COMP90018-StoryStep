package com.comp90018.storystep.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.comp90018.storystep.R;

/**
 * Maps attraction identifiers to bundled artwork without leaking UI resources into data.
 *
 * <p>The photographs belong to the five bundled sample places and to nothing else. Since REQ-18 the
 * list is mostly places fetched from OpenStreetMap, which ships no imagery, and every one of those
 * would otherwise have fallen through to the picture of the Old Arts Building — a photograph of one
 * real landmark presented as another. A fetched place gets the neutral placeholder instead, which
 * says nothing rather than something false.
 *
 * <p>{@link #recommendationFor} is subject to the same rule and answers {@code null} for anything
 * it does not have a written line for. The caller hides the row rather than substituting a generic
 * one: "Heritage architecture and university history" is a claim about a specific building, not a
 * default.
 */
public final class AttractionArtwork {

    private AttractionArtwork() {
    }

    @DrawableRes
    public static int drawableFor(String attractionId) {
        if ("system-garden".equals(attractionId)) {
            return R.drawable.photo_system_garden;
        }
        if ("melbourne-museum".equals(attractionId)
                || "potter-museum".equals(attractionId)) {
            return R.drawable.photo_melbourne_museum;
        }
        if ("old-arts".equals(attractionId)
                || "royal-exhibition-building".equals(attractionId)) {
            return R.drawable.photo_old_arts_thumb;
        }
        return R.drawable.bg_photo_placeholder;
    }

    @DrawableRes
    public static int heroDrawableFor(String attractionId) {
        if ("old-arts".equals(attractionId)
                || "royal-exhibition-building".equals(attractionId)) {
            return R.drawable.photo_old_arts;
        }
        return drawableFor(attractionId);
    }

    /**
     * @return the written line for one of the bundled places, or {@code null} when there is none
     */
    @Nullable
    public static String recommendationFor(String attractionId) {
        if ("system-garden".equals(attractionId)) {
            return "A peaceful outdoor stop nearby.";
        }
        if ("melbourne-museum".equals(attractionId)) {
            return "Explore Victoria’s natural and cultural history.";
        }
        if ("potter-museum".equals(attractionId)) {
            return "Art and culture close to campus.";
        }
        if ("old-arts".equals(attractionId)
                || "royal-exhibition-building".equals(attractionId)) {
            return "Heritage architecture and university history.";
        }
        return null;
    }
}
