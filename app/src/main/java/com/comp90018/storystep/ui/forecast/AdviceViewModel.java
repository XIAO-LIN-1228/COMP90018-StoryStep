package com.comp90018.storystep.ui.forecast;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.forecast.DepartureAdvisor;
import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;

import java.util.Objects;

/**
 * Departure advice for the planned walk (decision D-08): picks the stay time, asks for the
 * ensemble and the hourly forecast, and recomputes whenever the stay time, either forecast or the
 * current hour changes.
 */
public class AdviceViewModel extends ViewModel {

    /** Where the hourly forecast, and so the severe-weather check, stands. */
    public enum HourlyState { PENDING, FAILED, OK }

    /** The stay choices offered, in minutes; the first is the default (the planner's own). */
    public static final int[] STAY_MINUTES = {5, 15, 30, 60};

    private static final long MINUTE = 60_000L;

    private final ForecastRepository repository;
    private final Clock clock;
    private final DepartureAdvisor advisor = new DepartureAdvisor();

    private final MutableLiveData<AdviceUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Integer> stayMinutes = new MutableLiveData<>(STAY_MINUTES[0]);

    private GeoPoint origin;
    private long walkingMillis;
    private int stops;
    /** The last ensemble fetched, reused for stay changes while it is within the cache lifetime. */
    @Nullable private EnsemblePrecipitation ensemble;
    /** The hourly forecast: each departure's sky and the severe-weather check. Never waited for. */
    @Nullable private HourlyForecast hourly;
    private HourlyState hourlyState = HourlyState.PENDING;
    /** Bumped per request so a late answer for an older request is ignored. */
    private int generation;
    private int hourlyGeneration;
    /** The UTC hour the shown advice was computed in; a later hour makes "Now" stale. */
    private long publishedHour = Long.MIN_VALUE;

    public AdviceViewModel(ForecastRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Sets the walk once; later calls (after rotation) are ignored. Computes on first call. */
    public void init(GeoPoint origin, long walkingMillis, int stops) {
        if (this.origin != null) {
            return;
        }
        if (stops <= 0 || walkingMillis < 0) {
            throw new IllegalArgumentException("a walk needs at least one stop");
        }
        this.origin = Objects.requireNonNull(origin, "origin");
        this.walkingMillis = walkingMillis;
        this.stops = stops;
        requestHourly();
        refresh();
    }

    /**
     * Back on screen: an hour boundary passed since the advice was computed makes "Now" and the
     * first rows stale, so recompute (from held data where it is still fresh).
     */
    public void onResume() {
        AdviceUiState current = uiState.getValue();
        if (current != null && current.getStatus() == AdviceUiState.Status.CONTENT
                && hourOf(clock.wallClockMillis()) != publishedHour) {
            refresh();
        }
    }

    public LiveData<AdviceUiState> getUiState() {
        return uiState;
    }

    public LiveData<Integer> getStayMinutes() {
        return stayMinutes;
    }

    public int getStops() {
        return stops;
    }

    public void onStayChosen(int minutes) {
        Integer current = stayMinutes.getValue();
        if (current != null && current == minutes) {
            return;
        }
        stayMinutes.setValue(minutes);
        refresh();
    }

    /** Retry: forget both held forecasts and ask again. */
    public void retry() {
        ensemble = null;
        requestHourly();
        refresh();
    }

    long tripMillis() {
        Integer stay = stayMinutes.getValue();
        return walkingMillis + (stay == null ? STAY_MINUTES[0] : stay) * MINUTE * stops;
    }

    private void refresh() {
        if (origin == null) {
            return;
        }
        long trip = tripMillis();
        if (DepartureAdvisor.isTooLong(trip)) {
            generation++;
            uiState.setValue(AdviceUiState.tooLong(trip));
            return;
        }
        if (hourly != null && !fresh(hourly.getFetchedAtMillis())) {
            requestHourly();
        }
        if (ensemble != null && fresh(ensemble.getFetchedAtMillis())) {
            publish(trip, ensemble);
            return;
        }
        int request = ++generation;
        uiState.setValue(AdviceUiState.loading(trip));
        repository.ensemble(origin, new RepositoryCallback<EnsemblePrecipitation>() {
            @Override
            public void onSuccess(EnsemblePrecipitation result) {
                if (request != generation) {
                    return;
                }
                ensemble = result;
                publish(tripMillis(), result);
            }

            @Override
            public void onError(Throwable error) {
                if (request != generation) {
                    return;
                }
                uiState.setValue(AdviceUiState.error(tripMillis(),
                        ForecastViewModel.reasonOf(error)));
            }
        });
    }

    /** Asks for the hourly forecast; the advice is recomputed when it answers either way. */
    private void requestHourly() {
        int request = ++hourlyGeneration;
        hourlyState = HourlyState.PENDING;
        repository.hourly(origin, new RepositoryCallback<HourlyForecast>() {
            @Override
            public void onSuccess(HourlyForecast result) {
                if (request != hourlyGeneration) {
                    return;
                }
                hourly = result;
                hourlyState = HourlyState.OK;
                republish();
            }

            @Override
            public void onError(Throwable error) {
                if (request != hourlyGeneration) {
                    return;
                }
                hourlyState = HourlyState.FAILED;
                republish();
            }
        });
    }

    /** Severe weather changes which departure is best, so recompute rather than redraw. */
    private void republish() {
        AdviceUiState current = uiState.getValue();
        if (ensemble != null && current != null
                && current.getStatus() == AdviceUiState.Status.CONTENT) {
            publish(current.getTripMillis(), ensemble);
        }
    }

    private boolean fresh(long fetchedAtMillis) {
        long age = clock.wallClockMillis() - fetchedAtMillis;
        return age >= 0 && age <= ForecastRepository.LIFETIME_MILLIS;
    }

    private static long hourOf(long millis) {
        return Math.floorDiv(millis, 3_600_000L);
    }

    private void publish(long trip, EnsemblePrecipitation data) {
        long now = clock.wallClockMillis();
        try {
            uiState.setValue(AdviceUiState.content(trip,
                    advisor.advise(now, trip, data, hourlyState == HourlyState.OK ? hourly : null),
                    data.getPlace(), hourly, hourlyState));
            publishedHour = hourOf(now);
        } catch (ForecastUnavailableException unavailable) {
            uiState.setValue(AdviceUiState.error(trip, unavailable.getReason()));
        }
    }
}
