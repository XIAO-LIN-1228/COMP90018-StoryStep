package com.comp90018.storystep.ui.nearby;

import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
 * <p>Content also carries two things the list itself cannot express. Its {@link AttractionFreshness}
 * says whether it is a current answer from the provider or stored content being shown in its
 * absence, and its {@link AttractionProvenance} set says which sources are on screen, because the
 * licence on the fetched data requires them to be credited where they are displayed.
 *
 * <p>{@link Status#ERROR} carries a reason for the same kind of reason: a rate limit, an outage and
 * a device with no network need different words and different offers, and one untyped error state
 * gave all three the same unhelpful line.
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
    private final AttractionFreshness freshness;
    private final Set<AttractionProvenance> provenances;
    private final AttractionUnavailableException.Reason errorReason;

    private NearbyUiState(Status status,
                          OriginLabel originLabel,
                          SessionPhase sessionPhase,
                          List<AttractionListItem> items,
                          AttractionFreshness freshness,
                          Set<AttractionProvenance> provenances,
                          AttractionUnavailableException.Reason errorReason) {
        this.status = status;
        this.originLabel = originLabel;
        this.sessionPhase = sessionPhase;
        this.items = Collections.unmodifiableList(items);
        this.freshness = freshness;
        this.provenances = Collections.unmodifiableSet(provenances);
        this.errorReason = errorReason;
    }

    public static NearbyUiState loading(OriginLabel originLabel, SessionPhase phase) {
        return new NearbyUiState(Status.LOADING, originLabel, phase,
                Collections.<AttractionListItem>emptyList(),
                AttractionFreshness.FRESH, noProvenances(), null);
    }

    /** Content with no freshness or source claim; for tests and for a purely local assembly. */
    public static NearbyUiState content(OriginLabel originLabel,
                                        SessionPhase phase,
                                        List<AttractionListItem> items) {
        return content(originLabel, phase, items, AttractionFreshness.FRESH, noProvenances());
    }

    public static NearbyUiState content(OriginLabel originLabel,
                                        SessionPhase phase,
                                        List<AttractionListItem> items,
                                        AttractionFreshness freshness,
                                        Set<AttractionProvenance> provenances) {
        return items.isEmpty()
                ? empty(originLabel, phase, freshness)
                : new NearbyUiState(Status.CONTENT, originLabel, phase, items,
                        freshness, provenances, null);
    }

    public static NearbyUiState empty(OriginLabel originLabel, SessionPhase phase) {
        return empty(originLabel, phase, AttractionFreshness.FRESH);
    }

    /**
     * An empty result, which is a real answer rather than a failure.
     *
     * <p>It keeps its freshness because the two empties read differently: the provider searched
     * here and found nothing, or nothing could be searched and there was nothing stored either.
     */
    public static NearbyUiState empty(OriginLabel originLabel,
                                      SessionPhase phase,
                                      AttractionFreshness freshness) {
        return new NearbyUiState(Status.EMPTY, originLabel, phase,
                Collections.<AttractionListItem>emptyList(), freshness, noProvenances(), null);
    }

    public static NearbyUiState error(OriginLabel originLabel, SessionPhase phase) {
        return error(originLabel, phase, null);
    }

    /**
     * @param reason why nothing could be shown, or {@code null} when the failure was not one of
     *               the provider outcomes — a bad request from this app's own code, for instance
     */
    public static NearbyUiState error(OriginLabel originLabel,
                                      SessionPhase phase,
                                      AttractionUnavailableException.Reason reason) {
        return new NearbyUiState(Status.ERROR, originLabel, phase,
                Collections.<AttractionListItem>emptyList(),
                AttractionFreshness.FRESH, noProvenances(), reason);
    }

    private static Set<AttractionProvenance> noProvenances() {
        return EnumSet.noneOf(AttractionProvenance.class);
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
        return blocked(status, originLabel, phase, items, AttractionFreshness.FRESH, noProvenances());
    }

    public static NearbyUiState blocked(Status status, OriginLabel originLabel, SessionPhase phase,
            List<AttractionListItem> items, AttractionFreshness freshness,
            Set<AttractionProvenance> provenances) {
        if (status == Status.CONTENT || status == Status.LOADING) {
            throw new IllegalArgumentException("not a blocked status: " + status);
        }
        return new NearbyUiState(status, originLabel, phase, items, freshness, provenances, null);
    }

    /** Re-ranks retained rows while keeping the permission/network notice and provenance. */
    public NearbyUiState withItems(List<AttractionListItem> updated) {
        return new NearbyUiState(status, originLabel, sessionPhase, updated,
                freshness, provenances, errorReason);
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

    /** How current the listed content is. Always {@code FRESH} for states that carry no list. */
    public AttractionFreshness getFreshness() {
        return freshness;
    }

    /** The sources represented in {@link #getItems()}, for the credit each licence requires. */
    public Set<AttractionProvenance> getProvenances() {
        return provenances;
    }

    /** Why the load failed, or {@code null}. Meaningful only for {@link Status#ERROR}. */
    public AttractionUnavailableException.Reason getErrorReason() {
        return errorReason;
    }

    /** True when the list is stored content shown in the absence of a current provider answer. */
    public boolean isCached() {
        return freshness == AttractionFreshness.STALE;
    }

    /** True while distances are measured from the demonstration area rather than the user. */
    public boolean isReferenceArea() {
        return originLabel == OriginLabel.REFERENCE_AREA;
    }
}