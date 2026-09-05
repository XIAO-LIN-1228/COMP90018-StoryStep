package com.comp90018.storystep.ui.journey;

import com.comp90018.storystep.domain.model.SessionHistoryDetail;

/** Immutable state rendered by the journey-detail screen. */
public final class JourneyDetailUiState {

    /**
     * {@code NOT_FOUND} is separate from {@code ERROR} because the two need different words. A
     * journey that was deleted is gone and the user should be told so plainly; a failed read is
     * something that might work next time.
     */
    public enum Status { LOADING, CONTENT, NOT_FOUND, ERROR }

    private final Status status;
    private final SessionHistoryDetail detail;

    private JourneyDetailUiState(Status status, SessionHistoryDetail detail) {
        this.status = status;
        this.detail = detail;
    }

    public static JourneyDetailUiState loading() {
        return new JourneyDetailUiState(Status.LOADING, null);
    }

    public static JourneyDetailUiState content(SessionHistoryDetail detail) {
        return new JourneyDetailUiState(Status.CONTENT, detail);
    }

    public static JourneyDetailUiState notFound() {
        return new JourneyDetailUiState(Status.NOT_FOUND, null);
    }

    public static JourneyDetailUiState error() {
        return new JourneyDetailUiState(Status.ERROR, null);
    }

    public Status getStatus() {
        return status;
    }

    /** The journey, or null in every state but {@code CONTENT}. */
    public SessionHistoryDetail getDetail() {
        return detail;
    }
}
