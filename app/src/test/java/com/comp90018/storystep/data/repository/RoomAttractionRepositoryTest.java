package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.AttractionEntity;
import com.comp90018.storystep.data.local.DirectTransactions;
import com.comp90018.storystep.data.local.FakeAttractionAreaDao;
import com.comp90018.storystep.data.local.FakeAttractionDao;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Reading and seeding the local attraction cache.
 *
 * <p>Radius and ordering rules are covered against the shared selector by
 * {@code InMemoryAttractionRepositoryTest}; what matters here is that
 * the same rules survive a trip through the database, and that seeding is idempotent — a duplicate
 * seed row would show the user the same place twice, for as long as the install lasted.
 */
public class RoomAttractionRepositoryTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    /** Two places about 250 m and 1.2 km from the origin, plus one well outside any test radius. */
    private static final List<Attraction> SEED = Arrays.asList(
            Attraction.seeded("near", "Near Place", AttractionCategory.PARK, "Close by.", IndoorClassification.OUTDOOR,
                    new GeoPoint(-37.79630, 144.96425)),
            Attraction.seeded("far", "Far Place", AttractionCategory.MUSEUM, "Further away.", IndoorClassification.INDOOR,
                    new GeoPoint(-37.79630, 144.97500)),
            Attraction.seeded("distant", "Distant Place", AttractionCategory.HISTORICAL_BUILDING,
                    "Another suburb.", IndoorClassification.INDOOR, new GeoPoint(-37.85000, 145.05000)));

    private FakeAttractionDao dao;
    private FakeAttractionAreaDao areaDao;
    private RoomAttractionRepository repository;

    @Before
    public void setUp() {
        dao = new FakeAttractionDao();
        areaDao = new FakeAttractionAreaDao();
        repository = new RoomAttractionRepository(
                new AttractionCache(dao, areaDao, new DirectTransactions(),
                        FakeClock.startingAt(1_000L), SEED),
                Runnable::run, Runnable::run);
    }

    @Test
    public void theFirstQuerySeedsTheEmptyCache() {
        loadNearby(3_000.0);

        assertEquals(3, dao.count());
        AttractionEntity stored = dao.findById("near");
        assertEquals(AttractionEntity.PROVENANCE_SEED, stored.provenance);
        assertEquals(1_000L, stored.fetchedAtMillis);
    }

    @Test
    public void seedingRunsOnceHoweverManyQueriesFollow() {
        loadNearby(3_000.0);
        loadNearby(3_000.0);
        loadAttraction("near");

        assertEquals(3, dao.count());
        assertEquals(1, dao.insertMissingCalls);
    }

    @Test
    public void aRelaunchOverAPopulatedCacheDoesNotSeedAgain() {
        loadNearby(3_000.0);
        // A second repository over the same rows is what the next process start looks like.
        RoomAttractionRepository relaunched = new RoomAttractionRepository(
                new AttractionCache(dao, areaDao, new DirectTransactions(),
                        FakeClock.startingAt(99_000L), SEED),
                Runnable::run, Runnable::run);

        loadNearby(relaunched, 3_000.0);

        assertEquals(3, dao.count());
        assertEquals("the bundled copy must not overwrite what is already stored",
                1_000L, dao.findById("near").fetchedAtMillis);
    }

    @Test
    public void nearbyResultsAreFilteredByRadiusAndOrderedNearestFirst() {
        List<Attraction> nearby = loadNearby(3_000.0);

        assertEquals(2, nearby.size());
        assertEquals("near", nearby.get(0).getId());
        assertEquals("far", nearby.get(1).getId());
    }

    @Test
    public void aPlaceOutsideTheRadiusIsExcluded() {
        List<Attraction> nearby = loadNearby(500.0);

        assertEquals(1, nearby.size());
        assertEquals("near", nearby.get(0).getId());
    }

    @Test
    public void anAttractionRoundTripsThroughStorageWithItsContent() {
        Attraction loaded = loadAttraction("far");

        assertNotNull(loaded);
        assertEquals("Far Place", loaded.getName());
        assertEquals(AttractionCategory.MUSEUM, loaded.getCategory());
        assertEquals(IndoorClassification.INDOOR, loaded.getIndoor());
        assertEquals(144.97500, loaded.getLocation().getLongitude(), 1e-9);
    }

    @Test
    public void anUnknownIdIsReportedAsNotFoundRatherThanAnEmptyResult() {
        Result<Attraction> result = new Result<>();
        repository.loadAttraction("no-such-place", result);

        assertTrue(result.error instanceof AttractionNotFoundException);
    }

    @Test
    public void anInvalidRadiusIsRejectedInsteadOfReturningEverything() {
        Result<AttractionResult> result = new Result<>();
        repository.loadNearbyAttractions(ORIGIN, 0.0, result);

        assertTrue(result.error instanceof IllegalArgumentException);
    }

    private List<Attraction> loadNearby(double radiusMeters) {
        return loadNearby(repository, radiusMeters);
    }

    private static List<Attraction> loadNearby(RoomAttractionRepository target,
                                               double radiusMeters) {
        Result<AttractionResult> result = new Result<>();
        target.loadNearbyAttractions(ORIGIN, radiusMeters, result);
        assertTrue(result.succeeded());
        return result.value.getAttractions();
    }

    private Attraction loadAttraction(String id) {
        Result<Attraction> result = new Result<>();
        repository.loadAttraction(id, result);
        assertTrue(result.succeeded());
        return result.value;
    }

    /** Captures which callback arm ran; both executors here are direct. */
    private static final class Result<T> implements RepositoryCallback<T> {

        private T value;
        private Throwable error;
        private boolean called;

        @Override
        public void onSuccess(T result) {
            value = result;
            called = true;
        }

        @Override
        public void onError(Throwable throwable) {
            error = throwable;
            called = true;
        }

        boolean succeeded() {
            return called && error == null;
        }
    }
}
