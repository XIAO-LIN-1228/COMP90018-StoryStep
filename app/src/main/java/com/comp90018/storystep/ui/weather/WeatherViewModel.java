package com.comp90018.storystep.ui.weather;

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
 * <p>Refreshing is left to the repository's freshness rule rather than throttled here. Asking on
 * every sample is cheap — a fresh cache hit does no I/O worth avoiding, and two screens asking at
 * once are coalesced into one request.
 */
public class WeatherViewModel extends ViewModel implements ExplorationCoordinator.Observer {

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
        coordinator.removeObserver(this);
        super.onCleared();
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
        // Nothing: whose weather this is depends on whether a fix has arrived, not on the session
        // state. A paused session keeps showing the last area it knew about.
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
                WeatherSnapshot snapshot = result.getSnapshot();
                uiState.setValue(WeatherUiState.content(areaLabel, snapshot.getCondition(),
                        snapshot.getTemperatureCelsius(), snapshot.getObservedAtMillis(),
                        result.getFreshness()));
            }

            @Override
            public void onError(Throwable error) {
                if (generation != requestGeneration) {
                    return;
                }
                // A failure for a new area must not erase an observation already on screen for the
                // old one; but with nothing to show, say so rather than spin.
                WeatherUiState current = uiState.getValue();
                if (current != null && current.getStatus() == WeatherUiState.Status.CONTENT) {
                    return;
                }
                uiState.setValue(WeatherUiState.unavailable(areaLabel, reasonOf(error)));
            }
        });
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
