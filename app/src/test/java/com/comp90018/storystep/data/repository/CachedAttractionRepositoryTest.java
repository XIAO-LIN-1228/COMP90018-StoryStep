package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.AttractionEntity;
import com.comp90018.storystep.data.local.DirectTransactions;
import com.comp90018.storystep.data.local.FakeAttractionAreaDao;
import com.comp90018.storystep.data.local.FakeAttractionDao;
import com.comp90018.storystep.data.remote.FakeAttractionSource;
import com.comp90018.storystep.domain.attraction.AttractionAreaBucket;
import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.attraction.AttractionFreshnessPolicy;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.connectivity.ConnectivityMonitor;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The cache policy, exercised with no HTTP stack and no clock that has to be waited on.
 *
 * <p>Grouped by the five policies the class documents: cache first, coalescing, stale fallback,
 * atomic replace, and empty-is-an-answer.
 */
public class CachedAttractionRepositoryTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);
    private static final GeoPoint FAR_AWAY = new GeoPoint(-37.85000, 145.05000);
    private static final double RADIUS = 3000.0;
    private static final long NOW = 1_700_000_000_000L;
    private static final long DAY = 24L * 60L * 60L * 1000L;

    private static final List<Attraction> SEED = Collections.singletonList(
            Attraction.seeded("old-arts", "Old Arts Building",
                    AttractionCategory.UNIVERSITY_LANDMARK, "The 1924 clocktower building.",
                    IndoorClassification.OUTDOOR, new GeoPoint(-37.79810, 144.95910)));

    private FakeAttractionDao dao;
    private FakeAttractionAreaDao areaDao;
    private FakeAttractionSource source;
    private FakeClock clock;
    private boolean online;
    private AttractionCache cache;
    private CachedAttractionRepository repository;

    @Before
    public void setUp() {
        dao = new FakeAttractionDao();
        areaDao = new FakeAttractionAreaDao();
        source = new FakeAttractionSource();
        clock = FakeClock.startingAt(NOW);
        online = true;
        cache = new AttractionCache(dao, areaDao, new DirectTransactions(), clock, SEED);
        repository = build(Runnable::run);
    }

    private CachedAttractionRepository build(Executor ioExecutor) {
        ConnectivityMonitor monitor = () -> online;
        return new CachedAttractionRepository(cache, source, monitor,
                AttractionFreshnessPolicy.defaultPolicy(), clock, ioExecutor, Runnable::run);
    }

    // --- cache first ---------------------------------------------------------------------------

    @Test
    public void aColdStartFetchesAndStoresTheArea() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));

        AttractionResult result = loadNearby();

        assertEquals(1, source.requestCount());
        assertEquals(AttractionFreshness.FRESH, result.getFreshness());
        assertNotNull("the area must be marked fetched",
                areaDao.findByKey(AttractionAreaBucket.of(ORIGIN).key()));
        assertEquals(AttractionEntity.PROVENANCE_SEED.equals("seed") ? "osm" : "osm",
                dao.findById("osm:node/1").provenance);
    }

    @Test
    public void aFreshAreaAnswersWithoutARequest() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        loadNearby();

        clock.advance(12L * 60L * 60L * 1000L);
        AttractionResult result = loadNearby();

        assertEquals("a day-old list of museums needs no refresh", 1, source.requestCount());
        assertEquals(AttractionFreshness.FRESH, result.getFreshness());
    }

    @Test
    public void anAreaPastItsRefreshIntervalIsFetchedAgain() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        loadNearby();

        clock.advance(2L * DAY);
        loadNearby();

        assertEquals(2, source.requestCount());
    }

    @Test
    public void theRequestCarriesTheCellCentreRatherThanTheUsersPosition() {
        loadNearby();

        GeoPoint asked = source.requested.get(0);
        assertFalse(asked.getLatitude() == ORIGIN.getLatitude()
                && asked.getLongitude() == ORIGIN.getLongitude());
        assertEquals(AttractionAreaBucket.of(ORIGIN).center(), asked);
    }

    @Test
    public void theRequestRadiusIsWidenedToCoverTheWholeCell() {
        loadNearby();

        // Otherwise a user standing at a corner of the cell would be missing places that are
        // plainly near them, and the gap would look like the provider having no data.
        assertTrue(source.requestedRadii.get(0) > RADIUS);
    }

    @Test
    public void resultsAreFilteredFromTheRealOriginNotTheCellCentre() {
        source.results = Arrays.asList(
                fetched("close", -37.79640, 144.96150),
                fetched("distant", -37.85000, 145.05000));

        List<Attraction> nearby = loadNearby().getAttractions();

        // The request was centred on the cell, but the radius test is measured from where the user
        // actually is, so a place inside the fetched circle but outside their radius is dropped.
        List<String> ids = new ArrayList<>();
        for (Attraction attraction : nearby) {
            ids.add(attraction.getId());
        }
        assertTrue(ids.contains("osm:node/close"));
        assertFalse(ids.contains("osm:node/distant"));
    }

    @Test
    public void separateAreasAreCachedIndependently() {
        loadNearby(ORIGIN);
        loadNearby(FAR_AWAY);
        loadNearby(ORIGIN);

        assertEquals("the second area is not covered by the first", 2, source.requestCount());
    }

    // --- coalescing ----------------------------------------------------------------------------

    @Test
    public void twoCallersOnOneAreaProduceOneRequestAndTwoAnswers() {
        DeferredExecutor io = new DeferredExecutor();
        repository = build(io);
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));

        Capture first = new Capture();
        Capture second = new Capture();
        repository.loadNearbyAttractions(ORIGIN, RADIUS, first);
        repository.loadNearbyAttractions(ORIGIN, RADIUS, second);
        io.runAll();

        assertEquals("the nearby screen and the dashboard must share one request",
                1, source.requestCount());
        assertNotNull(first.result);
        assertNotNull(second.result);
    }

    @Test
    public void callersOnDifferentAreasAreNotCoalescedTogether() {
        DeferredExecutor io = new DeferredExecutor();
        repository = build(io);

        repository.loadNearbyAttractions(ORIGIN, RADIUS, new Capture());
        repository.loadNearbyAttractions(FAR_AWAY, RADIUS, new Capture());
        io.runAll();

        assertEquals(2, source.requestCount());
    }

    @Test
    public void oneSharedFetchCoversTheWidestRadiusAnyWaiterAskedFor() {
        DeferredExecutor io = new DeferredExecutor();
        repository = build(io);

        repository.loadNearbyAttractions(ORIGIN, 500.0, new Capture());
        repository.loadNearbyAttractions(ORIGIN, 3000.0, new Capture());
        io.runAll();

        // Taking the first caller's radius would silently under-serve the later joiner.
        assertTrue(source.requestedRadii.get(0) > 3000.0);
    }

    @Test
    public void aSecondAskAfterTheFirstCompletesIsNotStranded() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        Capture first = new Capture();
        repository.loadNearbyAttractions(ORIGIN, RADIUS, first);

        Capture second = new Capture();
        repository.loadNearbyAttractions(ORIGIN, RADIUS, second);

        assertNotNull(first.result);
        assertNotNull("the in-flight entry was drained, so this is a new request or a cache hit",
                second.result);
    }

    // --- stale fallback ------------------------------------------------------------------------

    @Test
    public void aFailedRefreshServesTheStoredContentLabelledStale() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        loadNearby();
        clock.advance(2L * DAY);
        source.failure = new AttractionUnavailableException(
                AttractionUnavailableException.Reason.TIMEOUT);

        AttractionResult result = loadNearby();

        assertEquals(AttractionFreshness.STALE, result.getFreshness());
        assertFalse("blanking a usable list to report a network problem is the bug this prevents",
                result.isEmpty());
    }

    @Test
    public void aWarmOfflineLaunchServesWhatIsStoredWithoutARequest() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        loadNearby();
        clock.advance(2L * DAY);
        online = false;

        AttractionResult result = loadNearby();

        assertEquals("a request certain to fail is not made", 1, source.requestCount());
        assertEquals(AttractionFreshness.STALE, result.getFreshness());
        assertFalse(result.isEmpty());
    }

    @Test
    public void aColdOfflineStartStillOffersTheBundledPlaces() {
        online = false;

        AttractionResult result = loadNearby();

        // The seed set is real content the user can browse; failing here would be a regression in
        // what the app could do before it had a provider at all.
        assertFalse(result.isEmpty());
        assertEquals(AttractionFreshness.STALE, result.getFreshness());
        assertEquals(AttractionProvenance.SEED, result.getAttractions().get(0).getProvenance());
    }

    @Test
    public void aColdOfflineStartWithNothingStoredAtAllReportsNoCache() {
        cache = new AttractionCache(dao, areaDao, new DirectTransactions(), clock,
                Collections.emptyList());
        repository = build(Runnable::run);
        online = false;

        Capture capture = loadInto(ORIGIN);

        assertNull(capture.result);
        assertEquals(AttractionUnavailableException.Reason.NO_CACHE,
                ((AttractionUnavailableException) capture.error).getReason());
    }

    @Test
    public void aRateLimitWithNothingStoredKeepsItsOwnReason() {
        cache = new AttractionCache(dao, areaDao, new DirectTransactions(), clock,
                Collections.emptyList());
        repository = build(Runnable::run);
        source.failure = new AttractionUnavailableException(
                AttractionUnavailableException.Reason.RATE_LIMITED);

        Capture capture = loadInto(ORIGIN);

        // "Wait a moment" and "you are offline" are different instructions to the user.
        assertEquals(AttractionUnavailableException.Reason.RATE_LIMITED,
                ((AttractionUnavailableException) capture.error).getReason());
    }

    @Test
    public void anUnreadableAreaTableFallsThroughToTheNetwork() {
        areaDao.failReads = true;
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));

        AttractionResult result = loadNearby();

        assertEquals("bookkeeping that cannot be read is not a reason to give up",
                1, source.requestCount());
        assertEquals(AttractionFreshness.FRESH, result.getFreshness());
    }

    // --- atomic replace ------------------------------------------------------------------------

    @Test
    public void aMalformedResponseDoesNotOverwriteStoredContent() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));
        loadNearby();
        clock.advance(2L * DAY);
        source.failure = new AttractionUnavailableException(
                AttractionUnavailableException.Reason.MALFORMED);

        AttractionResult result = loadNearby();

        assertNotNull("the good row survives a bad refresh", dao.findById("osm:node/1"));
        assertEquals(AttractionFreshness.STALE, result.getFreshness());
    }

    @Test
    public void aRefreshNeverRemovesTheBundledPlaces() {
        source.results = Collections.singletonList(fetched("1", -37.79640, 144.96150));

        loadNearby();

        // The seed set is the only content guaranteed offline, and the detail screen resolves a
        // saved place through this same table.
        assertNotNull(dao.findById("old-arts"));
    }

    @Test
    public void refreshingOneAreaDoesNotEmptyAnother() {
        source.results = Collections.singletonList(fetched("near", -37.79640, 144.96150));
        loadNearby(ORIGIN);
        source.results = Collections.singletonList(fetched("far", -37.85010, 145.05010));
        loadNearby(FAR_AWAY);

        assertNotNull("the first area's rows must survive the second area's refresh",
                dao.findById("osm:node/near"));
        assertNotNull(dao.findById("osm:node/far"));
    }

    @Test
    public void aSupersededRecordInTheRefreshedAreaIsReplaced() {
        source.results = Collections.singletonList(fetched("gone", -37.79640, 144.96150));
        loadNearby();
        clock.advance(2L * DAY);
        source.results = Collections.singletonList(fetched("still-here", -37.79645, 144.96155));

        loadNearby();

        assertNull("a place the provider no longer lists should not linger",
                dao.findById("osm:node/gone"));
        assertNotNull(dao.findById("osm:node/still-here"));
    }

    // --- empty is an answer --------------------------------------------------------------------

    @Test
    public void anAreaTheProviderFoundNothingInIsCachedAsEmpty() {
        source.results = Collections.emptyList();

        loadNearby();
        loadNearby();

        assertEquals("re-requesting a genuinely quiet area on every query is the bug this prevents",
                1, source.requestCount());
        assertNotNull(areaDao.findByKey(AttractionAreaBucket.of(ORIGIN).key()));
    }

    @Test
    public void aFailedFetchDoesNotMarkTheAreaAsFetched() {
        source.failure = new AttractionUnavailableException(
                AttractionUnavailableException.Reason.MALFORMED);

        loadNearby();

        assertNull("a broken response must not blank the area for a day",
                areaDao.findByKey(AttractionAreaBucket.of(ORIGIN).key()));
    }

    // --- single lookup -------------------------------------------------------------------------

    @Test
    public void aSingleAttractionIsReadFromStorageWithoutARequest() {
        Capture.Single<Attraction> capture = new Capture.Single<>();
        repository.loadAttraction("old-arts", capture);

        assertEquals("Old Arts Building", capture.result.getName());
        assertEquals(0, source.requestCount());
    }

    @Test
    public void anUnknownIdIsReportedAsNotFound() {
        Capture.Single<Attraction> capture = new Capture.Single<>();
        repository.loadAttraction("no-such-place", capture);

        assertTrue(capture.error instanceof AttractionNotFoundException);
    }

    // --- helpers -------------------------------------------------------------------------------

    private AttractionResult loadNearby() {
        return loadNearby(ORIGIN);
    }

    private AttractionResult loadNearby(GeoPoint origin) {
        Capture capture = loadInto(origin);
        assertNotNull("expected a result, got " + capture.error, capture.result);
        return capture.result;
    }

    private Capture loadInto(GeoPoint origin) {
        Capture capture = new Capture();
        repository.loadNearbyAttractions(origin, RADIUS, capture);
        return capture;
    }

    private static Attraction fetched(String id, double latitude, double longitude) {
        return new Attraction("osm:node/" + id, "Place " + id, AttractionCategory.MUSEUM, "",
                IndoorClassification.UNKNOWN, new GeoPoint(latitude, longitude),
                AttractionProvenance.OPENSTREETMAP, null);
    }

    private static class Capture implements RepositoryCallback<AttractionResult> {

        AttractionResult result;
        Throwable error;

        @Override
        public void onSuccess(AttractionResult result) {
            this.result = result;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        static final class Single<T> implements RepositoryCallback<T> {

            T result;
            Throwable error;

            @Override
            public void onSuccess(T result) {
                this.result = result;
            }

            @Override
            public void onError(Throwable error) {
                this.error = error;
            }
        }
    }

    /** Queues I/O work so a test can reach the window where a request is still in flight. */
    private static final class DeferredExecutor implements Executor {

        private final List<Runnable> pending = new ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        void runAll() {
            List<Runnable> due = new ArrayList<>(pending);
            pending.clear();
            for (Runnable runnable : due) {
                runnable.run();
            }
        }
    }
}
