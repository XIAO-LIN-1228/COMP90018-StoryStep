package com.comp90018.storystep.ui.journey;

import com.comp90018.storystep.domain.model.SessionHistoryEntry;

import java.util.Collections;
import java.util.List;

/** Immutable state rendered by the journey-history screen. */
public final class JourneysUiState {

    public enum Status { LOADING, CONTENT, EMPTY, ERROR }

    private final Status status;
    private final List<SessionHistoryEntry> journeys;

    private JourneysUiState(Status status, List<SessionHistoryEntry> journeys) {
        this.status = status;
        this.journeys = Collections.unmodifiableList(journeys);
    }

    public static JourneysUiState loading() {
        return new JourneysUiState(Status.LOADING, Collections.<SessionHistoryEntry>emptyList());
    }

    /** Content with nothing in it is the empty state, never an empty list on a content screen. */
    public static JourneysUiState content(List<SessionHistoryEntry> journeys) {
        return journeys.isEmpty() ? empty() : new JourneysUiState(Status.CONTENT, journeys);
    }

    public static JourneysUiState empty() {
        return new JourneysUiState(Status.EMPTY, Collections.<SessionHistoryEntry>emptyList());
    }

    public static JourneysUiState error() {
        return new JourneysUiState(Status.ERROR, Collections.<SessionHistoryEntry>emptyList());
    }

    public Status getStatus() {
        return status;
    }

    public List<SessionHistoryEntry> getJourneys() {
        return journeys;
    }
}
