package com.comp90018.storystep.application;

import com.comp90018.storystep.domain.arrival.ArrivalDetector;
import com.comp90018.storystep.domain.arrival.ArrivalPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.geo.RouteAccumulator;
import com.comp90018.storystep.domain.guidance.CircularSmoother;
import com.comp90018.storystep.domain.guidance.DirectionGuidance;
import com.comp90018.storystep.domain.guidance.GuidanceSnapshot;
import com.comp90018.storystep.domain.guidance.TrueHeading;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MotionSample;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.RestoredSession;
import com.comp90018.storystep.domain.model.RoutePoint;
import com.comp90018.storystep.domain.model.RouteSnapshot;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.motion.MovementClassifier;
import com.comp90018.storystep.domain.model.Visit;
import com.comp90018.storystep.domain.repository.DuplicateVisitException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.platform.sensor.HeadingAccuracy;
import com.comp90018.storystep.platform.sensor.HeadingSource;
import com.comp90018.storystep.platform.sensor.MotionSource;

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

        /** The movement estimate and its accumulated durations. */
        default void onMovementChanged(MovementSnapshot movement) {
        }

        /** Where the destination lies, and which way to turn if a compass is usable. */
        default void onGuidanceChanged(GuidanceSnapshot guidance) {
        }

        /**
         * True when the last attempt to save progress failed and the session is running on
         * unsaved data.
         *
         * <p>Separate from a stop failure: the session is still collecting normally, but an
         * interruption now would lose more than the checkpoint policy promises, and the user is
         * entitled to know that before they walk another kilometre.
         */
        default void onDurabilityChanged(boolean checkpointFailed) {
        }

        /**
         * The recorded visit whose story has not been offered on screen yet, or null when there
         * is none to offer.
         *
         * <p>Only ever carries a visit that is already durably stored, so nothing here can claim
         * an arrival that was not saved.
         */
        default void onPendingStoryChanged(Visit pendingStory) {
        }

        /**
         * True when recording an arrival failed and nothing was written.
         *
         * <p>Distinct from a checkpoint failure because the remedy differs: this one has a visit
         * waiting to be retried, and until it succeeds the app must not suggest the place has
         * been visited.
         */
        default void onVisitSaveFailed(boolean failed) {
        }
    }

    /** A heading older than this describes where the phone used to point, not where it points. */
    private static final long HEADING_STALE_AFTER_MILLIS = 5_000L;

    private final SessionStateMachine machine;
    private final SessionStore sessionStore;
    private final LocationSource locationSource;
    private final LocationSampleFilter sampleFilter;
    private final Clock clock;
    private final SensingSources sensing;

    /**
     * Owned here rather than injected: it holds the live session's route, so a second instance
     * would mean a second, disagreeing answer to how far the user has walked.
     */
    private final RouteAccumulator route = new RouteAccumulator();

    /** Same reason as the route: one session, one answer to how long the user has been walking. */
    private final MovementClassifier movement = new MovementClassifier();

    private final CircularSmoother headingSmoother = new CircularSmoother();

    /**
     * Owned here for the same reason as the route: the candidate stay is part of the live session,
     * and a second detector would be watching a different approach to the same place.
     */
    private final ArrivalDetector arrival;

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

    /**
     * How many of the accumulated route points have been confirmed written.
     *
     * <p>Only advanced by a successful append, so a failed checkpoint simply re-offers the same
     * points next time. The store ignores ones it already holds, which is what makes the retry
     * safe rather than duplicating the trace.
     */
    private int persistedRoutePointCount;

    /** True while the most recent checkpoint attempt is still unrecovered. */
    private boolean checkpointFailed;

    /** Monotonic time of the last heading, used to decide whether the compass has gone quiet. */
    private long lastHeadingMonotonicMillis;

    private boolean headingUsable;

    /**
     * The recorded visit whose story has not been presented in this process yet.
     *
     * <p>Process-scoped on purpose, and paired with the durable {@code unread} flag rather than
     * replacing it. The flag answers "has the user read this story", which must survive the app
     * being killed; this field answers "have we already offered it on screen", which must not,
     * because otherwise rotating the phone would announce the same arrival twice.
     */
    private Visit pendingStory;

    /**
     * The visit a failed write was trying to record, kept so Retry has something to retry.
     *
     * <p>Non-null exactly while the failure is unrecovered, which is also what the screen shows.
     */
    private Visit failedVisit;

    /** Location only. A device with no motion or compass sensing is a supported configuration. */
    public ExplorationCoordinator(SessionStateMachine machine,
                                  SessionStore sessionStore,
                                  LocationSource locationSource,
                                  LocationSampleFilter sampleFilter,
                                  Clock clock) {
        this(machine, sessionStore, locationSource, sampleFilter, clock, SensingSources.none());
    }

    public ExplorationCoordinator(SessionStateMachine machine,
                                  SessionStore sessionStore,
                                  LocationSource locationSource,
                                  LocationSampleFilter sampleFilter,
                                  Clock clock,
                                  SensingSources sensing) {
        this(machine, sessionStore, locationSource, sampleFilter, clock, sensing,
                ArrivalPolicy.defaultPolicy());
    }

    public ExplorationCoordinator(SessionStateMachine machine,
                                  SessionStore sessionStore,
                                  LocationSource locationSource,
                                  LocationSampleFilter sampleFilter,
                                  Clock clock,
                                  SensingSources sensing,
                                  ArrivalPolicy arrivalPolicy) {
        this.machine = machine;
        this.sessionStore = sessionStore;
        this.locationSource = locationSource;
        this.sampleFilter = sampleFilter;
        this.clock = clock;
        this.sensing = sensing;
        this.arrival = new ArrivalDetector(arrivalPolicy);
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
        observer.onMovementChanged(movementSnapshot());
        observer.onGuidanceChanged(guidanceSnapshot());
        observer.onDurabilityChanged(checkpointFailed);
        observer.onPendingStoryChanged(pendingStory);
        observer.onVisitSaveFailed(failedVisit != null);
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

    /**
     * The movement estimate, or {@code UNAVAILABLE} when this device cannot produce one.
     *
     * <p>Reported as unavailable rather than unknown when the sensor is absent, so the screen can
     * say "this phone has no motion sensor" instead of leaving the user waiting for a reading that
     * will never arrive.
     */
    public MovementSnapshot movementSnapshot() {
        return sensing.hasMotion() ? movement.snapshot() : MovementSnapshot.unavailable();
    }

    /** Where the destination lies relative to the user, as far as current data can say. */
    public GuidanceSnapshot guidanceSnapshot() {
        LocationSample last = route.snapshot().getLastSample();
        GeoPoint from = last == null ? null : last.getPosition();
        GeoPoint target = destination == null ? null : destination.getLocation();
        return DirectionGuidance.compute(from, target, currentTrueHeading());
    }

    /**
     * The smoothed true-north heading, or null when it cannot be trusted.
     *
     * <p>Null covers four separate situations — no compass, an unreliable reading, no fix to derive
     * declination from, and a heading that has gone stale — and they collapse here on purpose: the
     * only thing the arrow needs to know is whether it may be drawn.
     */
    private Double currentTrueHeading() {
        if (!headingUsable || !headingSmoother.hasValue()) {
            return null;
        }
        if (clock.monotonicMillis() - lastHeadingMonotonicMillis > HEADING_STALE_AFTER_MILLIS) {
            return null;
        }
        return headingSmoother.value();
    }

    /** The attraction being explored towards, or null when the user has not chosen one. */
    public Attraction getDestination() {
        return destination;
    }

    /** True when the final save failed and the user still needs to retry it. */
    public boolean hasStopSaveFailed() {
        return machine.hasStopSaveFailed();
    }

    /**
     * True when progress could not be saved and the session is running on unsaved data.
     *
     * <p>Cleared by the next checkpoint that succeeds. No user action is required — the next one
     * is attempted automatically — but the screen must be able to say so, because the durability
     * the checkpoint policy promises no longer holds.
     */
    public boolean hasCheckpointFailed() {
        return checkpointFailed;
    }

    /**
     * The stored visit whose story has not been offered on screen yet, or null when there is none.
     */
    public Visit getPendingStory() {
        return pendingStory;
    }

    /**
     * Marks the pending story as offered, so it is not announced again in this process.
     *
     * <p>Deliberately does not mark it read. Dismissing the prompt means "not now", and the story
     * stays recoverable; only actually opening it is reading it.
     */
    public void consumeArrival() {
        if (pendingStory == null) {
            return;
        }
        pendingStory = null;
        publishPendingStory();
    }

    /** True when an arrival could not be recorded and the user still needs to retry it. */
    public boolean hasVisitSaveFailed() {
        return failedVisit != null;
    }

    /**
     * Retries the arrival that failed to save.
     *
     * <p>Safe to call repeatedly: a retry that discovers the visit is already there is treated as
     * the success it is, rather than recording a second one.
     */
    public void retryVisit() {
        if (failedVisit == null) {
            return;
        }
        Visit retried = failedVisit;
        failedVisit = null;
        publishVisitSaveFailed();
        storeVisit(retried);
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
     * <p>The arrival candidate is reset, since a partially satisfied dwell towards the old target
     * says nothing about the new one — and carrying it over would let changing destination
     * manufacture an instant arrival at a place the user has not reached.
     */
    public void selectDestination(Attraction attraction) {
        destination = attraction;
        arrival.setTarget(attraction);
        publishDestination();
        // The bearing is to the new place from the moment it is chosen; the arrow must not keep
        // pointing at the old one until the next fix happens to arrive.
        publishGuidance();
        persistDestination();
    }

    /**
     * Stores the destination snapshot against the running session, if there is one.
     *
     * <p>A destination chosen with no session yet is not written: there is no record to attach it
     * to. It is persisted at the first checkpoint after the session is created instead.
     */
    private void persistDestination() {
        String sessionId = machine.getSessionId();
        if (sessionId == null) {
            return;
        }
        sessionStore.saveDestination(sessionId, destination, new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Nothing to show: the destination is already on screen.
            }

            @Override
            public void onError(Throwable error) {
                reportDurability(true);
            }
        });
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
                movement.clear();
                persistedRoutePointCount = 0;
                // Arrival state belongs to a session. A story left unread by the previous walk is
                // still in history, but it is not this walk's news, and a failed write against a
                // session that has ended can never be retried successfully.
                pendingStory = null;
                failedVisit = null;
                reportDurability(false);
                publishPendingStory();
                publishVisitSaveFailed();
                lastCheckpointMonotonicMillis = clock.monotonicMillis();
                // A destination chosen before Start had no session to attach itself to; now it
                // does, so the record knows where the user was heading from its first moment.
                persistDestination();
                publishRoute();
                publishMovement();
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
                // A discarded session must leave nothing behind, including its route and the time
                // it recorded as walked.
                route.reset();
                movement.clear();
                persistedRoutePointCount = 0;
                // The session's visits went with it; an offer to read a story from a walk that no
                // longer exists would be pointing at nothing.
                pendingStory = null;
                failedVisit = null;
                reportDurability(false);
                publishPendingStory();
                publishVisitSaveFailed();
                publishSession();
                publishRoute();
                publishMovement();
                publishGuidance();
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
        // position recorded before the pause. The motion windows and the compass smoothing are
        // reset for the same reason: readings from before the gap describe a different walk.
        sampleFilter.reset();
        movement.reset();
        arrival.reset();
        headingSmoother.reset();
        headingUsable = false;
        subscription = new Subscription(++generation);
        locationSource.startUpdates(subscription);
        // A missing sensor is not an error and must not stop the session: the location half of the
        // session is exactly as useful on a phone with no compass.
        if (sensing.hasMotion()) {
            sensing.getMotionSource().startUpdates(subscription);
        }
        if (sensing.hasHeading()) {
            sensing.getHeadingSource().startUpdates(subscription);
        }
    }

    private void closeSubscription() {
        if (subscription == null) {
            return;
        }
        subscription = null;
        // Bump before unregistering: anything already queued for the old generation is now stale.
        generation++;
        locationSource.stopUpdates();
        if (sensing.getMotionSource() != null) {
            sensing.getMotionSource().stopUpdates();
        }
        if (sensing.getHeadingSource() != null) {
            sensing.getHeadingSource().stopUpdates();
        }
        headingUsable = false;
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

    /**
     * Writes the session's state and any route recorded since the last successful append.
     *
     * <p>Two writes rather than one because they carry different things: the summary is a small
     * record that is rewritten each time, while the route only ever grows and is appended to. A
     * checkpoint that rewrote the whole trace would get slower the longer the walk went on.
     *
     * <p>A failure here does not interrupt the session. Collection is working; only durability is
     * degraded, the same points are re-offered next time, and the screen is told so the user is
     * not left believing progress is safely stored when it is not.
     */
    private void checkpoint() {
        if (!machine.getState().hasSession()) {
            return;
        }
        String sessionId = machine.getSessionId();
        if (sessionId == null) {
            return;
        }
        sessionStore.checkpoint(summary(), new RepositoryCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                appendPendingRoute(sessionId);
            }

            @Override
            public void onError(Throwable error) {
                reportDurability(true);
            }
        });
    }

    /**
     * Appends the points the store has not confirmed yet.
     *
     * <p>{@code persistedRoutePointCount} advances only on success, so an unconfirmed point is
     * always re-sent rather than assumed written.
     */
    private void appendPendingRoute(String sessionId) {
        RouteSnapshot snapshot = route.snapshot();
        List<RoutePoint> points = snapshot.getPoints();
        if (persistedRoutePointCount > points.size()) {
            // The route was reset under a session that is somehow still running; start again from
            // what actually exists rather than skipping points forever.
            persistedRoutePointCount = 0;
        }
        int firstSequence = persistedRoutePointCount;
        List<RoutePoint> pending = new ArrayList<>(points.subList(firstSequence, points.size()));
        int confirmed = points.size();
        sessionStore.appendRoute(sessionId, firstSequence, pending,
                snapshot.getTotalDistanceMeters(), snapshot.getSegmentCount(),
                new RepositoryCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        persistedRoutePointCount = confirmed;
                        reportDurability(false);
                    }

                    @Override
                    public void onError(Throwable error) {
                        reportDurability(true);
                    }
                });
    }

    /**
     * Records the arrival the detector just reported, if there is a session to record it against.
     *
     * <p>The name and story are copied from the destination as it is right now, so the visit stays
     * readable after the attraction cache is refreshed or the place disappears from the provider.
     */
    private void recordArrival() {
        String sessionId = machine.getSessionId();
        if (sessionId == null || destination == null) {
            return;
        }
        storeVisit(new Visit(
                sessionId,
                destination.getId(),
                clock.wallClockMillis(),
                destination.getName(),
                destination.getDescription(),
                true));
    }

    /**
     * Writes one visit and reacts to the three outcomes that mean different things.
     *
     * <p>A duplicate is success: the visit is already recorded, and the only wrong response would
     * be to announce the same arrival a second time. A genuine failure wrote nothing, so it is
     * reported as a failure and never as a saved visit — the user is offered a retry instead.
     */
    private void storeVisit(Visit visit) {
        sessionStore.recordVisit(visit, new RepositoryCallback<Visit>() {
            @Override
            public void onSuccess(Visit recorded) {
                pendingStory = recorded;
                publishPendingStory();
            }

            @Override
            public void onError(Throwable error) {
                if (error instanceof DuplicateVisitException) {
                    return;
                }
                failedVisit = visit;
                publishVisitSaveFailed();
            }
        });
    }

    private void reportDurability(boolean failed) {
        if (checkpointFailed == failed) {
            return;
        }
        checkpointFailed = failed;
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onDurabilityChanged(failed);
        }
    }

    /**
     * Adopts the unfinished session left behind by a process that ended, if there is one.
     *
     * <p>Called once at application start. The restored session is always paused and registers
     * nothing: the app reopening is not the user asking to continue, and treating it as such would
     * be exactly the background collection this project does not do.
     *
     * <p>A failure is deliberately silent. The app simply starts with no session, which is the
     * same state it would have had if there were nothing to restore.
     */
    public void restoreInterruptedSession() {
        if (machine.getState() != SessionState.IDLE) {
            return;
        }
        sessionStore.findUnfinished(new RepositoryCallback<RestoredSession>() {
            @Override
            public void onSuccess(RestoredSession restored) {
                if (restored == null
                        || restored.getSummary().getState() == SessionState.COMPLETED) {
                    return;
                }
                if (!machine.restore(restored.getSummary()).isChanged()) {
                    // A session was started while the read was in flight; that one is the real
                    // session and must not be replaced by a stale record.
                    return;
                }
                RouteSnapshot storedRoute = restored.getRoute();
                route.restore(storedRoute.getPoints(), storedRoute.getTotalDistanceMeters(),
                        storedRoute.getSegmentCount());
                persistedRoutePointCount = storedRoute.getPoints().size();
                // The checkpoint interval is measured from now, not from whenever the previous
                // process last wrote; a monotonic reading from before the relaunch means nothing
                // here.
                lastCheckpointMonotonicMillis = clock.monotonicMillis();
                destination = restored.getDestination();
                arrival.setTarget(destination);
                movement.clear();
                headingSmoother.reset();
                headingUsable = false;
                // A story the user never got to read is recovered by reading the visit back, not
                // by detecting the arrival again: the row already exists and must stay unique.
                restorePendingStory(restored.getSummary().getId());
                publishSession();
                publishDestination();
                publishRoute();
                publishMovement();
                publishGuidance();
            }

            @Override
            public void onError(Throwable error) {
                // Nothing to restore that can be trusted; the app stays idle.
            }
        });
    }

    /**
     * Re-offers the restored session's unread story, if it has one.
     *
     * <p>A failure is silent: not finding a story to re-offer leaves the session exactly as
     * usable as it was, and the visit itself is untouched either way.
     */
    private void restorePendingStory(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionStore.findUnreadVisit(sessionId, new RepositoryCallback<Visit>() {
            @Override
            public void onSuccess(Visit unread) {
                if (unread == null || !unread.getSessionId().equals(machine.getSessionId())) {
                    return;
                }
                pendingStory = unread;
                publishPendingStory();
            }

            @Override
            public void onError(Throwable error) {
                // Nothing to re-offer.
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

    private void publishMovement() {
        MovementSnapshot snapshot = movementSnapshot();
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onMovementChanged(snapshot);
        }
    }

    private void publishGuidance() {
        GuidanceSnapshot snapshot = guidanceSnapshot();
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onGuidanceChanged(snapshot);
        }
    }

    private void publishPendingStory() {
        Visit snapshot = pendingStory;
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onPendingStoryChanged(snapshot);
        }
    }

    private void publishVisitSaveFailed() {
        boolean failed = failedVisit != null;
        for (Observer observer : new ArrayList<>(observers)) {
            observer.onVisitSaveFailed(failed);
        }
    }

    /**
     * One registration's callbacks, tagged with the generation that created it.
     *
     * <p>A separate object per registration rather than the coordinator implementing the listener
     * itself: it is the only way to tell a callback from the current subscription apart from one
     * queued by a subscription that has since been replaced.
     */
    private final class Subscription
            implements LocationSource.Listener, MotionSource.Listener, HeadingSource.Listener {

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
            // Offered before the route filter, not after it. That filter discards movement below
            // its minimum displacement as receiver noise, which is precisely what standing at the
            // destination produces — behind it, the dwell would stop accumulating at the moment
            // the user actually arrives. The detector applies its own, stricter, quality rules.
            if (access == LocationAccess.PRECISE
                    && arrival.offer(sample, now).isArrival()) {
                recordArrival();
            }
            if (!sampleFilter.offer(sample, now).isAccepted()) {
                // A rejected sample is not an error: the next fix is usually fine, and showing a
                // failure on every noisy reading is worse than slightly older distances.
                return;
            }
            route.offer(sample, machine.summary().getSegment());
            publishSample(sample);
            publishRoute();
            // Moving changes the bearing to the destination even when the phone has not turned.
            publishGuidance();
            checkpointIfDue(now);
        }

        @Override
        public void onMotionSample(MotionSample sample) {
            if (isStale() || !machine.getState().isCollecting()) {
                return;
            }
            movement.offer(sample);
            publishMovement();
        }

        @Override
        public void onHeadingChanged(float magneticHeadingDegrees, HeadingAccuracy accuracy) {
            if (isStale() || !machine.getState().isCollecting()) {
                return;
            }
            if (!accuracy.isUsable()) {
                // Keep the smoothed history but stop trusting it: an uncalibrated compass points
                // somewhere plausible and wrong, which is worse than showing no arrow at all.
                headingUsable = false;
                publishGuidance();
                return;
            }
            LocationSample last = route.snapshot().getLastSample();
            Float declination = last == null
                    ? null
                    : sensing.declinationAt(last.getPosition(), last.getWallClockMillis());
            if (declination == null) {
                // Without a position there is no declination, and without declination a magnetic
                // heading is not a true heading. Reporting it as one would be a silent lie of
                // roughly eleven degrees in Melbourne, and far more in other parts of the world.
                headingUsable = false;
                publishGuidance();
                return;
            }
            headingSmoother.offer(TrueHeading.degrees(magneticHeadingDegrees, declination));
            lastHeadingMonotonicMillis = clock.monotonicMillis();
            headingUsable = true;
            publishGuidance();
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
