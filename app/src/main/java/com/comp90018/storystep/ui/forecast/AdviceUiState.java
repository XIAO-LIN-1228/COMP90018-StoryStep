package com.comp90018.storystep.ui.forecast;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.forecast.DepartureAdvice;
import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

/** What the departure advice page shows for the chosen stay time. */
public final class AdviceUiState {

    public enum Status { LOADING, CONTENT, ERROR, TOO_LONG }

    private final Status status;
    private final long tripMillis;
    @Nullable private final DepartureAdvice advice;
    private final ForecastPlace place;
    @Nullable private final ForecastUnavailableException.Reason reason;

    private AdviceUiState(Status status, long tripMillis, @Nullable DepartureAdvice advice,
                          ForecastPlace place,
                          @Nullable ForecastUnavailableException.Reason reason) {
        this.status = status;
        this.tripMillis = tripMillis;
        this.advice = advice;
        this.place = place;
        this.reason = reason;
    }

    static AdviceUiState loading(long tripMillis) {
        return new AdviceUiState(Status.LOADING, tripMillis, null, ForecastPlace.unknown(), null);
    }

    static AdviceUiState content(long tripMillis, DepartureAdvice advice, ForecastPlace place) {
        return new AdviceUiState(Status.CONTENT, tripMillis, advice, place, null);
    }

    static AdviceUiState error(long tripMillis, ForecastUnavailableException.Reason reason) {
        return new AdviceUiState(Status.ERROR, tripMillis, null, ForecastPlace.unknown(), reason);
    }

    static AdviceUiState tooLong(long tripMillis) {
        return new AdviceUiState(Status.TOO_LONG, tripMillis, null, ForecastPlace.unknown(), null);
    }

    /** The forecast location's timezone and sun times; times are shown in this zone. */
    public ForecastPlace getPlace() {
        return place;
    }

    public Status getStatus() {
        return status;
    }

    /** The whole walk for the chosen stay time: walking plus a stay at every stop. */
    public long getTripMillis() {
        return tripMillis;
    }

    /** Non-null only for {@link Status#CONTENT}. */
    @Nullable
    public DepartureAdvice getAdvice() {
        return advice;
    }

    /** Non-null only for {@link Status#ERROR}. */
    @Nullable
    public ForecastUnavailableException.Reason getReason() {
        return reason;
    }
}
