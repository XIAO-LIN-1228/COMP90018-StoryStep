package com.comp90018.storystep.ui;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import java.util.Set;

/**
 * Turns the attraction path's provenance, freshness and failure reasons into resource ids.
 *
 * <p>Kept out of the fragments and out of the domain layer, which is where the two halves of this
 * belong: the domain says which source a record came from, the resource file says how to word the
 * credit, and this is the one place the two are joined. Both screens that display fetched places
 * use it, so the credit cannot end up worded one way on one screen and another way on the next.
 *
 * <p>The OpenStreetMap credit is a licence obligation under ODbL, not a nicety. It is required
 * wherever the data is shown, which is why {@link #attributionFor(Set)} answers for a set of
 * sources rather than leaving each screen to decide.
 */
public final class AttributionText {

    private AttributionText() {
        // Utility class.
    }

    /**
     * The credit line for whichever sources are on screen.
     *
     * @return the string resource, or {@code 0} when there is nothing displayed to credit
     */
    @StringRes
    public static int attributionFor(Set<AttractionProvenance> provenances) {
        boolean openStreetMap = provenances.contains(AttractionProvenance.OPENSTREETMAP);
        boolean seed = provenances.contains(AttractionProvenance.SEED);
        if (openStreetMap && seed) {
            return R.string.attribution_openstreetmap_and_seed;
        }
        if (openStreetMap) {
            return R.string.attribution_openstreetmap;
        }
        return seed ? R.string.attribution_seed : 0;
    }

    /** The credit line for one record, on a screen that shows a single place. */
    @StringRes
    public static int attributionFor(AttractionProvenance provenance) {
        return provenance == AttractionProvenance.OPENSTREETMAP
                ? R.string.attribution_openstreetmap
                : R.string.attribution_seed;
    }

    /**
     * The notice explaining that a list is stored content rather than a current answer.
     *
     * @return the string resource, or {@code 0} when the content is current and needs no notice
     */
    @StringRes
    public static int freshnessNoticeFor(AttractionFreshness freshness) {
        return freshness == AttractionFreshness.STALE ? R.string.nearby_cached_notice : 0;
    }

    /**
     * What to tell the user about a failed load.
     *
     * <p>The distinctions are the ones the user can act on. Being offline with nothing stored is
     * not the same as a service that is busy, and neither is the same as a service that answered
     * with something unusable — the first is fixed by finding a signal, the second by waiting, and
     * the third by nothing the user can do. A null or unrecognised reason falls back to the
     * generic line rather than guessing at one of these.
     */
    @StringRes
    public static int errorMessageFor(@Nullable AttractionUnavailableException.Reason reason) {
        if (reason == null) {
            return R.string.nearby_error;
        }
        switch (reason) {
            case NO_NETWORK:
            case NO_CACHE:
                return R.string.nearby_error_offline;
            case RATE_LIMITED:
                return R.string.nearby_error_rate_limited;
            case TIMEOUT:
                return R.string.nearby_error_timeout;
            case HTTP_ERROR:
            case MALFORMED:
                return R.string.nearby_error_service;
            default:
                return R.string.nearby_error;
        }
    }
}
