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

    private AttractionDetailUiState(Status status, @Nullable Attraction attraction) {
        this.status = status;
        this.attraction = attraction;
    }

    public static AttractionDetailUiState loading() {
        return new AttractionDetailUiState(Status.LOADING, null);
    }

    public static AttractionDetailUiState content(Attraction attraction) {
        return new AttractionDetailUiState(Status.CONTENT, attraction);
    }

    public static AttractionDetailUiState error() {
        return new AttractionDetailUiState(Status.ERROR, null);
    }

    public Status getStatus() {
        return status;
    }

    @Nullable
    public Attraction getAttraction() {
        return attraction;
    }
}
