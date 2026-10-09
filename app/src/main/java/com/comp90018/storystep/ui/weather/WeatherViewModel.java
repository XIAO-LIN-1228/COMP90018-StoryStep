package com.comp90018.storystep.ui.weather;

import com.comp90018.storystep.domain.weather.WeatherFreshnessPolicy;
import com.comp90018.storystep.domain.weather.WeatherFreshness;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.time.Clock;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.model.WeatherSnapshot;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.WeatherRepository;
import com.comp90018.storystep.domain.repository.WeatherUnavailableException;
import com.comp90018.storystep.domain.weather.WeatherAreaBucket;
import com.comp90018.storystep.domain.weather.WeatherResult;
import com.comp90018.storystep.platform.location.LocationSource;

/**
 * The weather shown on the nearby screen and on the exploration dashboard.
 *
 * <p>One class serving both, because both ask the same question and the alternative — weather
 * logic duplicated into two screen ViewModels — is how the two would end up disagreeing about
 * whether an observation is stale.
 *
 * <p>It follows the coordinator's accepted samples so the observation describes where the user
 * actually is, and falls back to the reference area before a session exists. It starts no location
 * updates of its own: like every other screen, it observes the one subscription the coordinator
 * owns.
 *
 * <p>Requests occur on area changes or explicit refresh. Age-only updates never issue HTTP;
 * two screens requesting the same area share the repository cache and request coalescing.
 */
public class WeatherViewModel extends ViewModel implements ExplorationCoordinator.Observer {

    private final MutableLiveData<WeatherResult> contextWeather = new MutableLiveData<>();
    private WeatherResult lastResult;
    private final Clock clock;

    public LiveData<WeatherResult> getContextWeather() { return contextWeather; }

    private final WeatherRepository weather;
    private final ExplorationCoordinator coordinator;
    private final GeoPoint referenceOrigin;

    private final MutableLiveData<WeatherUiState> uiState = new MutableLiveData<>();

    /** The position the display currently describes. Starts as the reference area. */
    private GeoPoint currentOrigin;

    private WeatherUiState.AreaLabel areaLabel = WeatherUiState.AreaLabel.REFERENCE_AREA;

    /** The area the in-flight or last completed request was for; null before the first one. */
    private String requestedAreaKey;

    /**
     * Incremented per request. A callback carrying an older value is discarded, so a slow response
     * for an area the user has left cannot overwrite the newer one.
     */
    private int requestGeneration;

    public WeatherViewModel(WeatherRepository weather,
                            ExplorationCoordinator coordinator,
                            GeoPoint referenceOrigin) {
        this(weather, coordinator, referenceOrigin, new Clock() {
            public long wallClockMillis() { return System.currentTimeMillis(); }
            public long monotonicMillis() { return System.nanoTime() / 1_000_000L; }
        });
    }

    public WeatherViewModel(WeatherRepository weather, ExplorationCoordinator coordinator,
            GeoPoint referenceOrigin, Clock clock) {
        this.clock = clock;
        this.weather = weather;
        this.coordinator = coordinator;
        this.referenceOrigin = referenceOrigin;
        this.currentOrigin = referenceOrigin;
        // A screen opened mid-session must not spend its first seconds claiming the demonstration
        // area's weather. The coordinator replays its snapshots to a new observer but not its last
        // sample, so the position is taken from the route it has already accumulated.
        LocationSample lastSample = coordinator.routeSnapshot().getLastSample();
        if (lastSample != null) {
            this.currentOrigin = lastSample.getPosition();
            this.areaLabel = WeatherUiState.AreaLabel.LIVE;
        }
        coordinator.addObserver(this);
    }

    @Override
    protected void onCleared() {
        // The coordinator outlives this ViewModel; a left-behind observer would keep a destroyed
        // screen's state alive and publishing.
        ++requestGeneration;
        coordinator.removeObserver(this);
        super.onCleared();
    }

    /**
     * The point the card's weather is for: the live fix, or the reference origin before one. The
     * hourly forecast page is opened for this same point so the two always describe one area.
     */
    public GeoPoint getCurrentOrigin() {
        return currentOrigin;
    }

    /** Whether the card is showing live or reference-area weather. */
    public WeatherUiState.AreaLabel getAreaLabel() {
        return areaLabel;
    }

    public LiveData<WeatherUiState> getUiState() {
        return uiState;
    }

    /** Loads for the current area. Safe to call again; this is also the retry entry point. */
    public void load() {
        request(currentOrigin, true);
    }

