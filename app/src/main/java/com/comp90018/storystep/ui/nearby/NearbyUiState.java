package com.comp90018.storystep.ui.nearby;

import java.util.Collections;
import java.util.List;

/**
 * The four states the nearby screen can express. Loading, empty and error are designed in from
 * the start so later degradation paths — denied permission, offline, no fix — have somewhere to
 * go without reworking the screen.
 */
public final class NearbyUiState {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final List<AttractionListItem> items;

    private NearbyUiState(Status status, List<AttractionListItem> items) {
        this.status = status;
        this.items = Collections.unmodifiableList(items);
    }

    public static NearbyUiState loading() {
        return new NearbyUiState(Status.LOADING, Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState content(List<AttractionListItem> items) {
        return items.isEmpty()
                ? empty()
                : new NearbyUiState(Status.CONTENT, items);
    }

    public static NearbyUiState empty() {
        return new NearbyUiState(Status.EMPTY, Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState error() {
        return new NearbyUiState(Status.ERROR, Collections.<AttractionListItem>emptyList());
    }

    public Status getStatus() {
        return status;
    }

    public List<AttractionListItem> getItems() {
        return items;
    }
}
