package com.comp90018.storystep.ui.nearby;

import java.util.Collections;
import java.util.List;

/**
 * Everything the nearby screen can express.
 *
 * <p>Three independent axes are modelled rather than one flat enum, because they genuinely vary
 * independently: {@link Status} says what the screen is doing, {@link OriginLabel} says whose
 * position the distances are measured from, and {@link SessionPhase} says what the exploration
 * session is doing. A list of results measured from the demonstration reference area and the same
 * list measured from a live fix are both {@code CONTENT}, but they must never look the same to the
 * user.
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

    /**
     * What the exploration session is doing.
     *
     * <p>Separate from {@link Status} because the two answer different questions: a session can be
     * paused while the list is perfectly good content, and the list can be loading while no session
     * exists at all. Collapsing them would make the control label depend on the load state.
     */
    public enum SessionPhase {

        /** No session. The control offers Start. */
        IDLE,

        /** Start is in flight; the control is inert so a second press cannot open a second session. */
        STARTING,

        /** Collecting. The control offers Stop. */
        ACTIVE,

        /** Interrupted by leaving the app. Returning is enough; the control offers Resume. */
        PAUSED_BACKGROUND,

        /** Interrupted by losing permission or a provider. Resume needs that restored first. */
        PAUSED_ACCESS_LOST,

        /** Stop is in flight, or its save failed and can be retried. */
        STOPPING;

        /** True while collection is suspended but the session still exists. */
        public boolean isPaused() {
            return this == PAUSED_BACKGROUND || this == PAUSED_ACCESS_LOST;
        }

        /** True while a transition is in flight and the control must not accept another press. */
        public boolean isBusy() {
            return this == STARTING || this == STOPPING;
        }
    }

    /** Whose position the listed distances are measured from. */
    public enum OriginLabel {

        /** The demonstration reference area, before any session started. Must be labelled. */
        REFERENCE_AREA,

        /** A live fine-grained device fix. */
        LIVE_PRECISE,

        /** A live coarse device fix; distances are approximate and must say so. */
        LIVE_APPROXIMATE,

        /**
         * The user's last known position while the session is paused. Kept rather than snapping
         * back to the reference area: the distances are still the most useful ones available, but
         * they are no longer being updated and must not be presented as current.
         */
        LIVE_PAUSED
    }

    private final Status status;
    private final OriginLabel originLabel;
    private final SessionPhase sessionPhase;
    private final List<AttractionListItem> items;

    private NearbyUiState(Status status,
                          OriginLabel originLabel,
                          SessionPhase sessionPhase,
                          List<AttractionListItem> items) {
        this.status = status;
        this.originLabel = originLabel;
        this.sessionPhase = sessionPhase;
        this.items = Collections.unmodifiableList(items);
    }

    public static NearbyUiState loading(OriginLabel originLabel, SessionPhase phase) {
        return new NearbyUiState(Status.LOADING, originLabel, phase,
                Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState content(OriginLabel originLabel,
                                        SessionPhase phase,
                                        List<AttractionListItem> items) {
        return items.isEmpty()
                ? empty(originLabel, phase)
                : new NearbyUiState(Status.CONTENT, originLabel, phase, items);
    }

    public static NearbyUiState empty(OriginLabel originLabel, SessionPhase phase) {
        return new NearbyUiState(Status.EMPTY, originLabel, phase,
                Collections.<AttractionListItem>emptyList());
    }

    public static NearbyUiState error(OriginLabel originLabel, SessionPhase phase) {
        return new NearbyUiState(Status.ERROR, originLabel, phase,
                Collections.<AttractionListItem>emptyList());
    }

    /**
     * A state that blocks the list, such as a permission or provider problem.
     *
     * <p>{@code items} is carried through so a transient loss of location does not throw away
     * results the user can still read.
     */
    public static NearbyUiState blocked(Status status,
                                        OriginLabel originLabel,
                                        SessionPhase phase,
                                        List<AttractionListItem> items) {
        if (status == Status.CONTENT || status == Status.LOADING) {
            throw new IllegalArgumentException("not a blocked status: " + status);
        }
        return new NearbyUiState(status, originLabel, phase, items);
    }

    public Status getStatus() {
        return status;
    }

    public OriginLabel getOriginLabel() {
        return originLabel;
    }

    public SessionPhase getSessionPhase() {
        return sessionPhase;
    }

    public List<AttractionListItem> getItems() {
        return items;
    }

    /** True while distances are measured from the demonstration area rather than the user. */
    public boolean isReferenceArea() {
        return originLabel == OriginLabel.REFERENCE_AREA;
    }
}