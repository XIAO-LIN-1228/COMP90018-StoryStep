package com.comp90018.storystep.ui;

import androidx.annotation.DrawableRes;

import com.comp90018.storystep.R;

/** Maps prototype attraction identifiers to bundled artwork without leaking UI resources into data. */
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
        return R.drawable.photo_old_arts_thumb;
    }

    @DrawableRes
    public static int heroDrawableFor(String attractionId) {
        if ("old-arts".equals(attractionId)
                || "royal-exhibition-building".equals(attractionId)) {
            return R.drawable.photo_old_arts;
        }
        return drawableFor(attractionId);
    }

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
        return "Heritage architecture and university history.";
    }
}
