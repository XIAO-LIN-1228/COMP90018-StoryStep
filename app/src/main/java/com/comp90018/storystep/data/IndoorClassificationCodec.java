package com.comp90018.storystep.data;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.IndoorClassification;

import java.util.Locale;

/**
 * Converts {@link IndoorClassification} to and from the stable lower-case codes used by the
 * database, so the storage format is not tied to the enum's Java naming.
 *
 * <p>The column was a boolean until schema 6. An unrecognised or absent code reads back as
 * {@link IndoorClassification#UNKNOWN} rather than as a guess, which is also the honest answer for
 * a row written by a version that had no way to say "unknown".
 */
public final class IndoorClassificationCodec {

    private IndoorClassificationCodec() {
        // Utility class.
    }

    public static String toCode(IndoorClassification classification) {
        return classification.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Parses a stored code.
     *
     * @return the matching classification, or {@link IndoorClassification#UNKNOWN} when the code is
     *         absent or unrecognised, so an older row cannot crash the app or be read as a claim
     */
    public static IndoorClassification fromCode(@Nullable String code) {
        if (code == null) {
            return IndoorClassification.UNKNOWN;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        for (IndoorClassification classification : IndoorClassification.values()) {
            if (classification.name().equals(normalized)) {
                return classification;
            }
        }
        return IndoorClassification.UNKNOWN;
    }
}
