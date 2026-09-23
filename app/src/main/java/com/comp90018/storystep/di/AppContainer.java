package com.comp90018.storystep.di;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.core.content.ContextCompat;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.arrival.ArrivalPolicy;
import com.comp90018.storystep.application.SensingSources;
import com.comp90018.storystep.data.local.CacheRetentionSweeper;
import com.comp90018.storystep.data.local.RoomTransactions;
import com.comp90018.storystep.data.local.StoryStepDatabase;
import com.comp90018.storystep.data.remote.ApiClientFactory;
import com.comp90018.storystep.data.remote.OverpassAttractionSource;
import com.comp90018.storystep.data.remote.RetrofitWeatherSource;
import com.comp90018.storystep.data.remote.RetryingAttractionSource;
import com.comp90018.storystep.data.remote.RetryingWeatherSource;
import com.comp90018.storystep.data.repository.CachedWeatherRepository;
import com.comp90018.storystep.data.repository.AttractionCache;
import com.comp90018.storystep.data.repository.SampleJourneys;
import com.comp90018.storystep.data.repository.CachedAttractionRepository;
import com.comp90018.storystep.data.repository.RoomSavedAttractionRepository;
import com.comp90018.storystep.data.repository.RoomHistoryRepository;
import com.comp90018.storystep.data.repository.RoomSessionStore;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.domain.attraction.AttractionFreshnessPolicy;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;
import com.comp90018.storystep.domain.repository.SessionStore;
import com.comp90018.storystep.domain.repository.WeatherRepository;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.domain.privacy.RetentionPolicy;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.platform.connectivity.AndroidConnectivityMonitor;
import com.comp90018.storystep.platform.location.AndroidLocationSource;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.platform.sensor.AndroidDeclinationSource;
import com.comp90018.storystep.platform.sensor.AndroidSensorHub;
import com.comp90018.storystep.platform.time.AndroidClock;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
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
    private final StoryStepDatabase database;
    private final AttractionCache attractionCache;
    private final AttractionRepository attractionRepository;
    private final HistoryRepository historyRepository;
    private final LocationQualityPolicy locationQualityPolicy;
    private final Clock clock;
    private final SessionStore sessionStore;
    private final SavedAttractionRepository savedAttractionRepository;
    private final WeatherRepository weatherRepository;
    private final ExplorationCoordinator explorationCoordinator;
    /** The walk being planned; process-scoped so it survives navigation between screens. */
    private final com.comp90018.storystep.application.WalkPlanner walkPlanner =
            new com.comp90018.storystep.application.WalkPlanner();
    private final com.comp90018.storystep.data.remote.WalkRouteSource walkRouteSource;
    private final CacheRetentionSweeper cacheRetentionSweeper;
    private final com.comp90018.storystep.domain.repository.ThemeRepository themeRepository;

    private final Executor mainExecutor;
    private final ExecutorService ioExecutor;

    /**
     * The production assembly: attractions come from the Room cache and sessions are durable.
     *
     * <p>Overriding the repository is left to the two constructors below so a test or a debug
     * entry point can supply a fake without also having to stand up a database.
     */
    public AppContainer(Context applicationContext) {
        this(applicationContext, null, null);
    }

    public AppContainer(Context applicationContext, AttractionRepository attractionRepository) {
        this(applicationContext, attractionRepository, null);
    }

    /**
     * @param attractionRepository an override, or null for the cached provider-backed source
     * @param historyRepository    an override, or null to read the Room session tables
     */
    public AppContainer(Context applicationContext,
                        AttractionRepository attractionRepository,
                        HistoryRepository historyRepository) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.locationQualityPolicy = LocationQualityPolicy.defaultPolicy();
        this.mainExecutor = ContextCompat.getMainExecutor(this.applicationContext);
        this.ioExecutor = Executors.newFixedThreadPool(IO_THREAD_COUNT);
        this.clock = new AndroidClock();
        // Opened here, once, because the database is process-scoped: a second instance would mean
        // a second connection pool and two views of the same file.
        this.database = StoryStepDatabase.create(this.applicationContext);
        // Three published walks to open on, once; see SampleJourneys.
        this.ioExecutor.execute(() -> SampleJourneys.seedIfNeeded(
                this.applicationContext, database, clock.wallClockMillis()));
        // One cache object behind whichever repository is wired, so the seeding rule and the
        // write policy exist once rather than once per repository.
        this.attractionCache = new AttractionCache(
                database.attractionDao(),
                database.attractionAreaDao(),
                new RoomTransactions(database),
                clock,
                SampleAttractions.get());
        this.attractionRepository = attractionRepository != null
                ? attractionRepository
                : new CachedAttractionRepository(
                        attractionCache,
                new RetryingAttractionSource(
                        new OverpassAttractionSource(
                                ApiClientFactory.createOverpassService(
                                        isDebuggable(this.applicationContext)),
                                ApiClientFactory.createWikidataService(
                                        isDebuggable(this.applicationContext)),
                                ApiClientFactory.createWikipediaService(
                                        isDebuggable(this.applicationContext)),
                                ApiClientFactory.createCommonsService(
                                        isDebuggable(this.applicationContext)),
                                ApiClientFactory.createWikidataQueryService(
                                        isDebuggable(this.applicationContext)))),
                        new AndroidConnectivityMonitor(this.applicationContext),
                        AttractionFreshnessPolicy.defaultPolicy(),
                        clock,
                        ioExecutor,
                        mainExecutor);
        // Process-scoped rather than per-screen: the detail screen, the nearby list and the saved
        // list all observe the same set of saved identifiers, and a second instance would mean a
        // second cache that no write ever reaches.
        this.savedAttractionRepository = new RoomSavedAttractionRepository(
                database.savedAttractionDao(), clock, ioExecutor, mainExecutor);
        // The app's only network client. Process-scoped so the cache, the in-flight map and the
        // single OkHttp connection pool are shared: a per-screen instance would mean each screen
        // issuing its own request for the same observation.
        this.weatherRepository = new CachedWeatherRepository(
                database.weatherCacheDao(),
                new RetryingWeatherSource(
                        new RetrofitWeatherSource(
                                ApiClientFactory.createWeatherService(
                                        isDebuggable(this.applicationContext)))),
                new AndroidConnectivityMonitor(this.applicationContext),
                WeatherFreshnessPolicy.defaultPolicy(),
                clock,
                ioExecutor,
                mainExecutor);
        // Retention is a database concern rather than a repository one: it spans three caches
        // that no single repository owns, and it must leave the user's own record alone.
        this.cacheRetentionSweeper = new CacheRetentionSweeper(
                new RoomTransactions(database),
                database.attractionDao(),
                database.attractionAreaDao(),
                database.weatherCacheDao(),
                RetentionPolicy.defaultPolicy(),
                clock);
        this.sessionStore = new RoomSessionStore(
                new RoomTransactions(database), database.sessionDao(), database.routePointDao(),
                database.visitDao(), clock, ioExecutor, mainExecutor, database.themeDao());
        this.themeRepository = new com.comp90018.storystep.data.repository.RoomThemeRepository(
                new RoomTransactions(database), database.themeDao(), database.sessionDao(),
                database.visitDao(), ioExecutor, mainExecutor);
        this.historyRepository = historyRepository != null
                ? historyRepository
                : new RoomHistoryRepository(
                        new RoomTransactions(database), database.sessionDao(),
                        database.routePointDao(), database.visitDao(), ioExecutor, mainExecutor,
                        new com.comp90018.storystep.domain.repository.SessionArtifacts() {
                            @Override
                            public void discard(String sessionId) {
                                com.comp90018.storystep.platform.media.JourneyPhotoStore
                                        .deleteAll(applicationContext, sessionId);
                            }

                            @Override
                            public void discardAll() {
                                com.comp90018.storystep.platform.media.JourneyPhotoStore
                                        .deleteEverything(applicationContext);
                            }
                        });
        // One hub for both sensors: the accelerometer is needed by motion estimation and by the
        // compass, and registering it twice would double the callback rate and the power cost for
        // readings that are identical.
        AndroidSensorHub sensorHub = new AndroidSensorHub(this.applicationContext);
        // Assembled once, here, because the coordinator must outlive every screen: it owns the
        // session and the single location subscription, and a session survives navigation.
        this.explorationCoordinator = new ExplorationCoordinator(
                new SessionStateMachine(clock),
                sessionStore,
                new AndroidLocationSource(this.applicationContext),
                new LocationSampleFilter(locationQualityPolicy),
                clock,
                new SensingSources(sensorHub, sensorHub.asHeadingSource(),
                        new AndroidDeclinationSource(),
                        new com.comp90018.storystep.platform.sensor.AndroidStepSource(
                                this.applicationContext),
                        new com.comp90018.storystep.platform.sensor.AndroidPressureSource(
                                this.applicationContext)),
                ArrivalPolicy.defaultPolicy(),
                // The session records the weather it was walked in. Passed the same process-scoped
                // repository every screen uses, so the capture is a cache read rather than an
                // extra request.
                weatherRepository);
        // The coordinator follows the plan when there is one: arriving moves it to the next stop.
        this.explorationCoordinator.setPlanner(walkPlanner);
        this.walkRouteSource = new com.comp90018.storystep.data.remote.ValhallaWalkRouteSource(
                ApiClientFactory.createValhallaService(isDebuggable(this.applicationContext)));
    }

    /**
     * Whether this install is a debug build, read from the manifest rather than {@code BuildConfig}
     * so the project does not have to turn on build-config generation for one boolean.
     *
     * <p>Only gates the queryless HTTP logging in {@code ApiClientFactory}.
     */
    private static boolean isDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public AttractionRepository getAttractionRepository() {
        return attractionRepository;
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    /**
     * The user's exploration history.
     *
     * <p>Reads the same tables {@link #getSessionStore()} writes, through its own contract: the
     * store is the live session's write path, and history is a read-and-delete path used by
     * entirely different screens.
     */
    public HistoryRepository getHistoryRepository() {
        return historyRepository;
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
    /** The walking router, or null when none is wired: plans then draw straight legs. */
    public com.comp90018.storystep.data.remote.WalkRouteSource getWalkRouteSource() {
        return walkRouteSource;
    }

    public com.comp90018.storystep.application.WalkPlanner getWalkPlanner() {
        return walkPlanner;
    }

    public ExplorationCoordinator getExplorationCoordinator() {
        return explorationCoordinator;
    }

    public SessionStore getSessionStore() {
        return sessionStore;
    }

    /**
     * The user's saved attractions.
     *
     * <p>Independent of {@link #getAttractionRepository()} on purpose: refreshing or replacing the
     * attraction cache must never remove a place the user chose to keep.
     */
    /** Removes provider copies past the D-06 retention window; see {@link CacheRetentionSweeper}. */
    public CacheRetentionSweeper getCacheRetentionSweeper() {
        return cacheRetentionSweeper;
    }

    public SavedAttractionRepository getSavedAttractionRepository() {
        return savedAttractionRepository;
    }

    /**
     * Current-area weather, cached locally.
     *
     * <p>The app's only network-backed repository. Every screen that shows weather goes through
     * this one instance, which is what makes the coalescing and freshness rules in
     * {@code CachedWeatherRepository} effective rather than per-screen.
     */
    public WeatherRepository getWeatherRepository() {
        return weatherRepository;
    }

    /**
     * The process-scoped database.
     *
     * <p>Exposed for the repositories added by later requirements to build on, not for screens:
     * a Fragment holding a DAO would be a main-thread query waiting to happen.
     */
    public StoryStepDatabase getDatabase() {
        return database;
    }

    public Clock getClock() {
        return clock;
    }

    public com.comp90018.storystep.domain.repository.ThemeRepository getThemeRepository() {
        return themeRepository;
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
