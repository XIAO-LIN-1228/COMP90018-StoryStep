package com.comp90018.storystep.di;

import android.content.Context;

import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.InMemoryJourneyRepository;
import com.comp90018.storystep.data.repository.SampleAttractions;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.JourneyRepository;

/**
 * Manual dependency container for the application process.
 *
 * <p>Fragments and ViewModels receive their collaborators from here rather than constructing a
 * database, an HTTP client or a sensor implementation themselves. Swapping the Stage 1 in-memory
 * repository for a Room- and network-backed one is then a change to this class alone.
 *
 * <p>Assembly is deliberately manual: the project does not use a dependency-injection framework.
 */
public class AppContainer {

    private final Context applicationContext;
    private final AttractionRepository attractionRepository;
    private final JourneyRepository journeyRepository;

    public AppContainer(Context applicationContext) {
        this(applicationContext, new InMemoryAttractionRepository(),
                new InMemoryJourneyRepository());
    }

    public AppContainer(Context applicationContext, AttractionRepository attractionRepository) {
        this(applicationContext, attractionRepository, new InMemoryJourneyRepository());
    }

    public AppContainer(Context applicationContext,
                        AttractionRepository attractionRepository,
                        JourneyRepository journeyRepository) {
        this.applicationContext = applicationContext.getApplicationContext();
        this.attractionRepository = attractionRepository;
        this.journeyRepository = journeyRepository;
    }

    public AttractionRepository getAttractionRepository() {
        return attractionRepository;
    }

    public Context getApplicationContext() {
        return applicationContext;
    }

    public JourneyRepository getJourneyRepository() {
        return journeyRepository;
    }

    /**
     * Reference position used for distance display until an exploration session provides a real
     * fix. Reading it starts no location updates and needs no permission.
     */
    public GeoPoint getReferenceOrigin() {
        return SampleAttractions.DEMO_ORIGIN;
    }

    /** Search radius, in metres, applied to nearby-attraction queries. */
    public double getNearbySearchRadiusMeters() {
        return 3000.0;
    }
}
