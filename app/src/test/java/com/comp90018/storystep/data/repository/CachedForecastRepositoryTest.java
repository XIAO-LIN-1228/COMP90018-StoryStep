package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.comp90018.storystep.data.remote.ForecastSource;
import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cache, offline and failure rules of the forecast repository, with direct executors so every
 * callback has run by the time a test asserts.
 */
public class CachedForecastRepositoryTest {

    private static final long START = 1_791_587_559_000L;
    private static final long MINUTE = 60_000L;
    private static final GeoPoint ORIGIN = new GeoPoint(-37.8136, 144.9631);

    /** Answers with a fresh value per call, or throws the queued failure. */
    private static final class CountingSource<T> implements ForecastSource<T> {
        final List<T> answers = new ArrayList<>();
        final List<RuntimeException> failures = new ArrayList<>();
        final List<GeoPoint> asked = new ArrayList<>();
        int calls;

        @Override
        public T fetch(GeoPoint cellCentre, long fetchedAtMillis) {
            asked.add(cellCentre);
            calls++;
            if (!failures.isEmpty()) {
                throw failures.remove(0);
            }
            return answers.remove(0);
        }
    }

    private static final class Capture<T> implements RepositoryCallback<T> {
        T result;
        Throwable error;

        @Override
        public void onSuccess(T value) {
            result = value;
        }

        @Override
        public void onError(Throwable failure) {
            error = failure;
        }
    }

    private CountingSource<HourlyForecast> hourly;
    private CountingSource<EnsemblePrecipitation> ensemble;
    private FakeClock clock;
    private boolean online;
    private CachedForecastRepository repository;

    @Before
    public void setUp() {
        hourly = new CountingSource<>();
        ensemble = new CountingSource<>();
        clock = FakeClock.startingAt(START);
        online = true;
        repository = new CachedForecastRepository(hourly, ensemble, () -> online, clock,
                Runnable::run, Runnable::run);
    }

    private static HourlyForecast hourlyForecast() {
        return new HourlyForecast(Collections.singletonList(
                new HourlyForecast.Hour(1_791_586_800L, 0, null)), START);
    }

    private static EnsemblePrecipitation ensembleForecast() {
        return new EnsemblePrecipitation(new long[] {1_791_586_800L},
                Collections.singletonList(new EnsemblePrecipitation.Model("ICON", 1,
                        new double[][] {{0.0}})),
                ForecastPlace.unknown(), START);
    }

    private Capture<HourlyForecast> loadHourly() {
        Capture<HourlyForecast> capture = new Capture<>();
        repository.hourly(ORIGIN, capture);
        return capture;
    }

    @Test
    public void theProviderIsAskedAboutTheAreaCentreNotTheOrigin() {
        hourly.answers.add(hourlyForecast());

        loadHourly();

        assertEquals(WeatherAreaBucket.of(ORIGIN).center(), hourly.asked.get(0));
    }

    @Test
    public void aForecastIsReusedWithinThirtyMinutes() {
        HourlyForecast first = hourlyForecast();
        hourly.answers.add(first);

        loadHourly();
        clock.shiftWallClock(30 * MINUTE);
        Capture<HourlyForecast> again = loadHourly();

        assertSame(first, again.result);
        assertEquals(1, hourly.calls);
    }

    @Test
    public void anOlderForecastIsFetchedAgain() {
        hourly.answers.add(hourlyForecast());
        HourlyForecast second = hourlyForecast();
        hourly.answers.add(second);

        loadHourly();
        clock.shiftWallClock(30 * MINUTE + 1);
        Capture<HourlyForecast> again = loadHourly();

        assertSame(second, again.result);
        assertEquals(2, hourly.calls);
    }

    @Test
    public void aClockMovedBackDoesNotTrustTheCache() {
        hourly.answers.add(hourlyForecast());
        hourly.answers.add(hourlyForecast());

        loadHourly();
        clock.shiftWallClock(-MINUTE);
        loadHourly();

        assertEquals(2, hourly.calls);
    }

    @Test
    public void nearbyOriginsInOneAreaShareTheCache() {
        hourly.answers.add(hourlyForecast());

        loadHourly();
        repository.hourly(new GeoPoint(-37.8137, 144.9632), new Capture<>());

        assertEquals(1, hourly.calls);
    }

    @Test
    public void theTwoForecastsAreCachedSeparately() {
        hourly.answers.add(hourlyForecast());
        EnsemblePrecipitation members = ensembleForecast();
        ensemble.answers.add(members);

        loadHourly();
        Capture<EnsemblePrecipitation> capture = new Capture<>();
        repository.ensemble(ORIGIN, capture);

        assertSame(members, capture.result);
        assertEquals(1, ensemble.calls);
    }

    @Test
    public void offlineFailsAtOnceWithoutAsking() {
        online = false;

        Capture<HourlyForecast> capture = loadHourly();

        assertEquals(0, hourly.calls);
        assertEquals(ForecastUnavailableException.Reason.NO_NETWORK,
                ((ForecastUnavailableException) capture.error).getReason());
    }

    @Test
    public void offlineStillAnswersFromAFreshCache() {
        HourlyForecast cached = hourlyForecast();
        hourly.answers.add(cached);
        loadHourly();
        online = false;

        assertSame(cached, loadHourly().result);
    }

    @Test
    public void aTypedFailureIsPassedOnAndNotCached() {
        hourly.failures.add(new ForecastUnavailableException(
                ForecastUnavailableException.Reason.TIMEOUT));
        HourlyForecast later = hourlyForecast();
        hourly.answers.add(later);

        Capture<HourlyForecast> failed = loadHourly();
        Capture<HourlyForecast> retried = loadHourly();

        assertEquals(ForecastUnavailableException.Reason.TIMEOUT,
                ((ForecastUnavailableException) failed.error).getReason());
        assertNull(failed.result);
        assertSame(later, retried.result);
    }

    @Test
    public void anUnexpectedErrorBecomesATypedFailure() {
        hourly.failures.add(new IllegalStateException("bug"));

        Capture<HourlyForecast> capture = loadHourly();

        assertEquals(ForecastUnavailableException.Reason.MALFORMED,
                ((ForecastUnavailableException) capture.error).getReason());
    }
}
