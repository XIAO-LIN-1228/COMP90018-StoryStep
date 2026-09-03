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
    private final MovementSnapshot movement;
    private final GuidanceSnapshot guidance;

    private ExplorationUiState(String destinationName,
                               SessionState state,
                               PauseReason pauseReason,
                               long activeDurationMillis,
                               double travelledDistanceMeters,
                               Double remainingDistanceMeters,
                               Float accuracyMeters,
                               LocationSource.Reason unavailableReason,
                               boolean stopSaveFailed,
                               MovementSnapshot movement,
                               GuidanceSnapshot guidance) {
        this.destinationName = destinationName;
        this.state = state;
        this.pauseReason = pauseReason;
        this.activeDurationMillis = activeDurationMillis;
        this.travelledDistanceMeters = travelledDistanceMeters;
        this.remainingDistanceMeters = remainingDistanceMeters;
        this.accuracyMeters = accuracyMeters;
        this.unavailableReason = unavailableReason;
        this.stopSaveFailed = stopSaveFailed;
        this.movement = movement;
        this.guidance = guidance;
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
                                          MovementSnapshot movement,
                                          GuidanceSnapshot guidance) {
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
                movement,
                guidance);
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
