package com.comp90018.storystep.application;

import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.geo.RouteAccumulator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the active exploration session and, with it, the only location subscription in the app.
 *
 * <p>One instance per process. Screens ask it to start, resume or stop and observe what it
 * publishes; none of them registers a location source itself. That single-owner rule is what stops
 * the nearby list, and later the dashboard, from each opening their own GPS listener and doubling
 * the battery cost for one user-visible session.
 *
 * <p>Collection is strictly foreground. Losing visibility, permission or a provider pauses the
 * session and unregisters immediately; only an explicit Resume starts it again. Returning to the
 * app deliberately does <b>not</b> resume on its own — the user asked for a session and should be
 * the one who decides it continues.
 *
 * <p>Main-thread only, matching the callback contract of the repositories it uses.
 */
public final class ExplorationCoordinator {

    /** No more than this much active progress may go unsaved while a session runs. */
    private static final long CHECKPOINT_INTERVAL_MILLIS = 5_000L;

    /** What a screen needs to render the session. */
    public interface Observer {

        void onSessionChanged(SessionSummary summary);

        /** A sample that passed the quality policy. Rejected samples are never published. */
        void onSample(LocationSample sample);

        /** Location cannot currently produce a usable position. */
        void onLocationUnavailable(LocationSource.Reason reason);

        /**
         * The attraction the user is exploring towards, or null when none is selected.
         *
         * <p>Default no-op: screens that only care about whether a session is running, such as the
         * nearby list, have nothing to do with a destination change.
         */
        default void onDestinationChanged(Attraction destination) {
        }

        /** Travelled distance and the sampled trace after an accepted fix. */
        default void onRouteChanged(RouteSnapshot route) {
        }
    }

    private final SessionStateMachine machine;
    private final SessionStore sessionStore;
    private final LocationSource locationSource;
    private final LocationSampleFilter sampleFilter;
    private final Clock clock;

    /**
     * Owned here rather than injected: it holds the live session's route, so a second instance
     * would mean a second, disagreeing answer to how far the user has walked.
     */
    private final RouteAccumulator route = new RouteAccumulator();

    private final List<Observer> observers = new ArrayList<>(2);

    /**
     * Incremented on every registration. A callback carrying an older value belongs to a
     * subscription that has been torn down and is discarded, so a fix already queued on the main
     * looper when the user pressed Stop cannot reach a session that has moved on.
     */
    private int generation;

    private Subscription subscription;

    private boolean appVisible;

    private LocationAccess access = LocationAccess.NOT_REQUESTED;

    private Attraction destination;

    /**
     * Monotonic time of the last checkpoint, so an active session writes progress at a bounded
     * interval instead of on every fix.
     */
    private long lastCheckpointMonotonicMillis;

    public ExplorationCoordinator(SessionStateMachine machine,
                                  SessionStore sessionStore,
                                  LocationSource locationSource,
                                  LocationSampleFilter sampleFilter,
                                  Clock clock) {
        this.machine = machine;
        this.sessionStore = sessionStore;
        this.locationSource = locationSource;
        this.sampleFilter = sampleFilter;
        this.clock = clock;
    }

    // --- observers ---------------------------------------------------------------------------

    /** Registers {@code observer} and immediately gives it the current session state. */
    public void addObserver(Observer observer) {
        if (observer == null) {
            throw new IllegalArgumentException("observer must not be null");
        }
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
        observer.onSessionChanged(summary());
        observer.onDestinationChanged(destination);
        observer.onRouteChanged(route.snapshot());
    }

    public void removeObserver(Observer observer) {
        observers.remove(observer);
    }

    public SessionSummary summary() {
        return machine.summary();
    }

    /** Travelled distance and the sampled trace of the current session. */
    public RouteSnapshot routeSnapshot() {
        return route.snapshot();
    }

    /** The attraction being explored towards, or null when the user has not chosen one. */
    public Attraction getDestination() {
        return destination;
    }

    /** True when the final save failed and the user still needs to retry it. */
    public boolean hasStopSaveFailed() {
        return machine.hasStopSaveFailed();
    }

