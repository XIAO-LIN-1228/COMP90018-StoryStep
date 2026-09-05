package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.FakeWeatherCacheDao;
import com.comp90018.storystep.data.local.WeatherCacheEntity;
import com.comp90018.storystep.data.remote.FakeWeatherSource;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.domain.weather.WeatherResult;
import com.comp90018.storystep.platform.connectivity.ConnectivityMonitor;

import org.junit.Before;
import org.junit.Test;

/**
 * The cache-first, coalescing, stale-tolerant policy.
 *
 * <p>These are the rules that decide whether the user sees a temperature or a blank card, so most
 * cases here are about the failure paths: a refresh that fails must never take a readable
 * observation off the screen, and a cold offline launch must say something actionable rather than
 * spin.
 *
 * <p>Executors are direct, so every callback has already run by the time a test asserts.
 */
public class CachedWeatherRepositoryTest {

    private static final GeoPoint MELBOURNE = new GeoPoint(-37.80320, 144.97170);
    private static final GeoPoint SYDNEY = new GeoPoint(-33.86880, 151.20930);
    private static final long START = 1_700_000_000_000L;
    private static final long MINUTE = 60L * 1000L;

    private FakeWeatherCacheDao dao;
    private FakeWeatherSource source;
    private FakeClock clock;
    private boolean online;

    @Before
    public void setUp() {
        dao = new FakeWeatherCacheDao();
        source = new FakeWeatherSource();
        clock = FakeClock.startingAt(START);
        online = true;
    }

    private CachedWeatherRepository repository() {
        ConnectivityMonitor monitor = () -> online;
        return new CachedWeatherRepository(dao, source, monitor,
                WeatherFreshnessPolicy.defaultPolicy(), clock, Runnable::run, Runnable::run);
    }

    /** Seeds the cache as though an observation had been fetched at {@code fetchedAtMillis}. */
    private void seedCache(GeoPoint position, WeatherCondition condition, long fetchedAtMillis) {
        WeatherCacheEntity row = new WeatherCacheEntity();
        row.areaKey = WeatherAreaBucket.of(position).key();
        row.condition = condition.name();
        row.temperatureCelsius = 9.5;
        row.observedAtMillis = fetchedAtMillis;
        row.fetchedAtMillis = fetchedAtMillis;
        dao.put(row);
    }

    private Capture load(GeoPoint position) {
        Capture capture = new Capture();
        repository().loadWeather(position, capture);
        return capture;
    }

    @Test
    public void aFreshCachedObservationAnswersWithoutARequest() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        clock.advance(5L * MINUTE);

        Capture capture = load(MELBOURNE);

