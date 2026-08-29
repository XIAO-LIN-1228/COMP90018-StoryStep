package com.comp90018.storystep.data;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.AttractionCategory;

import java.util.Locale;

/**
 * Converts {@link AttractionCategory} to and from the stable lower-case codes used by the
 * database and by remote payloads, so neither storage format is tied to the enum's Java naming.
 */
public final class AttractionCategoryCodec {

    private AttractionCategoryCodec() {
        // Utility class.
    }

    public static String toCode(AttractionCategory category) {
        return category.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a stored or received code.
     *
     * @return the matching category, or {@code fallback} when the code is unknown, so an
     *         unrecognised value from a service or an older database row cannot crash the app
     */
    public static AttractionCategory fromCode(@Nullable String code,
                                              AttractionCategory fallback) {
        if (code == null) {
            return fallback;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (AttractionCategory category : AttractionCategory.values()) {
            if (category.name().equals(normalized)) {
                return category;
            }
        }
        return fallback;
    }
}