    /** The permission level last reported to the coordinator. */
    public LocationAccess getAccess() {
        return access;
    }

    // --- user commands -----------------------------------------------------------------------

    /**
     * Chooses, or changes, the attraction the user is heading towards.
     *
     * <p>Safe at any point in a session. Changing the destination mid-session deliberately touches
     * nothing else: the same session keeps its id, its segment and every metre already walked,
     * because the user changed their mind about where to go, not about whether they are exploring.
     *
     * <p>REQ-12 will additionally reset the arrival candidate here, since a partially satisfied
     * dwell towards the old target says nothing about the new one.
     */
    public void selectDestination(Attraction attraction) {
        destination = attraction;
        publishDestination();
    }

    /** Selects {@code destination} and starts, for entry points that begin from a chosen place. */
    public void requestStart(Attraction attraction) {
        selectDestination(attraction);
        requestStart();
    }

    /** The user pressed Start. A second press while starting or running does nothing. */
    public void requestStart() {
        if (!machine.start().isChanged()) {
            return;
        }
        publishSession();
        if (!canCollect()) {
            // Nothing was created, so there is no half-started session to explain away.
            machine.onStartFailed();
            publishSession();
            return;
        }
        sessionStore.create(new RepositoryCallback<SessionSummary>() {
            @Override
            public void onSuccess(SessionSummary created) {
                if (!machine.onSessionCreated(created.getId()).isChanged()) {
                    return;
                }
                // A new session starts from zero. Reset happens here rather than on Stop so that a
                // finished route stays readable to the summary screen until the next session.
                route.reset();
                lastCheckpointMonotonicMillis = clock.monotonicMillis();
                publishRoute();
                // Conditions can change while the record is being written; the session now exists,
                // so the honest outcome is a paused session rather than a cancelled start.
                PauseReason blocked = blockingReason();
                if (blocked != null) {
                    machine.pause(blocked);
                } else {
                    openSubscription();
                }
                publishSession();
            }

            @Override
            public void onError(Throwable error) {
                machine.onStartFailed();
                publishSession();
            }
        });
    }

    /**
     * The user pressed Resume. Ignored unless the app is visible and location is available again,
     * because resuming into an unusable state would only pause the session straight back.
     */
    public void requestResume() {
        if (!canCollect()) {
            return;
        }
        if (!machine.resume().isChanged()) {
            return;
        }
        openSubscription();
        checkpoint();
        publishSession();
    }

