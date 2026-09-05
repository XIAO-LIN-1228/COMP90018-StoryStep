package com.comp90018.storystep.application;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.WeatherRepository;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.weather.WeatherResult;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Covers the half of REQ-19.3 that reaches storage: the weather a session was walked in is
 * recorded onto the session, and a weather failure cannot disturb the walk.
 *
 * <p>The last property is the important one. Everything the capture touches is decoration on a
 * historical record; none of it may reach the session's own state, its durability reporting or its
 * observers.
 */
public class ExplorationCoordinatorWeatherTest {

    private static final GeoPoint SOMEWHERE = new GeoPoint(-37.79630, 144.96140);

    private FakeClock clock;
    private FakeLocationSource locationSource;
    private InMemorySessionStore store;
    private RecordingWeather weather;
    private ExplorationCoordinator coordinator;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        locationSource = new FakeLocationSource();
        store = new InMemorySessionStore(clock);
        weather = new RecordingWeather();
        coordinator = TestCoordinators.with(
                clock, locationSource, SensingSources.none(), store, weather);
    }

    @Test
    public void noWeatherIsRequestedBeforeASessionHasARealFix() {
        usable();

        assertTrue("browsing is not a session, and there is nothing to record it against",
                weather.requestedPositions.isEmpty());
    }

    @Test
    public void theFirstAcceptedFixRecordsTheWeatherWhereTheWalkIsHappening() {
        activeSession();

        emit(SOMEWHERE, 5_000L);

        assertEquals("one fix, one request", 1, weather.requestedPositions.size());
        assertEquals("the request must use the walk's own position, never a reference origin",
                SOMEWHERE.getLatitude(),
                weather.requestedPositions.get(0).getLatitude(), 0.000001);
        assertNotNull("the observation must reach the session record",
                store.recordedWeather());
        assertEquals(WeatherCondition.CLOUDY, store.recordedWeather().getCondition());
    }

    @Test
    public void aWalkThatKeepsMovingStillRecordsTheWeatherOnlyOnce() {
        activeSession();

        emit(SOMEWHERE, 5_000L);
        emit(new GeoPoint(-37.79530, 144.96140), 12_000L);
        emit(new GeoPoint(-37.79430, 144.96140), 19_000L);

        assertEquals("the weather of a walk is one observation, not one per fix",
                1, weather.requestedPositions.size());
    }

    @Test
    public void aFailedCaptureLeavesTheSessionRunningAndUndisturbed() {
        weather.failWith(WeatherUnavailableException.Reason.NO_NETWORK);
        DurabilityRecorder durability = new DurabilityRecorder();
        activeSession();
        coordinator.addObserver(durability);

        emit(SOMEWHERE, 5_000L);

        assertEquals("collection continues through a weather failure",
                SessionState.ACTIVE, coordinator.summary().getState());
        assertFalse("a weather failure is not a durability problem and must not be reported as one",
                durability.reportedFailure);
        assertNull("nothing may be recorded when the service failed", store.recordedWeather());
    }

    @Test
    public void aFailedCaptureIsRetriedWhenTheSessionStops() {
        weather.failWith(WeatherUnavailableException.Reason.NO_NETWORK);
        activeSession();
        emit(SOMEWHERE, 5_000L);
        assertEquals(1, weather.requestedPositions.size());

        weather.succeed();
        coordinator.requestStop();

        assertEquals("stopping makes one last attempt for a walk that never managed one",
                2, weather.requestedPositions.size());
        assertNotNull(store.recordedWeather());
        assertEquals("the session must still finish", SessionState.COMPLETED,
                coordinator.summary().getState());
    }

    @Test
    public void stoppingDoesNotRequestWeatherAgainWhenTheWalkAlreadyRecordedSome() {
        activeSession();
        emit(SOMEWHERE, 5_000L);

        coordinator.requestStop();

        assertEquals("an observation already recorded is not refreshed at the finish line",
                1, weather.requestedPositions.size());
    }

    @Test
    public void aWeatherFailureAtStopStillFinishesTheSession() {
        activeSession();
        weather.failWith(WeatherUnavailableException.Reason.TIMEOUT);
        emit(SOMEWHERE, 5_000L);

        coordinator.requestStop();

        assertEquals("weather must never be able to hold up a stop",
                SessionState.COMPLETED, coordinator.summary().getState());
    }

    @Test
    public void aBuildWithNoWeatherServiceRunsASessionIdentically() {
        coordinator = TestCoordinators.with(
                clock, locationSource, SensingSources.none(), store, null);

        activeSession();
        emit(SOMEWHERE, 5_000L);
        coordinator.requestStop();

        assertEquals("a null weather service is a supported configuration",
                SessionState.COMPLETED, coordinator.summary().getState());
        assertNull(store.recordedWeather());
    }

    @Test
    public void aDiscardedSessionTakesItsRecordedWeatherWithIt() {
        activeSession();
        emit(SOMEWHERE, 5_000L);
        assertNotNull(store.recordedWeather());

        coordinator.requestDiscard();

        assertNull("a walk the user threw away leaves nothing behind, weather included",
                store.recordedWeather());
    }

    @Test
    public void aNewSessionCapturesItsOwnWeatherRatherThanReusingTheLastWalksObservation() {
        activeSession();
        emit(SOMEWHERE, 5_000L);
        coordinator.requestStop();
        assertEquals(1, weather.requestedPositions.size());

        coordinator.requestStart();
        emit(new GeoPoint(-37.81000, 144.97000), 40_000L);

        assertEquals("the weather belongs to the walk, not to the app",
                2, weather.requestedPositions.size());
    }

    @Test
    public void theMovementTotalsAreRecordedEvenWithNoWeatherService() {
        coordinator = TestCoordinators.with(
                clock, locationSource, SensingSources.none(), store, null);
        activeSession();

        emit(SOMEWHERE, 5_000L);
        coordinator.requestStop();

        assertNotNull("movement is always written, weather or not", store.recordedMovement());
    }

    // --- helpers -------------------------------------------------------------------------------

    private void activeSession() {
        usable();
        coordinator.requestStart();
    }

    private void usable() {
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    private void emit(GeoPoint position, long monotonicMillis) {
        locationSource.emit(new LocationSample(position, 5.0f,
                1_700_000_000_000L + monotonicMillis, monotonicMillis, LocationOrigin.DEVICE));
    }

    /** Records what the coordinator asked for, and can be made to fail. */
    private static final class RecordingWeather implements WeatherRepository {

        final List<GeoPoint> requestedPositions = new ArrayList<>();
        private WeatherUnavailableException.Reason failureReason;

        void failWith(WeatherUnavailableException.Reason reason) {
            failureReason = reason;
        }

        void succeed() {
            failureReason = null;
        }

        @Override
        public void loadWeather(GeoPoint position, RepositoryCallback<WeatherResult> callback) {
            requestedPositions.add(position);
            if (failureReason != null) {
                callback.onError(new WeatherUnavailableException(failureReason));
                return;
            }
            callback.onSuccess(new WeatherResult(
                    new WeatherSnapshot("-37.80,144.95", WeatherCondition.CLOUDY, 14.5,
                            1_700_000_000_000L, 1_700_000_001_000L,
                            WeatherSnapshot.Provenance.NETWORK),
                    WeatherFreshness.FRESH));
        }

        @Override
        public void clearCache(RepositoryCallback<Integer> callback) {
            callback.onSuccess(0);
        }
    }

    /** Watches the one signal a weather failure must never be able to trip. */
    private static final class DurabilityRecorder implements ExplorationCoordinator.Observer {

        boolean reportedFailure;

        @Override
        public void onSessionChanged(
                com.comp90018.storystep.domain.model.SessionSummary summary) {
            // Not what this recorder is watching.
        }

        @Override
        public void onSample(LocationSample sample) {
            // Not what this recorder is watching.
        }

        @Override
        public void onLocationUnavailable(
                com.comp90018.storystep.platform.location.LocationSource.Reason reason) {
            // Not what this recorder is watching.
        }

        @Override
        public void onDurabilityChanged(boolean failed) {
            if (failed) {
                reportedFailure = true;
            }
        }
    }
}
