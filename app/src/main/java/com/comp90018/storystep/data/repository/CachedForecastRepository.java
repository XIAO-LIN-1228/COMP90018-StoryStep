package com.comp90018.storystep.data.repository;

import static java.util.Objects.requireNonNull;

import com.comp90018.storystep.data.remote.ForecastSource;
import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.platform.connectivity.ConnectivityMonitor;

import java.util.concurrent.Executor;

/**
 * The production {@link ForecastRepository}: a 30-minute in-memory cache per weather area in front
 * of the two forecast sources.
 *
 * <ul>
 *   <li><b>Cache first.</b> A forecast fetched within the lifetime answers without a request, so
 *       reopening a page or changing the stay time does not ask again.</li>
 *   <li><b>Offline short-circuit.</b> With no network, fail at once with {@code NO_NETWORK}
 *       instead of spending the retry budget on a request that cannot succeed, as current weather
 *       does.</li>
 *   <li><b>No stale fallback.</b> An expired forecast is not shown: its first hours are already
 *       past, and the page says so plainly instead.</li>
 * </ul>
 *
 * <p>Requests run on their own executor, so retry waits cannot hold up the shared I/O threads the
 * rest of the app uses. Results are dispatched on the main executor. No coalescing: each page asks
 * once when opened.
 */
public final class CachedForecastRepository implements ForecastRepository {

    /** How long a fetched forecast is reused. */
    public static final long LIFETIME_MILLIS = 30L * 60L * 1000L;

    private final ForecastSource<HourlyForecast> hourlySource;
    private final ForecastSource<EnsemblePrecipitation> ensembleSource;
    private final ConnectivityMonitor connectivity;
    private final Clock clock;
    private final Executor forecastExecutor;
    private final Executor mainExecutor;

    private final TimedAreaCache<HourlyForecast> hourlyCache =
            new TimedAreaCache<>(LIFETIME_MILLIS);
    private final TimedAreaCache<EnsemblePrecipitation> ensembleCache =
            new TimedAreaCache<>(LIFETIME_MILLIS);

    public CachedForecastRepository(ForecastSource<HourlyForecast> hourlySource,
                                    ForecastSource<EnsemblePrecipitation> ensembleSource,
                                    ConnectivityMonitor connectivity,
                                    Clock clock,
                                    Executor forecastExecutor,
                                    Executor mainExecutor) {
        this.hourlySource = requireNonNull(hourlySource, "hourlySource");
        this.ensembleSource = requireNonNull(ensembleSource, "ensembleSource");
        this.connectivity = requireNonNull(connectivity, "connectivity");
        this.clock = requireNonNull(clock, "clock");
        this.forecastExecutor = requireNonNull(forecastExecutor, "forecastExecutor");
        this.mainExecutor = requireNonNull(mainExecutor, "mainExecutor");
    }

    @Override
    public void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback) {
        load(origin, callback, hourlyCache, hourlySource);
    }

    @Override
    public void ensemble(GeoPoint origin, RepositoryCallback<EnsemblePrecipitation> callback) {
        load(origin, callback, ensembleCache, ensembleSource);
    }

    private <T> void load(GeoPoint origin, RepositoryCallback<T> callback,
                          TimedAreaCache<T> cache, ForecastSource<T> source) {
        requireNonNull(origin, "origin");
        requireNonNull(callback, "callback");
        WeatherAreaBucket bucket = WeatherAreaBucket.of(origin);
        String areaKey = bucket.key();
        forecastExecutor.execute(() -> {
            T cached = cache.get(areaKey, clock.wallClockMillis());
            if (cached != null) {
                succeed(callback, cached);
                return;
            }
            if (!connectivity.isOnline()) {
                fail(callback, new ForecastUnavailableException(
                        ForecastUnavailableException.Reason.NO_NETWORK));
                return;
            }
            try {
                long now = clock.wallClockMillis();
                T fetched = source.fetch(bucket.center(), now);
                cache.put(areaKey, fetched, now);
                succeed(callback, fetched);
            } catch (ForecastUnavailableException unavailable) {
                fail(callback, unavailable);
            } catch (RuntimeException unexpected) {
                // Nothing but the typed failure may reach the page.
                fail(callback, new ForecastUnavailableException(
                        ForecastUnavailableException.Reason.MALFORMED, unexpected));
            }
        });
    }

    private <T> void succeed(RepositoryCallback<T> callback, T result) {
        mainExecutor.execute(() -> callback.onSuccess(result));
    }

    private <T> void fail(RepositoryCallback<T> callback, ForecastUnavailableException error) {
        mainExecutor.execute(() -> callback.onError(error));
    }
}
