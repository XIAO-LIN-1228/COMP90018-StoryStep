package com.comp90018.storystep.ui.saved;

import com.comp90018.storystep.ui.nearby.AttractionListItem;

import java.util.Collections;
import java.util.List;

/**
 * The states the saved list can express.
 *
 * <p>EMPTY is distinct from CONTENT with no rows on purpose: "you have not saved anything yet" and
 * "this failed to load" must not look the same, and an empty list rendered as content reads as the
 * second.
 */
public final class SavedUiState {

    public enum Status {
        LOADING,
        CONTENT,
        EMPTY,
        ERROR
    }

    private final Status status;
    private final List<AttractionListItem> items;

    private SavedUiState(Status status, List<AttractionListItem> items) {
        this.status = status;
        this.items = Collections.unmodifiableList(items);
    }

    public static SavedUiState loading() {
        return new SavedUiState(Status.LOADING, Collections.<AttractionListItem>emptyList());
    }

    public static SavedUiState content(List<AttractionListItem> items) {
        return items.isEmpty()
                ? empty()
                : new SavedUiState(Status.CONTENT, items);
    }

    public static SavedUiState empty() {
        return new SavedUiState(Status.EMPTY, Collections.<AttractionListItem>emptyList());
    }

    public static SavedUiState error() {
        return new SavedUiState(Status.ERROR, Collections.<AttractionListItem>emptyList());
    }

    public Status getStatus() {
        return status;
    }

    public List<AttractionListItem> getItems() {
        return items;
    }
}
