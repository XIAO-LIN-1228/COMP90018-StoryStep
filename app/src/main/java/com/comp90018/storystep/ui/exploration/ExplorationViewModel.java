package com.comp90018.storystep.ui.exploration;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.WalkPlanner;
import com.comp90018.storystep.data.remote.WalkRouteSource;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.plan.PlannedRoute;
import com.comp90018.storystep.domain.plan.WalkPlan;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.platform.location.LocationSource;

import java.util.Objects;
import java.util.concurrent.Executor;

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
public class ExplorationViewModel extends ViewModel implements ExplorationCoordinator.Observer,
        WalkPlanner.Observer {

    /** How often the duration is redrawn. Display only; nothing is persisted at this rate. */
    static final long TICK_INTERVAL_MILLIS = 1_000L;

    /** Schedules the redraw. Injected so tests can drive time instead of waiting for it. */
    public interface Ticker {

        void start(Runnable onTick, long intervalMillis);

        void stop();
    }

    private final ExplorationCoordinator coordinator;
    private final Ticker ticker;
    private final WalkPlanner planner;
    private final WalkRouteSource routeSource;
    private final Executor ioExecutor;
    private final Executor mainExecutor;
    private WalkPlan displayedPlan = WalkPlan.EMPTY;
    private GeoPoint planOrigin;
    private PlannedRoute plannedRoute;
    private int routeGeneration;
    private final MutableLiveData<ExplorationUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<ExplorationMapState> mapState = new MutableLiveData<>();

    private Attraction destination;
    private RouteSnapshot route = RouteSnapshot.empty();
    private LocationSource.Reason unavailableReason;
    private MovementSnapshot movement = MovementSnapshot.unavailable();
    private boolean checkpointFailed;
    private Visit pendingStory;
    private boolean visitSaveFailed;
    private final ArrivalFeedbackGate arrivalFeedbackGate = new ArrivalFeedbackGate();

    public ExplorationViewModel(ExplorationCoordinator coordinator) {
        this(coordinator, new HandlerTicker());
    }

    public ExplorationViewModel(ExplorationCoordinator coordinator, WalkPlanner planner,
                               WalkRouteSource routeSource, Executor ioExecutor, Executor mainExecutor) {
        this(coordinator, new HandlerTicker(), planner, routeSource, ioExecutor, mainExecutor);
    }

    ExplorationViewModel(ExplorationCoordinator coordinator, Ticker ticker) {
        this(coordinator, ticker, null, null, Runnable::run, Runnable::run);
    }

    ExplorationViewModel(ExplorationCoordinator coordinator, Ticker ticker, WalkPlanner planner,
                         WalkRouteSource routeSource, Executor ioExecutor, Executor mainExecutor) {
        this.coordinator = coordinator;
        this.ticker = ticker;
        this.planner = planner;
        this.routeSource = routeSource;
        this.ioExecutor = ioExecutor;
        this.mainExecutor = mainExecutor;
        coordinator.addObserver(this);
        if (planner != null) {
            planner.addObserver(this);
        }
        ticker.start(this::publish, TICK_INTERVAL_MILLIS);
    }

    public LiveData<ExplorationMapState> getMapState() {
        return mapState;
    }

    public LiveData<ExplorationUiState> getUiState() {
        return uiState;
    }

    /**
     * Claims the transient arrival cue for the current persisted visit.
     *
     * <p>The gate lives in the ViewModel so view recreation cannot vibrate again for a banner the
     * user has already seen. The durable visit remains the source of truth for the story itself.
     */
    public boolean claimArrivalFeedback() {
        return pendingStory != null && arrivalFeedbackGate.claim(
                pendingStory.getSessionId(), pendingStory.getAttractionId());
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

    /**
     * The user chose to open the revealed story.
     *
     * <p>Consumes the prompt but does not mark the story read — that happens when the story screen
     * actually shows it, so a navigation that never arrives does not lose the story.
     */
    public void onStoryRequested() {
        coordinator.consumeArrival();
    }

    /**
     * The user dismissed the arrival prompt.
     *
     * <p>The visit stays recorded and unread: "not now" is not "never", and the story is still
     * there to be opened from this session later.
     */
    public void onArrivalDismissed() {
        coordinator.consumeArrival();
    }

    /** Retries an arrival that could not be recorded. */
    public void onRetryVisitRequested() {
        coordinator.retryVisit();
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
        updatePlannedRoute();
        publishMap();
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
        updatePlannedRoute();
        publishMap();
    }

    @Override
    public void onMovementChanged(MovementSnapshot changed) {
        movement = changed;
        publish();
    }

    @Override
    public void onGuidanceChanged(GuidanceSnapshot changed) {
        publish();
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        unavailableReason = reason;
        publish();
    }

    @Override
    public void onDurabilityChanged(boolean failed) {
        checkpointFailed = failed;
        publish();
    }

    @Override
    public void onPendingStoryChanged(Visit changed) {
        pendingStory = changed;
        publish();
    }

    @Override
    public void onVisitSaveFailed(boolean failed) {
        visitSaveFailed = failed;
        publish();
    }

    private void publish() {
        // Always read the session back rather than caching the last published one: the tick's
        // whole job is to show a duration that grows without the session having changed.
        uiState.setValue(ExplorationUiState.from(coordinator.summary(), destination, route,
                unavailableReason, coordinator.hasStopSaveFailed(), checkpointFailed, movement,
                coordinator.guidanceSnapshot(), pendingStory, visitSaveFailed,
                coordinator.stepsWalked(), coordinator.ascentMetres()));
    }

    /** Heading and timer ticks must not rebuild the complete route overlay. */
    private void publishMap() {
        mapState.setValue(ExplorationMapState.from(
                destination == null ? null : destination.getLocation(), route,
                plannedRoute, displayedPlan.getStops()));
    }

    @Override public void onPlanChanged(WalkPlan ignored) {
        updatePlannedRoute();
        publishMap();
    }

    private void updatePlannedRoute() {
        WalkPlan next = destination == null ? WalkPlan.EMPTY
                : planner != null && planner.plan().contains(destination.getId())
                        ? planner.plan() : WalkPlan.EMPTY.with(destination);
        // The first accepted point anchors this walk. Do not reroute on every GPS reading.
        GeoPoint origin = route.getPoints().isEmpty() ? null : route.getPoints().get(0).getPosition();
        if (next.equals(displayedPlan) && Objects.equals(origin, planOrigin)) {
            return;
        }
        displayedPlan = next;
        planOrigin = origin;
        int generation = ++routeGeneration;
        plannedRoute = next.isEmpty() ? null : PlannedRoute.straight(next, origin);
        if (next.isEmpty() || origin == null || routeSource == null) {
            return;
        }
        ioExecutor.execute(() -> {
            PlannedRoute result;
            try {
                result = routeSource.walkThrough(next.path(origin));
            } catch (RuntimeException unavailable) {
                result = null;
            }
            PlannedRoute answer = result;
            mainExecutor.execute(() -> {
                if (generation != routeGeneration || answer == null) {
                    return;
                }
                plannedRoute = answer;
                publishMap();
            });
        });
    }

    @Override
    protected void onCleared() {
        routeGeneration++;
        if (planner != null) {
            planner.removeObserver(this);
        }
        ticker.stop();
        coordinator.removeObserver(this);
        super.onCleared();
    }
}
