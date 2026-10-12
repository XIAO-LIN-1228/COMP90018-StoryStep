package com.comp90018.storystep.ui.forecast;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.HourlyForecast;
import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.Collections;
import java.util.List;

/** What the hourly forecast page shows: loading, the next hours, or why there are none. */
public final class ForecastUiState {

    public enum Status { LOADING, CONTENT, ERROR }

    private final Status status;
    private final List<HourlyForecast.Hour> hours;
    private final ForecastPlace place;
    private final long referenceMillis;
    @Nullable
    private final ForecastUnavailableException.Reason reason;

    private ForecastUiState(Status status, List<HourlyForecast.Hour> hours, ForecastPlace place,
                            long referenceMillis,
                            @Nullable ForecastUnavailableException.Reason reason) {
        this.status = status;
        this.hours = Collections.unmodifiableList(hours);
        this.place = place;
        this.referenceMillis = referenceMillis;
        this.reason = reason;
    }

    static ForecastUiState loading() {
        return new ForecastUiState(Status.LOADING, Collections.emptyList(),
                ForecastPlace.unknown(), 0L, null);
    }

    static ForecastUiState content(List<HourlyForecast.Hour> hours, ForecastPlace place,
                                   long referenceMillis) {
        return new ForecastUiState(Status.CONTENT, hours, place, referenceMillis, null);
    }

    static ForecastUiState error(ForecastUnavailableException.Reason reason) {
        return new ForecastUiState(Status.ERROR, Collections.emptyList(),
                ForecastPlace.unknown(), 0L, reason);
    }

    /** The forecast location's timezone and sun times; unknown unless {@link Status#CONTENT}. */
    public ForecastPlace getPlace() {
        return place;
    }

    /**
     * The instant the hours were counted from; 0 unless {@link Status#CONTENT}. The hours span
     * midnight, so a row label must say which day an hour falls on relative to this instant.
     */
    public long getReferenceMillis() {
        return referenceMillis;
    }

    public Status getStatus() {
        return status;
    }

    /** The upcoming hours, in order; empty unless {@link Status#CONTENT}. */
    public List<HourlyForecast.Hour> getHours() {
        return hours;
    }

    /** Why there is no forecast; null unless {@link Status#ERROR}. */
    @Nullable
    public ForecastUnavailableException.Reason getReason() {
        return reason;
    }
}