    @Override
    public void onSample(LocationSample sample) {
        currentOrigin = sample.getPosition();
        boolean becameLive = areaLabel != WeatherUiState.AreaLabel.LIVE;
        areaLabel = WeatherUiState.AreaLabel.LIVE;
        // Only when the fix has crossed into a different cell is there a different answer to get.
        // Within one cell the stored observation already describes where the user is.
        request(currentOrigin, false);
        if (becameLive) {
            // The first fix usually lands in the cell the reference area already covers, so the
            // request above is skipped and nothing would republish. The observation is unchanged
            // but what it now describes is not: it stopped being the demonstration area's weather
            // and became the user's, and the card has to stop saying otherwise.
            relabelDisplayedState();
        }
    }

    /** Re-publishes what is on screen under the current area label, without refetching. */
    private void relabelDisplayedState() {
        WeatherUiState current = uiState.getValue();
        if (current == null || current.getAreaLabel() == areaLabel) {
            return;
        }
        switch (current.getStatus()) {
            case CONTENT:
                uiState.setValue(WeatherUiState.content(areaLabel, current.getCondition(),
                        current.getTemperatureCelsius(), current.getObservedAtMillis(),
                        current.getFreshness()));
                break;
            case UNAVAILABLE:
                uiState.setValue(WeatherUiState.unavailable(areaLabel, current.getReason()));
                break;
            case LOADING:
            default:
                uiState.setValue(WeatherUiState.loading(areaLabel));
                break;
        }
    }

    @Override
    public void onSessionChanged(SessionSummary summary) {
        if (summary.getState() == SessionState.IDLE
                || summary.getState() == SessionState.COMPLETED) {
            boolean changed = areaLabel != WeatherUiState.AreaLabel.REFERENCE_AREA;
            currentOrigin = referenceOrigin;
            areaLabel = WeatherUiState.AreaLabel.REFERENCE_AREA;
            if (changed) {
                request(currentOrigin, false);
                relabelDisplayedState();
            }
        }
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        // Weather is not location's problem. The last known area stays on screen, and losing a fix
        // is not a reason to blank a temperature that is still broadly true.
    }

    /**
     * @param force when true the request is made even if the area has not changed, which is what a
     *              user-pressed retry needs; the repository still serves a fresh cache without
     *              touching the network
     */
    private void request(GeoPoint origin, boolean force) {
        String areaKey = WeatherAreaBucket.of(origin).key();
        if (!force && areaKey.equals(requestedAreaKey)) {
            return;
        }
        if (!areaKey.equals(requestedAreaKey)) {
            lastResult = null;
            contextWeather.setValue(null);
            uiState.setValue(WeatherUiState.loading(areaLabel));
        }
        requestedAreaKey = areaKey;
        int generation = ++requestGeneration;
        if (uiState.getValue() == null) {
            uiState.setValue(WeatherUiState.loading(areaLabel));
        }
        weather.loadWeather(origin, new RepositoryCallback<WeatherResult>() {
            @Override
            public void onSuccess(WeatherResult result) {
                if (generation != requestGeneration) {
                    return;
                }
                lastResult = result;
                refreshAge();
            }

            @Override
            public void onError(Throwable error) {
                if (generation != requestGeneration) {
                    return;
                }
                lastResult = null;
                contextWeather.setValue(null);
                uiState.setValue(WeatherUiState.unavailable(areaLabel, reasonOf(error)));
            }
        });
    }

    /** Age-only refresh while visible: no request, even when the observation expires. */
    public void refreshAge() {
        if (lastResult == null) { return; }
        WeatherSnapshot snapshot = lastResult.getSnapshot();
        long now = clock.wallClockMillis();
        WeatherFreshnessPolicy policy =
                WeatherFreshnessPolicy.defaultPolicy();
        WeatherFreshness freshness = policy.classify(snapshot, now);
        if (freshness == WeatherFreshness.EXPIRED) {
            contextWeather.setValue(null);
            uiState.setValue(WeatherUiState.unavailable(areaLabel,
                    WeatherUnavailableException.Reason.NO_CACHE));
            return;
        }
        if (lastResult.getFreshness() == WeatherFreshness.STALE
                || now < snapshot.getFetchedAtMillis() || now < snapshot.getObservedAtMillis()
                || now - snapshot.getObservedAtMillis() > policy.getRefreshAfterMillis()) {
            freshness = WeatherFreshness.STALE;
        }
        WeatherResult aged = new WeatherResult(snapshot, freshness);
        if (!aged.equals(contextWeather.getValue())) { contextWeather.setValue(aged); }
        uiState.setValue(WeatherUiState.content(areaLabel, snapshot.getCondition(),
                snapshot.getTemperatureCelsius(), snapshot.getObservedAtMillis(), freshness));
    }

    private static WeatherUnavailableException.Reason reasonOf(Throwable error) {
        if (error instanceof WeatherUnavailableException) {
            return ((WeatherUnavailableException) error).getReason();
        }
        return WeatherUnavailableException.Reason.MALFORMED;
    }

    /** The reference area, for a screen that wants to label pre-session browsing. */
    public GeoPoint getReferenceOrigin() {
        return referenceOrigin;
    }
}
