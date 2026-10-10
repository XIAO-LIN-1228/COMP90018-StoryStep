package com.comp90018.storystep.ui;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.AttractionCategory;

/**
 * Maps attraction identifiers to bundled artwork without leaking UI resources into data.
 *
 * <p>The photographs belong to the five bundled sample places and to nothing else. Since REQ-18 the
 * list is mostly places fetched from OpenStreetMap, which ships no imagery, and every one of those
 * would otherwise have fallen through to the picture of the Old Arts Building — a photograph of one
 * real landmark presented as another. A fetched place gets the neutral placeholder instead, which
 * says nothing rather than something false.
 *
 * <p>The category overloads go one step further without breaking that rule: a place with no
 * photograph shows an illustration of its kind (a palette for public art, a cup for a cafe). That
 * is a statement about the category the provider gave the place, not a picture of the place.
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

    /**
     * As {@link #drawableFor(String)}, but a place without bundled artwork gets the illustration
     * for its category instead of the neutral placeholder.
     */
    @DrawableRes
    public static int drawableFor(String attractionId, @Nullable AttractionCategory category) {
        int bundled = drawableFor(attractionId);
        return bundled == R.drawable.bg_photo_placeholder ? categoryDrawable(category) : bundled;
    }

    /** The illustration for a category, or the neutral placeholder when none is known. */
    @DrawableRes
    public static int categoryDrawable(@Nullable AttractionCategory category) {
        if (category == null) {
            return R.drawable.bg_photo_placeholder;
        }
        switch (category) {
            case HISTORICAL_BUILDING:
                return R.drawable.art_category_historical_building;
            case MUSEUM:
                return R.drawable.art_category_museum;
            case PARK:
                return R.drawable.art_category_park;
            case PUBLIC_ART:
                return R.drawable.art_category_public_art;
            case CULTURAL_SITE:
                return R.drawable.art_category_cultural_site;
            case UNIVERSITY_LANDMARK:
                return R.drawable.art_category_university_landmark;
            case FOOD:
                return R.drawable.art_category_food;
            case DRINK:
                return R.drawable.art_category_drink;
            case SHOPPING:
                return R.drawable.art_category_shopping;
            default:
                return R.drawable.bg_photo_placeholder;
        }
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
