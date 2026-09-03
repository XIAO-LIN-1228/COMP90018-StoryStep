package com.comp90018.storystep.ui.exploration;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.platform.location.LocationSource;

/**
 * Renders the process-wide exploration session for the dashboard.
 *
 * <p>Like every other screen, it observes and commands the shared coordinator and never registers a
 * location source itself, so opening the dashboard cannot start a second subscription for a session
 * that is already running.
 *
 * <p>The elapsed time on screen is redrawn on a tick, not stored on a tick. Active duration is
 * always read back from the session itself, which means a paused session simply stops changing
 * rather than needing the timer to be cancelled in the right order.
 */
public class ExplorationViewModel extends ViewModel implements ExplorationCoordinator.Observer {

    /** How often the duration is redrawn. Display only; nothing is persisted at this rate. */
    static final long TICK_INTERVAL_MILLIS = 1_000L;

    /** Schedules the redraw. Injected so tests can drive time instead of waiting for it. */
    public interface Ticker {

        void start(Runnable onTick, long intervalMillis);

        void stop();
    }

    private final ExplorationCoordinator coordinator;
    private final Ticker ticker;
    private final MutableLiveData<ExplorationUiState> uiState = new MutableLiveData<>();

    private Attraction destination;
    private RouteSnapshot route = RouteSnapshot.empty();
    private LocationSource.Reason unavailableReason;
    private MovementSnapshot movement = MovementSnapshot.unavailable();
    private GuidanceSnapshot guidance = GuidanceSnapshot.unavailable();

    public ExplorationViewModel(ExplorationCoordinator coordinator) {
        this(coordinator, new HandlerTicker());
    }

    ExplorationViewModel(ExplorationCoordinator coordinator, Ticker ticker) {
        this.coordinator = coordinator;
        this.ticker = ticker;
        coordinator.addObserver(this);
        ticker.start(this::publish, TICK_INTERVAL_MILLIS);
    }

    public LiveData<ExplorationUiState> getUiState() {
        return uiState;
    }

    // --- user commands -----------------------------------------------------------------------

    public void onStopRequested() {
        coordinator.requestStop();
    }

    public void onDiscardRequested() {
        coordinator.requestDiscard();
    }

    public void onResumeRequested() {
        coordinator.requestResume();
    }

    /** Retries the final save after it failed; the session is already no longer collecting. */
    public void onRetrySaveRequested() {
        coordinator.requestStop();
    }

    // --- coordinator observer ------------------------------------------------------------------

    @Override
    public void onSessionChanged(SessionSummary changed) {
        publish();
    }

    @Override
    public void onDestinationChanged(Attraction changed) {
        destination = changed;
        publish();
    }

    @Override
    public void onSample(LocationSample sample) {
        // A fix means location is working again, so any previous unavailable reason is history.
        unavailableReason = null;
    }

    @Override
    public void onRouteChanged(RouteSnapshot changed) {
        route = changed;
        publish();
    }

    @Override
    public void onMovementChanged(MovementSnapshot changed) {
        movement = changed;
        publish();
    }

    @Override
    public void onGuidanceChanged(GuidanceSnapshot changed) {
        guidance = changed;
        publish();
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        unavailableReason = reason;
        publish();
    }

    private void publish() {
        // Always read the session back rather than caching the last published one: the tick's
        // whole job is to show a duration that grows without the session having changed.
        uiState.setValue(ExplorationUiState.from(coordinator.summary(), destination, route,
                unavailableReason, coordinator.hasStopSaveFailed(), movement, guidance));
    }

    @Override
    protected void onCleared() {
        ticker.stop();
        coordinator.removeObserver(this);
        super.onCleared();
    }
}
