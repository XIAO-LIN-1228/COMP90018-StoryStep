package com.comp90018.storystep.ui.weather;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.local.FakeWeatherCacheDao;
import com.comp90018.storystep.data.local.WeatherCacheEntity;
import com.comp90018.storystep.data.remote.FakeWeatherSource;
import com.comp90018.storystep.data.repository.CachedWeatherRepository;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * What the weather card says, and when it changes.
 *
 * <p>Runs the real repository over fakes rather than a stub repository, so the freshness and
 * fallback rules the screen depends on are the ones actually shipped.
 */
public class WeatherViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint REFERENCE = new GeoPoint(-37.79630, 144.96140);
    private static final GeoPoint FAR_AWAY = new GeoPoint(-33.86880, 151.20930);
    private static final long START = 1_700_000_000_000L;

    private FakeWeatherCacheDao dao;
    private FakeWeatherSource source;
    private FakeClock clock;
    private FakeLocationSource locationSource;
    private ExplorationCoordinator coordinator;
    private boolean online;

    @Before
    public void setUp() {
        dao = new FakeWeatherCacheDao();
        source = new FakeWeatherSource();
        clock = FakeClock.startingAt(START);
        locationSource = new FakeLocationSource();
        coordinator = TestCoordinators.with(clock, locationSource);
        online = true;
    }

    private WeatherViewModel viewModel() {
        CachedWeatherRepository repository = new CachedWeatherRepository(dao, source,
                () -> online, WeatherFreshnessPolicy.defaultPolicy(), clock,
                Runnable::run, Runnable::run);
        return new WeatherViewModel(repository, coordinator, REFERENCE, clock);
    }

    /**
     * A sample stamped from the fake clock, so the quality filter sees it as current. A fixed
     * monotonic value would be rejected as stale and never reach the coordinator's route.
     */
    private LocationSample sampleAt(GeoPoint position) {
        return new LocationSample(position, 8.0f,
                clock.wallClockMillis(), clock.monotonicMillis(), LocationOrigin.DEVICE);
    }

    @Test
    public void aScreenOpenedMidSessionStartsFromThePositionTheCoordinatorAlreadyHas() {
        // The exploration dashboard is created after the session is already running. Waiting for
        // the next fix to relabel would mean showing "reference area" over a live walk.
        // Driven through the coordinator, not by calling onSample directly: what is being tested
        // is that the new ViewModel reads the position the coordinator has already accumulated.
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        coordinator.requestStart();
        locationSource.emit(sampleAt(FAR_AWAY));

        WeatherViewModel openedLater = viewModel();
        openedLater.load();

        WeatherUiState state = openedLater.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.AreaLabel.LIVE, state.getAreaLabel());
    }

    @Test
    public void loadingProducesAnObservationForTheReferenceAreaBeforeAnyFix() {
        source.condition = WeatherCondition.CLOUDY;
        WeatherViewModel viewModel = viewModel();

        viewModel.load();

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.CONTENT, state.getStatus());
        assertEquals(WeatherCondition.CLOUDY, state.getCondition());
        // Before a fix the observation describes the demonstration area, not the user, and the
        // screen has to be able to say so.
        assertEquals(WeatherUiState.AreaLabel.REFERENCE_AREA, state.getAreaLabel());
    }

    @Test
    public void aFixInTheSameCellAsTheReferenceAreaStillStopsSayingReferenceArea() {
        // The usual case, and the one that is easy to get wrong: the first fix normally lands in
        // the cell the reference area already covers, so no request is needed. The observation is
        // unchanged, but it stopped being the demonstration area's weather and became the user's.
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        int afterFirstLoad = source.requestCount();

        viewModel.onSample(sampleAt(REFERENCE));

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.AreaLabel.LIVE, state.getAreaLabel());
        assertEquals(WeatherUiState.Status.CONTENT, state.getStatus());
        // And it must not have refetched to say so.
        assertEquals(afterFirstLoad, source.requestCount());
    }

    @Test
    public void anUnavailableCardIsRelabelledByAFixToo() {
        online = false;
        WeatherViewModel viewModel = viewModel();
        viewModel.load();

        viewModel.onSample(sampleAt(REFERENCE));

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.UNAVAILABLE, state.getStatus());
        assertEquals(WeatherUiState.AreaLabel.LIVE, state.getAreaLabel());
        assertEquals(WeatherUnavailableException.Reason.NO_CACHE, state.getReason());
    }

    @Test
    public void aFixRelabelsTheObservationAsTheUsersOwnArea() {
        WeatherViewModel viewModel = viewModel();
        viewModel.load();

        viewModel.onSample(sampleAt(FAR_AWAY));

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.AreaLabel.LIVE, state.getAreaLabel());
    }

    @Test
    public void movingWithinOneAreaDoesNotIssueAnotherRequest() {
        // The reason the bucket exists: a walking user produces a fix every few seconds, and none
        // of them is a different question to ask the provider.
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        int afterFirstLoad = source.requestCount();

        viewModel.onSample(sampleAt(REFERENCE));
        viewModel.onSample(sampleAt(new GeoPoint(-37.79500, 144.96300)));
        viewModel.onSample(sampleAt(new GeoPoint(-37.79400, 144.96500)));

        assertEquals(afterFirstLoad, source.requestCount());
    }

    @Test
    public void crossingIntoAnotherAreaFetchesAgain() {
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        int afterFirstLoad = source.requestCount();

        viewModel.onSample(sampleAt(FAR_AWAY));

        assertEquals(afterFirstLoad + 1, source.requestCount());
    }

    @Test
    public void aStaleObservationIsLabelledRatherThanShownAsCurrent() {
        seedCacheFor(REFERENCE, WeatherCondition.RAIN, START);
        clock.advance(45L * 60L * 1000L);
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.TIMEOUT);
        WeatherViewModel viewModel = viewModel();

        viewModel.load();

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.CONTENT, state.getStatus());
        assertEquals(WeatherFreshness.STALE, state.getFreshness());
        assertTrue(state.isStale());
    }

    @Test
    public void anOfflineColdStartIsUnavailableAndRetryable() {
        online = false;
        WeatherViewModel viewModel = viewModel();

        viewModel.load();

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.UNAVAILABLE, state.getStatus());
        assertEquals(WeatherUnavailableException.Reason.NO_CACHE, state.getReason());
        assertTrue(state.isRetryable());
    }

    @Test
    public void aMalformedResponseIsNotOfferedForRetry() {
        // Asking again immediately would be a loop: the provider is answering, and answering
        // wrongly. The user pressing retry cannot change that.
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.MALFORMED);
        WeatherViewModel viewModel = viewModel();

        viewModel.load();

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.UNAVAILABLE, state.getStatus());
        assertFalse(state.isRetryable());
    }

    @Test
    public void aFailureForANewAreaCannotUseThePreviousAreasWeather() {
        // Another city cannot inherit the previous city's weather for display or scoring.
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        source.failure =
                new WeatherUnavailableException(WeatherUnavailableException.Reason.TIMEOUT);

        viewModel.onSample(sampleAt(FAR_AWAY));

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.UNAVAILABLE, state.getStatus());
        org.junit.Assert.assertNull(viewModel.getContextWeather().getValue());
    }

    @Test
    public void retryAfterAFailureCanSucceed() {
        online = false;
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        assertEquals(WeatherUiState.Status.UNAVAILABLE,
                viewModel.getUiState().getValue().getStatus());

        online = true;
        source.condition = WeatherCondition.CLEAR;
        viewModel.load();

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.CONTENT, state.getStatus());
        assertEquals(WeatherCondition.CLEAR, state.getCondition());
    }

    @Test
    public void aRetryForAnUnchangedAreaStillAsksTheRepository() {
        // The area has not changed, so the sample path would skip it. An explicit retry must not
        // be swallowed by that optimisation.
        online = false;
        WeatherViewModel viewModel = viewModel();
        viewModel.load();
        int afterFailure = source.requestCount();

        online = true;
        viewModel.load();

        assertEquals(afterFailure + 1, source.requestCount());
    }

    @Test
    public void losingTheFixLeavesTheLastObservationOnScreen() {
        WeatherViewModel viewModel = viewModel();
        viewModel.load();

        viewModel.onLocationUnavailable(LocationSource.Reason.NO_RECENT_FIX);

        WeatherUiState state = viewModel.getUiState().getValue();
        assertNotNull(state);
        assertEquals(WeatherUiState.Status.CONTENT, state.getStatus());
    }

    @Test public void visibleClockExpiryNeutralizesWeatherWithoutARequest() {
        WeatherViewModel model = viewModel();
        model.load();
        int requests = source.requestCount();
        clock.advance(31 * 60_000L);
        model.refreshAge();
        assertEquals(WeatherFreshness.STALE, model.getContextWeather().getValue().getFreshness());
        assertTrue(model.getUiState().getValue().isStale());
        assertEquals(requests, source.requestCount());
        clock.advance(6 * 60 * 60_000L);
        model.refreshAge();
        org.junit.Assert.assertNull(model.getContextWeather().getValue());
        assertEquals(WeatherUiState.Status.UNAVAILABLE, model.getUiState().getValue().getStatus());
        assertEquals(requests, source.requestCount());
    }

    private void seedCacheFor(GeoPoint position, WeatherCondition condition, long fetchedAt) {
        WeatherCacheEntity row = new WeatherCacheEntity();
        row.areaKey = WeatherAreaBucket.of(position).key();
        row.condition = condition.name();
        row.temperatureCelsius = 11.0;
        row.observedAtMillis = fetchedAt;
        row.fetchedAtMillis = fetchedAt;
        dao.put(row);
    }
}