    /** The user pressed Stop. Sources are released before the save is attempted. */
    public void requestStop() {
        if (!machine.stop().isChanged()) {
            return;
        }
        closeSubscription();
        publishSession();
        sessionStore.finish(summary(), new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                machine.onStopPersisted();
                publishSession();
            }

            @Override
            public void onError(Throwable error) {
                // Collection has already ended; the session stays STOPPING so the screen can
                // offer a retry rather than claiming a completed session that was never saved.
                machine.onStopFailed();
                publishSession();
            }
        });
    }

    /**
     * Ends collection and removes the unfinished session instead of saving it to journey history.
     */
    public void requestDiscard() {
        if (!machine.stop().isChanged()) {
            return;
        }
        closeSubscription();
        String sessionId = machine.getSessionId();
        publishSession();
        sessionStore.discard(sessionId, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                machine.onDiscarded();
                // A discarded session must leave nothing behind, including its route.
                route.reset();
                publishSession();
                publishRoute();
            }

            @Override
            public void onError(Throwable error) {
                machine.onStopFailed();
                publishSession();
            }
        });
    }

    // --- environment -------------------------------------------------------------------------

    /**
     * Reports whether any part of the app is on screen.
     *
     * <p>Becoming visible intentionally does nothing: continuing requires an explicit Resume.
     */
    public void onVisibilityChanged(boolean visible) {
        appVisible = visible;
        if (!visible) {
            pauseFor(PauseReason.APP_NOT_VISIBLE);
        }
    }

    /** Reports the current permission level. Losing access mid-session pauses it. */
    public void onLocationAccessChanged(LocationAccess newAccess) {
        if (newAccess == null) {
            throw new IllegalArgumentException("access must not be null");
        }
        access = newAccess;
        if (!newAccess.isGranted()) {
            pauseFor(PauseReason.PERMISSION_LOST);
        }
    }

    // --- internals ---------------------------------------------------------------------------

    private void pauseFor(PauseReason reason) {
        if (!machine.pause(reason).isChanged()) {
            return;
        }
        closeSubscription();
        checkpoint();
        publishSession();
    }

    private boolean canCollect() {
        return appVisible && access.isGranted();
    }

    private PauseReason blockingReason() {
        if (!appVisible) {
            return PauseReason.APP_NOT_VISIBLE;
        }
        if (!access.isGranted()) {
            return PauseReason.PERMISSION_LOST;
        }
        return null;
    }

    private void openSubscription() {
        closeSubscription();
        // A resumed session starts a new segment, so its first fix must not be compared against a
        // position recorded before the pause.
        sampleFilter.reset();
        subscription = new Subscription(++generation);
        locationSource.startUpdates(subscription);
    }

    private void closeSubscription() {
        if (subscription == null) {
            return;
        }
        subscription = null;
        // Bump before unregistering: anything already queued for the old generation is now stale.
        generation++;
        locationSource.stopUpdates();
    }

    /**
     * Checkpoints at a bounded interval rather than on every fix, so an interruption loses at most
     * a few seconds of progress without turning each location callback into a write.
     */
    private void checkpointIfDue(long nowMonotonicMillis) {
        if (nowMonotonicMillis - lastCheckpointMonotonicMillis < CHECKPOINT_INTERVAL_MILLIS) {
            return;
        }
        lastCheckpointMonotonicMillis = nowMonotonicMillis;
        checkpoint();
    }

    private void checkpoint() {
        if (!machine.getState().hasSession()) {
            return;
        }
        sessionStore.checkpoint(summary(), new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Nothing to show: a checkpoint is a durability measure, not a user action.
            }

            @Override
            public void onError(Throwable error) {
                // Surfacing and retrying checkpoint failures is REQ-15's durability work; losing
                // one checkpoint must not interrupt a session that is otherwise running fine.
            }
        });
    }

    private void publishSession() {
        SessionSummary snapshot = summary();
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onSessionChanged(snapshot);
        }
    }

    private void publishSample(LocationSample sample) {
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onSample(sample);
        }
    }

    private void publishDestination() {
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onDestinationChanged(destination);
        }
    }

    private void publishRoute() {
        RouteSnapshot snapshot = route.snapshot();
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onRouteChanged(snapshot);
        }
    }

    private void publishUnavailable(LocationSource.Reason reason) {
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onLocationUnavailable(reason);
        }
    }

    /**
     * One registration's callbacks, tagged with the generation that created it.
     *
     * <p>A separate object per registration rather than the coordinator implementing the listener
     * itself: it is the only way to tell a callback from the current subscription apart from one
     * queued by a subscription that has since been replaced.
     */
    private final class Subscription implements LocationSource.Listener {

        private final int ownGeneration;

        Subscription(int ownGeneration) {
            this.ownGeneration = ownGeneration;
        }

        @Override
        public void onLocationChanged(LocationSample sample) {
            if (isStale() || !machine.getState().isCollecting()) {
                return;
            }
            long now = clock.monotonicMillis();
            if (!sampleFilter.offer(sample, now).isAccepted()) {
                // A rejected sample is not an error: the next fix is usually fine, and showing a
                // failure on every noisy reading is worse than slightly older distances.
                return;
            }
            route.offer(sample, machine.summary().getSegment());
            publishSample(sample);
            publishRoute();
            checkpointIfDue(now);
        }

        @Override
        public void onLocationUnavailable(LocationSource.Reason reason) {
            if (isStale()) {
                return;
            }
            if (reason == LocationSource.Reason.PROVIDER_DISABLED) {
                pauseFor(PauseReason.PROVIDER_LOST);
            }
            publishUnavailable(reason);
        }

        private boolean isStale() {
            return ownGeneration != generation;
        }
    }
}
