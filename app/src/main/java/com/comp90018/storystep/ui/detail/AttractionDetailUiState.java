package com.comp90018.storystep.ui.detail;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.Attraction;

/** The states the attraction detail screen can express. */
public final class AttractionDetailUiState {

    public enum Status {
        LOADING,
        CONTENT,
        ERROR
    }

    private final Status status;
    @Nullable
    private final Attraction attraction;
    private final boolean saved;
    private final boolean saveInFlight;
    private final boolean fromSavedSnapshot;

    private AttractionDetailUiState(Status status,
                                    @Nullable Attraction attraction,
                                    boolean saved,
                                    boolean saveInFlight,
                                    boolean fromSavedSnapshot) {
        this.status = status;
        this.attraction = attraction;
        this.saved = saved;
        this.saveInFlight = saveInFlight;
        this.fromSavedSnapshot = fromSavedSnapshot;
    }

    public static AttractionDetailUiState loading() {
        return new AttractionDetailUiState(Status.LOADING, null, false, false, false);
    }

    public static AttractionDetailUiState content(Attraction attraction) {
        return new AttractionDetailUiState(Status.CONTENT, attraction, false, false, false);
    }

    /**
     * Content read back from the user's own saved copy because the attraction cache could not
     * answer. It is shown as ordinary content, labelled, rather than as an error: the user kept
     * this place, and a stored copy of it is a better answer than a dead end.
     */
    public static AttractionDetailUiState savedSnapshot(Attraction attraction) {
        return new AttractionDetailUiState(Status.CONTENT, attraction, true, false, true);
    }

    public static AttractionDetailUiState error() {
        return new AttractionDetailUiState(Status.ERROR, null, false, false, false);
    }

    /** The same screen with a new saved state; used when the store answers or another screen writes. */
    public AttractionDetailUiState withSaved(boolean saved, boolean saveInFlight) {
        return new AttractionDetailUiState(status, attraction, saved, saveInFlight,
                fromSavedSnapshot);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public Attraction getAttraction() {
        return attraction;
    }

    /** True when this attraction is in the user's saved list. */
    public boolean isSaved() {
        return saved;
    }

    /**
     * True while a save or unsave for this attraction is still being written.
     *
     * <p>The screen keeps showing the previous state rather than an intermediate one, and the
     * ViewModel ignores further toggles until the write resolves — which is what stops a rapid
     * double tap from racing two writes at the same row.
     */
    public boolean isSaveInFlight() {
        return saveInFlight;
    }

    /**
     * True when the content came from the user's saved copy rather than the attraction cache.
     *
     * <p>The screen says so. A snapshot was taken when the place was saved and the live record may
     * have changed since; claiming it is current would be a claim the app cannot support.
     */
    public boolean isFromSavedSnapshot() {
        return fromSavedSnapshot;
    }
}
