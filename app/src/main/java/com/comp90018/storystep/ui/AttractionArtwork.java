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
 * real landmark presented as another. A fetched place gets drawn artwork for its kind instead -
 * see {@link #artworkFor} - which says what sort of place it is and never claims to be a picture
 * of that one. OpenStreetMap has photographs of landmarks and almost none of cafes, shops or
 * street sculpture, so without it most of the list would be grey holes.
 *
 * <p>{@link #recommendationFor} is subject to the same rule and answers {@code null} for anything
 * it does not have a written line for. The caller hides the row rather than substituting a generic
 * one: "Heritage architecture and university history" is a claim about a specific building, not a
 * default.
 */
public final class AttractionArtwork {

    private AttractionArtwork() {
    }

    /**
     * Drawn artwork for a kind of place: a tinted ground carrying that kind's mark. Every
     * category answers with something, so no place is ever left without an image.
     */
    @DrawableRes
    public static int artworkFor(@Nullable AttractionCategory category) {
        if (category == null) {
            return R.drawable.art_sights;
        }
        switch (category) {
            case MUSEUM:
                return R.drawable.art_museum;
            case PUBLIC_ART:
                return R.drawable.art_art;
            case PARK:
                return R.drawable.art_park;
            case FOOD:
                return R.drawable.art_food;
            case DRINK:
                return R.drawable.art_drink;
            case SHOPPING:
                return R.drawable.art_shopping;
            case HISTORICAL_BUILDING:
            case UNIVERSITY_LANDMARK:
                return R.drawable.art_building;
            case CULTURAL_SITE:
            default:
                return R.drawable.art_sights;
        }
    }

    /** The bundled photograph for a seed place, or that kind's drawn artwork. */
    @DrawableRes
    public static int drawableFor(String attractionId, @Nullable AttractionCategory category) {
        int bundled = bundledFor(attractionId);
        return bundled != 0 ? bundled : artworkFor(category);
    }

    /** As {@link #drawableFor}, with the larger bundled photograph where there is one. */
    @DrawableRes
    public static int heroDrawableFor(String attractionId, @Nullable AttractionCategory category) {
        if ("old-arts".equals(attractionId)
                || "royal-exhibition-building".equals(attractionId)) {
            return R.drawable.photo_old_arts;
        }
        return drawableFor(attractionId, category);
    }

    /** The bundled photograph for one of the seed places, or zero when it is not one. */
    @DrawableRes
    private static int bundledFor(String attractionId) {
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
        return 0;
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
