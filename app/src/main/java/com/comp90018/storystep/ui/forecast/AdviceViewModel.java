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
 * ensemble once, and recomputes from it whenever the stay time changes.
 */
public class AdviceViewModel extends ViewModel {

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
    /** The hourly forecast, only for each departure's sky icon; advice never waits for it. */
    @Nullable private HourlyForecast hourly;
    /** Bumped per request so a late answer for an older request is ignored. */
    private int generation;

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
        refresh();
        repository.hourly(origin, new RepositoryCallback<HourlyForecast>() {
            @Override
            public void onSuccess(HourlyForecast result) {
                hourly = result;
                AdviceUiState current = uiState.getValue();
                if (current != null && current.getStatus() == AdviceUiState.Status.CONTENT) {
                    uiState.setValue(AdviceUiState.content(current.getTripMillis(),
                            current.getAdvice(), current.getPlace(), result));
                }
            }

            @Override
            public void onError(Throwable error) {
                // Rows keep their day or night symbol; the advice itself does not need this.
            }
        });
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

    /** Retry: forget the held ensemble and ask again. */
    public void retry() {
        ensemble = null;
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
        if (ensemble != null && fresh(ensemble)) {
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

    private boolean fresh(EnsemblePrecipitation held) {
        long age = clock.wallClockMillis() - held.getFetchedAtMillis();
        return age >= 0 && age <= ForecastRepository.LIFETIME_MILLIS;
    }

    private void publish(long trip, EnsemblePrecipitation data) {
        try {
            uiState.setValue(AdviceUiState.content(trip,
                    advisor.advise(clock.wallClockMillis(), trip, data), data.getPlace(),
                    hourly));
        } catch (ForecastUnavailableException unavailable) {
            uiState.setValue(AdviceUiState.error(trip, unavailable.getReason()));
        }
    }
}
