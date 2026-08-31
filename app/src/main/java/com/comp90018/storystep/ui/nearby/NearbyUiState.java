package com.comp90018.storystep.ui.nearby;

import java.util.Collections;
import java.util.List;

/**
 * Everything the nearby screen can express.
 *
 * <p>Two independent axes are modelled rather than one flat enum, because they genuinely vary
 * independently: {@link Status} says what the screen is doing, and {@link OriginLabel} says whose
 * position the distances are measured from. A list of results measured from the demonstration
 * reference area and the same list measured from a live fix are both {@code CONTENT}, but they must
 * never look the same to the user.
 *
 * <p>Cached and stale content states arrive with the local cache work; they are not represented yet
 * because nothing can currently produce them.
 */
public final class NearbyUiState {

    public enum Status {

        LOADING,
        CONTENT,
        EMPTY,
        ERROR,

        /** Location has not been requested yet; the screen offers the purpose and a Start action. */
        PERMISSION_REQUIRED,

        /** Denied, but asking again would still show a dialog. */
        PERMISSION_DENIED,

        /** Denied for good; only system settings can recover, so offer that instead of a retry. */
        PERMISSION_PERMANENTLY_DENIED,

        /** Location services are off, or no provider is enabled. */
        LOCATION_UNAVAILABLE,

        /** Updates are running; no usable fix has arrived yet. */
        WAITING_FOR_FIX
    }

    /** Whose position the listed distances are measured from. */
    public enum OriginLabel {

        /** The demonstration reference area, before any session started. Must be labelled. */
        REFERENCE_AREA,

        /** A live fine-grained device fix. */
        LIVE_PRECISE,

        /** A live coarse device fix; distances are approximate and must say so. */
        LIVE_APPROXIMATE
    }

    private final Status status;
    private final OriginLabel originLabel;
    private final List<AttractionListItem> items;

    private NearbyUiState(Status status, OriginLabel originLabel, List<AttractionListItem> items) {
        this.status = status;
        this.originLabel = originLabel;
        this.items = Collections.unmodifiableList(items);
    }

    public static NearbyUiState loading(OriginLabel originLabel) {
        return new NearbyUiState(Status.LOADING, originLabel, Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState content(OriginLabel originLabel, List<AttractionListItem> items) {
        return items.isEmpty()
                ? empty(originLabel)
                : new NearbyUiState(Status.CONTENT, originLabel, items);
    }

    public static NearbyUiState empty(OriginLabel originLabel) {
        return new NearbyUiState(Status.EMPTY, originLabel, Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState error(OriginLabel originLabel) {
        return new NearbyUiState(Status.ERROR, originLabel, Collections.<AttractionListItem>emptyList());
    }

    /**
     * A state that blocks the list, such as a permission or provider problem.
     *
     * <p>{@code items} is carried through so a transient loss of location does not throw away
     * results the user can still read.
     */
    public static NearbyUiState blocked(Status status,
                                        OriginLabel originLabel,
                                        List<AttractionListItem> items) {
        if (status == Status.CONTENT || status == Status.LOADING) {
            throw new IllegalArgumentException("not a blocked status: " + status);
        }
        return new NearbyUiState(status, originLabel, items);
    }

    public Status getStatus() {
        return status;
    }

    public OriginLabel getOriginLabel() {
        return originLabel;
    }

    public List<AttractionListItem> getItems() {
        return items;
    }

    /** True while distances are measured from the demonstration area rather than the user. */
    public boolean isReferenceArea() {
        return originLabel == OriginLabel.REFERENCE_AREA;
    }
}