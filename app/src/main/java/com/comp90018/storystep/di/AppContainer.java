package com.comp90018.storystep.di;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.platform.location.AndroidLocationSource;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.platform.time.AndroidClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Manual dependency container for the application process.
 *
 * <p>Fragments and ViewModels receive their collaborators from here rather than constructing a
 * database, an HTTP client or a sensor implementation themselves. Swapping the in-memory
 * repository for a Room- and network-backed one is then a change to this class alone.
 *
 * <p>Assembly is deliberately manual: the project does not use a dependency-injection framework.
 */
public class AppContainer {

    /** Two threads: enough to overlap one slow query with one network call, bounded by design. */
    private static final int IO_THREAD_COUNT = 2;

    private final Context applicationContext;
    private final AttractionRepository attractionRepository;
    private final LocationQualityPolicy locationQualityPolicy;
    private final Clock clock;
    private final SessionStore sessionStore;
    private final ExplorationCoordinator explorationCoordinator;

    private final Executor mainExecutor;
    private final ExecutorService ioExecutor;

    public AppContainer(Context applicationContext) {
        this(applicationContext, new InMemoryAttractionRepository());
    }

    public AppContainer(Context applicationContext, AttractionRepository attractionRepository) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.attractionRepository = attractionRepository;
        this.locationQualityPolicy = LocationQualityPolicy.defaultPolicy();
        this.mainExecutor = ContextCompat.getMainExecutor(this.applicationContext);
        this.ioExecutor = Executors.newFixedThreadPool(IO_THREAD_COUNT);
        this.clock = new AndroidClock();
        this.sessionStore = new InMemorySessionStore(clock);
        // Assembled once, here, because the coordinator must outlive every screen: it owns the
        // session and the single location subscription, and a session survives navigation.
        this.explorationCoordinator = new ExplorationCoordinator(
                new SessionStateMachine(clock),
                sessionStore,
                new AndroidLocationSource(this.applicationContext),
                new LocationSampleFilter(locationQualityPolicy),
                clock);
    }

    public AttractionRepository getAttractionRepository() {
        return attractionRepository;
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    /**
     * Dispatcher used by asynchronous repositories to honour the main-thread callback contract
     * documented on {@code RepositoryCallback}.
     */
    public Executor getMainExecutor() {
        return mainExecutor;
    }

    /** Bounded pool for disk and network work; never used for anything that touches a View. */
    public ExecutorService getIoExecutor() {
        return ioExecutor;
    }

    public LocationQualityPolicy getLocationQualityPolicy() {
        return locationQualityPolicy;
    }

    /**
     * The process-wide session owner.
     *
     * <p>The only route to location updates. There is deliberately no accessor handing out a
     * {@link LocationSource}: a screen that could register its own would be a second, invisible
     * subscription running alongside the session's.
     */
    public ExplorationCoordinator getExplorationCoordinator() {
        return explorationCoordinator;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    public Clock getClock() {
        return clock;
    }

    /**
     * Reference position for the demonstration area, used only for pre-session browsing.
     *
     * <p>This is <b>not</b> the user's position and must never be presented as one: any screen
     * using it has to label the distances as being from the reference area. Reading it starts no
     * location updates and needs no permission.
     */
    public GeoPoint getReferenceOrigin() {
        return SampleAttractions.DEMO_ORIGIN;
    }

    /** Search radius, in metres, applied to nearby-attraction queries. Inclusive boundary. */
    public double getNearbySearchRadiusMeters() {
        return 3000.0;
    }
}