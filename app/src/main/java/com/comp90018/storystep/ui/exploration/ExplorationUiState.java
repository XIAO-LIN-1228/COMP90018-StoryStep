package com.comp90018.storystep.ui.exploration;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.guidance.CardinalDirection;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.platform.location.LocationSource;

/**
 * Everything the exploration dashboard renders, computed once so the Fragment only formats.
 *
 * <p>Optional context is carried as a nullable box rather than a number with a sentinel. A missing
 * fix means the remaining distance is <b>unknown</b>, and a screen that printed 0 m for it would be
 * telling the user they had arrived. Every reader must branch on the {@code has*} methods.
 */
public final class ExplorationUiState {

    private final String destinationName;
    private final SessionState state;
    private final PauseReason pauseReason;
    private final long activeDurationMillis;
    private final double travelledDistanceMeters;
    private final Double remainingDistanceMeters;
    private final Float accuracyMeters;
    private final LocationSource.Reason unavailableReason;
    private final boolean stopSaveFailed;
    private final boolean checkpointFailed;
    private final MovementSnapshot movement;
    private final GuidanceSnapshot guidance;
    private final Visit pendingStory;
    private final boolean visitSaveFailed;
    private final Long steps;
    private final Double ascentMeters;

    private ExplorationUiState(String destinationName,
                               SessionState state,
                               PauseReason pauseReason,
                               long activeDurationMillis,
                               double travelledDistanceMeters,
                               Double remainingDistanceMeters,
                               Float accuracyMeters,
                               LocationSource.Reason unavailableReason,
                               boolean stopSaveFailed,
                               boolean checkpointFailed,
                               MovementSnapshot movement,
                               GuidanceSnapshot guidance,
                               Visit pendingStory,
                               boolean visitSaveFailed,
                               Long steps,
                               Double ascentMeters) {
        this.steps = steps;
        this.ascentMeters = ascentMeters;
        this.destinationName = destinationName;
        this.state = state;
        this.pauseReason = pauseReason;
        this.activeDurationMillis = activeDurationMillis;
        this.travelledDistanceMeters = travelledDistanceMeters;
        this.remainingDistanceMeters = remainingDistanceMeters;
        this.accuracyMeters = accuracyMeters;
        this.unavailableReason = unavailableReason;
        this.stopSaveFailed = stopSaveFailed;
        this.checkpointFailed = checkpointFailed;
        this.movement = movement;
        this.guidance = guidance;
        this.pendingStory = pendingStory;
        this.visitSaveFailed = visitSaveFailed;
    }

    /**
     * Combines the coordinator's independent facts into one renderable state.
     *
     * @param unavailableReason the last reported reason location produced nothing, or null once a
     *                          fix has been accepted again
     */
    public static ExplorationUiState from(SessionSummary summary,
                                          Attraction destination,
                                          RouteSnapshot route,
                                          LocationSource.Reason unavailableReason,
                                          boolean stopSaveFailed,
                                          boolean checkpointFailed,
                                          MovementSnapshot movement,
                                          GuidanceSnapshot guidance,
                                          Visit pendingStory,
                                          boolean visitSaveFailed) {
        return from(summary, destination, route, unavailableReason, stopSaveFailed,
                checkpointFailed, movement, guidance, pendingStory, visitSaveFailed, null, null);
    }

    /**
     * @param steps        steps counted so far, or null on a device that cannot count them
     * @param ascentMeters metres climbed so far, or null without a barometer
     */
    public static ExplorationUiState from(SessionSummary summary,
                                          Attraction destination,
                                          RouteSnapshot route,
                                          LocationSource.Reason unavailableReason,
                                          boolean stopSaveFailed,
                                          boolean checkpointFailed,
                                          MovementSnapshot movement,
                                          GuidanceSnapshot guidance,
                                          Visit pendingStory,
                                          boolean visitSaveFailed,
                                          Long steps,
                                          Double ascentMeters) {
        LocationSample last = route.getLastSample();
        Double remaining = null;
        if (last != null && destination != null) {
            remaining = GeoCalculator.distanceMeters(
                    last.getPosition(), destination.getLocation());
        }
        return new ExplorationUiState(
                destination == null ? null : destination.getName(),
                summary.getState(),
                summary.getPauseReason(),
                summary.getActiveDurationMillis(),
                route.getTotalDistanceMeters(),
                remaining,
                last == null ? null : last.getAccuracyMeters(),
                last == null ? unavailableReason : null,
                stopSaveFailed,
                checkpointFailed,
                movement,
                guidance,
                pendingStory,
                visitSaveFailed,
                steps,
                ascentMeters);
    }

    /** Steps counted so far, or null when the device cannot count them. */
    public Long getSteps() {
        return steps;
    }

