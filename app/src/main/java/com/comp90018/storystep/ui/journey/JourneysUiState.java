package com.comp90018.storystep.ui.journey;

import com.comp90018.storystep.domain.model.Journey;

import java.util.Collections;
import java.util.List;

/** Immutable state rendered by the journeys screen. */
public final class JourneysUiState {
    public enum Status { LOADING, CONTENT, EMPTY, ERROR }

    private final Status status;
    private final List<Journey> journeys;

    private JourneysUiState(Status status, List<Journey> journeys) {
        this.status = status;
        this.journeys = Collections.unmodifiableList(journeys);
    }

    public static JourneysUiState loading() {
        return new JourneysUiState(Status.LOADING, Collections.<Journey>emptyList());
    }

    public static JourneysUiState content(List<Journey> journeys) {
        return journeys.isEmpty() ? empty() : new JourneysUiState(Status.CONTENT, journeys);
    }

    public static JourneysUiState empty() {
        return new JourneysUiState(Status.EMPTY, Collections.<Journey>emptyList());
    }

    public static JourneysUiState error() {
        return new JourneysUiState(Status.ERROR, Collections.<Journey>emptyList());
    }

    public Status getStatus() { return status; }
    public List<Journey> getJourneys() { return journeys; }
}
