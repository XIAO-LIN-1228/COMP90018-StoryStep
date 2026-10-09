package com.comp90018.storystep.ui.forecast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.ForecastRepository;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.time.Clock;

import java.util.Objects;

/**
 * The next 24 hours of rain chance for the area the home weather card describes (decision D-08).
 *
 * <p>The origin comes from the caller through {@link #init}, because the factory builds ViewModels
 * without arguments. Results survive rotation; only {@link #load()} asks again.
 */
public class ForecastViewModel extends ViewModel {

    /** How many upcoming hours the page lists. */
    static final int HOURS_SHOWN = 24;

    private final ForecastRepository repository;
    private final Clock clock;

    private final MutableLiveData<ForecastUiState> uiState = new MutableLiveData<>();

    private GeoPoint origin;
    private boolean referenceArea;

    public ForecastViewModel(ForecastRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Sets the origin once; later calls (after rotation) are ignored. Loads on first call. */
    public void init(GeoPoint origin, boolean referenceArea) {
        if (this.origin != null) {
            return;
        }
        this.origin = Objects.requireNonNull(origin, "origin");
        this.referenceArea = referenceArea;
        load();
    }

    public LiveData<ForecastUiState> getUiState() {
        return uiState;
    }

    /** Whether the forecast is for the reference area rather than a live position. */
    public boolean isReferenceArea() {
        return referenceArea;
    }

    /** Asks for the forecast; also the Retry action. */
    public void load() {
        if (origin == null) {
            return;
        }
        uiState.setValue(ForecastUiState.loading());
        repository.hourly(origin, new RepositoryCallback<HourlyForecast>() {
            @Override
            public void onSuccess(HourlyForecast forecast) {
                uiState.setValue(ForecastUiState.content(
                        forecast.upcoming(clock.wallClockMillis(), HOURS_SHOWN)));
            }

            @Override
            public void onError(Throwable error) {
                uiState.setValue(ForecastUiState.error(reasonOf(error)));
            }
        });
    }

    static ForecastUnavailableException.Reason reasonOf(Throwable error) {
        return error instanceof ForecastUnavailableException
                ? ((ForecastUnavailableException) error).getReason()
                : ForecastUnavailableException.Reason.MALFORMED;
    }
}