    /** Metres climbed so far, or null when the device has no barometer. */
    public Double getAscentMeters() {
        return ascentMeters;
    }

    /**
     * True when a recorded arrival is waiting to be offered to the user.
     *
     * <p>Only ever true for a visit that is already stored, so the banner cannot promise a story
     * from an arrival that failed to save.
     */
    public boolean isArrivalBannerVisible() {
        return pendingStory != null;
    }

    /** The arrived-at attraction's name, or null when no arrival is pending. */
    public String getArrivedAttractionName() {
        return pendingStory == null ? null : pendingStory.getAttractionName();
    }

    /** The arrived-at attraction's id, for opening its story. Null when no arrival is pending. */
    public String getArrivedAttractionId() {
        return pendingStory == null ? null : pendingStory.getAttractionId();
    }

    /**
     * True when an arrival could not be recorded.
     *
     * <p>Deliberately never shown at the same time as the banner: one says a visit is saved and
     * ready to read, the other says nothing was saved at all.
     */
    public boolean isVisitSaveFailed() {
        return visitSaveFailed;
    }

    /** Null when the user started exploring without choosing a destination. */
    public String getDestinationName() {
        return destinationName;
    }

    public SessionState getState() {
        return state;
    }

    /** Non-null exactly when {@link #getState()} is {@code PAUSED}. */
    public PauseReason getPauseReason() {
        return pauseReason;
    }

    /** Collecting time only; paused spans are already excluded. */
    public long getActiveDurationMillis() {
        return activeDurationMillis;
    }

    public double getTravelledDistanceMeters() {
        return travelledDistanceMeters;
    }

    public boolean hasRemainingDistance() {
        return remainingDistanceMeters != null;
    }

    /** Straight-line metres to the destination. Only meaningful when {@link #hasRemainingDistance()}. */
    public double getRemainingDistanceMeters() {
        return remainingDistanceMeters == null ? 0.0 : remainingDistanceMeters;
    }

    public boolean hasAccuracy() {
        return accuracyMeters != null;
    }

    public float getAccuracyMeters() {
        return accuracyMeters == null ? 0.0f : accuracyMeters;
    }

    /** Why no position is available, or null when a fix has been accepted. */
    public LocationSource.Reason getUnavailableReason() {
        return unavailableReason;
    }

    /** True while the session is running and collecting. */
    public boolean isCollecting() {
        return state.isCollecting();
    }

    /** Stop is offered whenever there is a session to stop, and never during a save. */
    public boolean isStopEnabled() {
        return state.hasSession() && state != SessionState.STOPPING;
    }

    public boolean isResumeVisible() {
        return state == SessionState.PAUSED;
    }

    /** The session ended but could not be saved; the user must be able to retry, not navigate on. */
    public boolean isRetrySaveVisible() {
        return stopSaveFailed;
    }

    /**
     * Progress could not be written and the session is running on unsaved data.
     *
     * <p>Only shown while a session is actually in progress. After a stop, the failure that
     * matters is the final save, and two warnings about the same storage problem would just
     * compete for the user's attention.
     */
    public boolean isUnsavedProgressWarningVisible() {
        return checkpointFailed && state.hasSession() && !stopSaveFailed;
    }

    /** True once the session is saved, which is when the summary screen may be opened. */
    public boolean isFinished() {
        return state == SessionState.COMPLETED;
    }

    /** True before any session exists, for example after a discard. */
    public boolean isIdle() {
        return state == SessionState.IDLE;
    }

    // --- movement ------------------------------------------------------------------------------

    /** Never null; {@code UNAVAILABLE} on a device with no accelerometer. */
    public MovementState getMovementState() {
        return movement.getState();
    }

    public long getMovingMillis() {
        return movement.getMovingMillis();
    }

    public long getStationaryMillis() {
        return movement.getStationaryMillis();
    }

    // --- direction -----------------------------------------------------------------------------

    /**
     * True when an arrow may be drawn.
     *
     * <p>False does not mean the direction is unknown — {@link #hasCardinal()} may still hold. It
     * means only that the device cannot say which way it is facing, so a turn cannot be shown.
     */
    public boolean hasDirectionArrow() {
        return guidance.hasRelativeBearing();
    }

    /** Degrees to rotate the arrow: positive is clockwise. Check {@link #hasDirectionArrow()}. */
    public double getRelativeBearingDegrees() {
        return guidance.getRelativeBearingDegrees();
    }

    /** True when the destination's compass direction is known, with or without a heading. */
    public boolean hasCardinal() {
        return guidance.getCardinal() != null;
    }

    /** The eight-point label for the destination's bearing, or null when there is no bearing. */
    public CardinalDirection getCardinal() {
        return guidance.getCardinal();
    }
}
