package com.comp90018.storystep.di;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.platform.location.AndroidLocationSource;
import com.comp90018.storystep.platform.location.LocationSource;

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
     * A new foreground location adapter.
     *
     * <p>Returned per caller rather than shared, because each instance owns one platform
     * registration and its lifetime is the screen that started it. Once the exploration
     * coordinator exists it becomes the single owner and this becomes an internal detail.
     */
    public LocationSource createLocationSource() {
        return new AndroidLocationSource(applicationContext);
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