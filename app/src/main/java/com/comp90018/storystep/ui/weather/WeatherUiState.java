package com.comp90018.storystep.ui.weather;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.weather.WeatherFreshness;

import java.util.Objects;

/**
 * Everything the weather display can say.
 *
 * <p>Three statuses, and the middle one is the reason this is a state object rather than a
 * temperature string: an observation the app is showing while a refresh failed has to look
 * different from one it just fetched. Without that distinction the screen would present a
 * forty-minute-old reading as the current weather.
 *
 * <p>{@link #getAreaLabel()} answers a separate question from all of that — whether the
 * observation describes where the user actually is, or the demonstration reference area they are
 * browsing from before starting a session. The two must never look the same, for the same reason
 * the nearby list labels its own origin.
 */
public final class WeatherUiState {

    public enum Status {

        /** A request is running and nothing is on screen yet. */
        LOADING,

        /** There is an observation to show. Consult {@link #getFreshness()} for its label. */
        CONTENT,

        /** Nothing can be shown. {@link #getReason()} says why, and what the user can do. */
        UNAVAILABLE
    }

    /** Whose weather this is. */
    public enum AreaLabel {

        /** The demonstration reference area, browsed before a session starts. Not the user. */
        REFERENCE_AREA,

        /** The area the user's own fix falls in. */
        LIVE
    }

    private final Status status;
    private final AreaLabel areaLabel;
    private final WeatherCondition condition;
    private final double temperatureCelsius;
    private final long observedAtMillis;
    private final WeatherFreshness freshness;
    private final WeatherUnavailableException.Reason reason;

    private WeatherUiState(Status status,
                           AreaLabel areaLabel,
                           WeatherCondition condition,
                           double temperatureCelsius,
                           long observedAtMillis,
                           WeatherFreshness freshness,
                           WeatherUnavailableException.Reason reason) {
        this.status = Objects.requireNonNull(status, "status");
        this.areaLabel = Objects.requireNonNull(areaLabel, "areaLabel");
        this.condition = condition;
        this.temperatureCelsius = temperatureCelsius;
        this.observedAtMillis = observedAtMillis;
        this.freshness = freshness;
        this.reason = reason;
    }

    public static WeatherUiState loading(AreaLabel areaLabel) {
        return new WeatherUiState(Status.LOADING, areaLabel, null, 0.0, 0L, null, null);
    }

    public static WeatherUiState content(AreaLabel areaLabel,
                                         WeatherCondition condition,
                                         double temperatureCelsius,
                                         long observedAtMillis,
                                         WeatherFreshness freshness) {
        return new WeatherUiState(Status.CONTENT, areaLabel,
                Objects.requireNonNull(condition, "condition"), temperatureCelsius,
                observedAtMillis, Objects.requireNonNull(freshness, "freshness"), null);
    }

    public static WeatherUiState unavailable(AreaLabel areaLabel,
                                             WeatherUnavailableException.Reason reason) {
        return new WeatherUiState(Status.UNAVAILABLE, areaLabel, null, 0.0, 0L, null,
                Objects.requireNonNull(reason, "reason"));
    }

    public Status getStatus() {
        return status;
    }

    public AreaLabel getAreaLabel() {
        return areaLabel;
    }

    /** Null unless {@link #getStatus()} is {@link Status#CONTENT}. */
    public WeatherCondition getCondition() {
        return condition;
    }

    /** Meaningful only in {@link Status#CONTENT}. */
    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    /** When the provider observed it. Zero outside {@link Status#CONTENT}. */
    public long getObservedAtMillis() {
        return observedAtMillis;
    }

    /** Null unless {@link Status#CONTENT}. Decides whether an age label is shown. */
    public WeatherFreshness getFreshness() {
        return freshness;
    }

    /** Null unless {@link Status#UNAVAILABLE}. Decides whether a retry is worth offering. */
    public WeatherUnavailableException.Reason getReason() {
        return reason;
    }

    /** True when an observation is on screen but is not current. */
    public boolean isStale() {
        return status == Status.CONTENT && freshness == WeatherFreshness.STALE;
    }

    /**
     * True when the user pressing retry could plausibly change the outcome.
     *
     * <p>False for a malformed response: the provider is answering, it is answering wrongly, and
     * asking again immediately is a loop rather than a recovery.
     */
    public boolean isRetryable() {
        if (status != Status.UNAVAILABLE) {
            return false;
        }
        return reason != WeatherUnavailableException.Reason.MALFORMED;
    }
}