        assertNotNull(capture.result);
        assertEquals(WeatherCondition.RAIN, capture.result.getSnapshot().getCondition());
        assertEquals(WeatherFreshness.FRESH, capture.result.getFreshness());
        assertFalse(capture.result.isLive());
        assertEquals(0, source.requestCount());
    }

    @Test
    public void anEmptyCacheFetchesAndStores() {
        source.condition = WeatherCondition.CLOUDY;

        Capture capture = load(MELBOURNE);

        assertNotNull(capture.result);
        assertEquals(WeatherCondition.CLOUDY, capture.result.getSnapshot().getCondition());
        assertEquals(WeatherFreshness.FRESH, capture.result.getFreshness());
        assertTrue(capture.result.isLive());
        assertEquals(1, source.requestCount());
        assertEquals(1, dao.count());
    }

    @Test
    public void theRequestCarriesTheCellCentreNotTheUsersPosition() {
        load(MELBOURNE);

        assertEquals(1, source.requestCount());
        GeoPoint sent = source.requested.get(0);
        assertEquals(WeatherAreaBucket.of(MELBOURNE).center(), sent);
        assertFalse(MELBOURNE.equals(sent));
    }

    @Test
    public void anExpiredCachedObservationTriggersARefresh() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        clock.advance(45L * MINUTE);
        source.condition = WeatherCondition.CLEAR;

        Capture capture = load(MELBOURNE);

        assertEquals(1, source.requestCount());
        assertNotNull(capture.result);
        assertEquals(WeatherCondition.CLEAR, capture.result.getSnapshot().getCondition());
        assertTrue(capture.result.isLive());
    }

    @Test
    public void aFailedRefreshServesTheCachedObservationLabelledStale() {
        // The rule this class exists for: a network problem must not blank a readable temperature.
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        clock.advance(45L * MINUTE);
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.TIMEOUT);

        Capture capture = load(MELBOURNE);

        assertNull(capture.error);
        assertNotNull(capture.result);
        assertEquals(WeatherCondition.RAIN, capture.result.getSnapshot().getCondition());
        assertEquals(WeatherFreshness.STALE, capture.result.getFreshness());
        assertFalse(capture.result.isLive());
    }

    @Test
    public void anOfflineWarmStartServesTheCacheWithoutARequest() {
        seedCache(MELBOURNE, WeatherCondition.CLOUDY, START);
        clock.advance(45L * MINUTE);
        online = false;

        Capture capture = load(MELBOURNE);

        assertEquals(0, source.requestCount());
        assertNotNull(capture.result);
        assertEquals(WeatherFreshness.STALE, capture.result.getFreshness());
    }

    @Test
    public void anOfflineColdStartReportsNoCacheRatherThanSpinning() {
        online = false;

        Capture capture = load(MELBOURNE);

        assertNull(capture.result);
        assertNotNull(capture.error);
        assertEquals(WeatherUnavailableException.Reason.NO_CACHE, reasonOf(capture.error));
    }

    @Test
    public void aTooOldCachedObservationIsNotPresentedEvenOffline() {
        // Past the expiry: showing a six-hour-old temperature as the current weather would
        // mislead more than showing nothing.
        seedCache(MELBOURNE, WeatherCondition.CLEAR, START);
        clock.advance(7L * 60L * MINUTE);
        online = false;

        Capture capture = load(MELBOURNE);

        assertNull(capture.result);
        assertNotNull(capture.error);
    }

    @Test
    public void aFetchFailureWithNoCacheKeepsItsOwnReason() {
        // Online but the service refused: the screen must be able to tell that apart from offline.
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.RATE_LIMITED);

        Capture capture = load(MELBOURNE);

        assertNotNull(capture.error);
        assertEquals(WeatherUnavailableException.Reason.RATE_LIMITED, reasonOf(capture.error));
    }

    @Test
    public void aMalformedResponseLeavesTheGoodCachedRowInPlace() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        clock.advance(45L * MINUTE);
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.MALFORMED);

        load(MELBOURNE);

        WeatherCacheEntity stored =
                dao.findByArea(WeatherAreaBucket.of(MELBOURNE).key());
        assertNotNull(stored);
        assertEquals(WeatherCondition.RAIN.name(), stored.condition);
        assertEquals(0, dao.upsertCount);
    }

    @Test
    public void aCacheWriteFailureStillServesTheFetchedObservation() {
        source.condition = WeatherCondition.SNOW;
        dao.failWrites = new IllegalStateException("disk full");

        Capture capture = load(MELBOURNE);

        assertNull(capture.error);
        assertNotNull(capture.result);
        assertEquals(WeatherCondition.SNOW, capture.result.getSnapshot().getCondition());
    }

    @Test
    public void anUnreadableCacheFallsBackToTheNetwork() {
        dao.failReads = new IllegalStateException("corrupt");
        source.condition = WeatherCondition.CLEAR;

        Capture capture = load(MELBOURNE);

        assertNull(capture.error);
        assertNotNull(capture.result);
        assertEquals(1, source.requestCount());
    }

    @Test
    public void separateAreasDoNotShareACachedObservation() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);

        Capture capture = load(SYDNEY);

        assertEquals(1, source.requestCount());
        assertTrue(capture.result.isLive());
    }

    @Test
    public void twoCallersForOneAreaProduceOneRequestAndTwoAnswers() {
        // Coalescing, forced deterministically: the I/O executor holds the first call's work, the
        // second caller arrives while it is still in flight, then the work is released.
        DeferredExecutor io = new DeferredExecutor();
        CachedWeatherRepository repository = new CachedWeatherRepository(dao, source,
                () -> online, WeatherFreshnessPolicy.defaultPolicy(), clock, io, Runnable::run);

        Capture first = new Capture();
        Capture second = new Capture();
        repository.loadWeather(MELBOURNE, first);
        repository.loadWeather(MELBOURNE, second);

        // Only the first call scheduled work; the second joined it.
        assertEquals(1, io.pendingCount());

        io.runAll();

        assertEquals(1, source.requestCount());
        assertNotNull(first.result);
        assertNotNull(second.result);
        assertEquals(first.result, second.result);
    }

    @Test
    public void aRequestForADifferentAreaIsNotCoalescedIntoTheFirst() {
        DeferredExecutor io = new DeferredExecutor();
        CachedWeatherRepository repository = new CachedWeatherRepository(dao, source,
                () -> online, WeatherFreshnessPolicy.defaultPolicy(), clock, io, Runnable::run);

        repository.loadWeather(MELBOURNE, new Capture());
        repository.loadWeather(SYDNEY, new Capture());

        assertEquals(2, io.pendingCount());
    }

    @Test
    public void anAreaCanBeAskedAgainAfterItsRequestCompletes() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        clock.advance(45L * MINUTE);
        CachedWeatherRepository repository = repository();

        Capture first = new Capture();
        repository.loadWeather(MELBOURNE, first);
        clock.advance(45L * MINUTE);
        Capture second = new Capture();
        repository.loadWeather(MELBOURNE, second);

        // The in-flight entry was drained by the first call, so the second is not stranded.
        assertNotNull(first.result);
        assertNotNull(second.result);
        assertEquals(2, source.requestCount());
    }

    @Test
    public void clearingRemovesEveryCachedObservation() {
        seedCache(MELBOURNE, WeatherCondition.RAIN, START);
        seedCache(SYDNEY, WeatherCondition.CLEAR, START);

        int[] removed = {-1};
        repository().clearCache(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer result) {
                removed[0] = result;
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertEquals(2, removed[0]);
        assertEquals(0, dao.count());
    }

    private static WeatherUnavailableException.Reason reasonOf(Throwable error) {
        assertTrue("expected a typed weather failure, got " + error,
                error instanceof WeatherUnavailableException);
        return ((WeatherUnavailableException) error).getReason();
    }

    /** Records whichever of the two callbacks fired. */
    private static final class Capture implements RepositoryCallback<WeatherResult> {

        WeatherResult result;
        Throwable error;

        @Override
        public void onSuccess(WeatherResult value) {
            result = value;
        }

        @Override
        public void onError(Throwable failure) {
            error = failure;
        }
    }

    /**
     * Holds submitted work until the test runs it, so the window in which a second caller can
     * join an in-flight request is reachable without threads.
     */
    private static final class DeferredExecutor implements java.util.concurrent.Executor {

        private final java.util.List<Runnable> pending = new java.util.ArrayList<>();

        @Override
        public void execute(Runnable command) {
            pending.add(command);
        }

        int pendingCount() {
            return pending.size();
        }

        void runAll() {
            java.util.List<Runnable> due = new java.util.ArrayList<>(pending);
            pending.clear();
            for (Runnable runnable : due) {
                runnable.run();
            }
        }
    }
}
