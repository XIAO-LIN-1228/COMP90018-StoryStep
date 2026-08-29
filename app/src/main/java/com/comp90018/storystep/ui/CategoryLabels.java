package com.comp90018.storystep.ui;

import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.AttractionCategory;

/**
 * Maps domain categories to localized labels. Keeping this in the UI layer is what allows the
 * domain model to stay free of user-visible text and of Android resources.
 */
public final class CategoryLabels {

    private CategoryLabels() {
        // Utility class.
    }

    @StringRes
    public static int labelOf(AttractionCategory category) {
        switch (category) {
            case HISTORICAL_BUILDING:
                return R.string.category_historical_building;
            case MUSEUM:
                return R.string.category_museum;
            case PARK:
                return R.string.category_park;
            case PUBLIC_ART:
                return R.string.category_public_art;
            case UNIVERSITY_LANDMARK:
                return R.string.category_university_landmark;
            case CULTURAL_SITE:
            default:
                return R.string.category_cultural_site;
        }
    }
}
