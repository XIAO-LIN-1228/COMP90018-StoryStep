package com.comp90018.storystep.ui.nearby;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Screen state for the nearby list.
 *
 * <p>Before a session starts, the list is measured from a fixed reference area and labelled as
 * such. Once the coordinator publishes accepted samples it follows the user. Distances are
 * recomputed from the retained domain objects on every sample, but the repository is re-queried
 * only after meaningful movement, so walking does not produce a query per fix.
 *
 * <p>It does not own the location subscription and does not filter samples; the coordinator does
 * both, so the same session is shared with every other screen. The Fragment renders what this class
 * publishes and performs no geographic calculation.
 */
public class NearbyAttractionsViewModel extends ViewModel
        implements ExplorationCoordinator.Observer {

    /**
     * Movement required before asking the repository again. Smaller moves only recompute the
     * distances already on screen, which is free and cannot fail.
     */
    private static final double REQUERY_DISTANCE_METERS = 250.0;

    private final AttractionRepository repository;
    private final GeoPoint referenceOrigin;
    private final double radiusMeters;
    private final ExplorationCoordinator coordinator;

    private final MutableLiveData<NearbyUiState> uiState = new MutableLiveData<>();

    /** Origin the currently displayed distances are measured from. */
    private GeoPoint currentOrigin;

    /** Origin the last repository query used; drives the movement threshold. */
    private GeoPoint lastQueryOrigin;

    private NearbyUiState.OriginLabel originLabel = NearbyUiState.OriginLabel.REFERENCE_AREA;

    private NearbyUiState.SessionPhase sessionPhase = NearbyUiState.SessionPhase.IDLE;

    /**
     * The label to return to when a paused session resumes. Remembered because the permission level
     * is reported separately from the samples, and a resume must not silently upgrade an
     * approximate session to a precise one.
     */
    private NearbyUiState.OriginLabel liveLabel = NearbyUiState.OriginLabel.LIVE_PRECISE;

    /** Retained so distances can be recomputed without another query. */
    private List<Attraction> loadedAttractions = Collections.emptyList();

    /**
     * Incremented for every query. A callback carrying an older value is discarded, so a slow
     * response from a position the user has already left cannot restore the old list.
     */
    private int requestGeneration;

    public NearbyAttractionsViewModel(AttractionRepository repository,
                                      GeoPoint referenceOrigin,
                                      double radiusMeters,
                                      ExplorationCoordinator coordinator) {
        this.repository = repository;
        this.referenceOrigin = referenceOrigin;
        this.radiusMeters = radiusMeters;
        this.coordinator = coordinator;
        this.currentOrigin = referenceOrigin;
        coordinator.addObserver(this);
    }

    @Override
    protected void onCleared() {
        // The coordinator outlives this ViewModel; leaving the observer registered would keep a
        // destroyed screen's state alive and publishing.
        coordinator.removeObserver(this);
        super.onCleared();
    }

    public LiveData<NearbyUiState> getUiState() {
        return uiState;
    }

    /** Loads from the current origin. Safe to call again for a retry. */
    public void load() {
        query(currentOrigin);
    }

    /** Loads only if nothing has been published yet, so a rotation does not refetch. */
    public void loadIfNeeded() {
        if (uiState.getValue() == null) {
            load();
        }
    }

    // --- user commands -------------------------------------------------------------------------

    /** The user asked to begin exploring. */
    public void onStartRequested() {
        coordinator.requestStart();
    }

    /** The user asked to continue a paused session. */
    public void onResumeRequested() {
        coordinator.requestResume();
    }

    /** The user asked to finish. */
    public void onStopRequested() {
        coordinator.requestStop();
    }

    /**
     * The user pressed Start without having been told why location is needed. Shows the purpose;
     * the request itself follows from the notice's own action.
     */
    public void onExplanationRequired() {
        publishBlocked(NearbyUiState.Status.PERMISSION_REQUIRED);
    }

    // --- location access -----------------------------------------------------------------------

    /**
     * A routine re-check, such as when the screen becomes visible again.
     *
     * <p>Deliberately quiet when there is no session: someone who declined once should not be shown
     * the same explanation every time they open the app. Losing access during a session is
     * different and is always reported.
     */
    public void onLocationAccessChanged(LocationAccess access) {
        recordAccess(access);
        if (!access.isGranted() && sessionPhase != NearbyUiState.SessionPhase.IDLE) {
            publishAccessProblem(access);
        }
    }

    /**
     * The outcome of a request the user themselves triggered, so the result is always reported —
     * a permission dialog that appears to do nothing is worse than a denial that is explained.
     */
    public void onLocationAccessRequested(LocationAccess access) {
        recordAccess(access);
        if (!access.isGranted()) {
            publishAccessProblem(access);
        }
    }

    private void recordAccess(LocationAccess access) {
        if (access == LocationAccess.PRECISE) {
            liveLabel = NearbyUiState.OriginLabel.LIVE_PRECISE;
        } else if (access == LocationAccess.APPROXIMATE) {
            liveLabel = NearbyUiState.OriginLabel.LIVE_APPROXIMATE;
        }
        if (sessionPhase == NearbyUiState.SessionPhase.ACTIVE) {
            originLabel = liveLabel;
        }
        // The coordinator decides whether this ends a running session; it owns that transition.
        coordinator.onLocationAccessChanged(access);
    }

    private void publishAccessProblem(LocationAccess access) {
        switch (access) {
            case NOT_REQUESTED:
                publishBlocked(NearbyUiState.Status.PERMISSION_REQUIRED);
                break;
            case DENIED:
                publishBlocked(NearbyUiState.Status.PERMISSION_DENIED);
                break;
            case PERMANENTLY_DENIED:
                publishBlocked(NearbyUiState.Status.PERMISSION_PERMANENTLY_DENIED);
                break;
            default:
                throw new IllegalArgumentException("not an access problem: " + access);
        }
    }

    // --- coordinator observer ------------------------------------------------------------------

    @Override
    public void onSessionChanged(SessionSummary summary) {
        NearbyUiState.SessionPhase phase = toPhase(summary);
        if (phase == sessionPhase) {
            return;
        }
        NearbyUiState.SessionPhase previous = sessionPhase;
        sessionPhase = phase;

        if (phase == NearbyUiState.SessionPhase.ACTIVE) {
            originLabel = liveLabel;
            // Nothing is published here: the first accepted sample, or a waiting state from the
            // coordinator, is what actually changes the list.
            republishWithCurrentPhase();
            return;
        }
        if (phase.isPaused()) {
            // Stop claiming a current position. The distances stay on screen because they are
            // still the best available, but they are labelled as no longer being updated.
            if (originLabel != NearbyUiState.OriginLabel.REFERENCE_AREA) {
                originLabel = NearbyUiState.OriginLabel.LIVE_PAUSED;
            }
            republishWithCurrentPhase();
            return;
        }
        if (phase == NearbyUiState.SessionPhase.IDLE && previous != NearbyUiState.SessionPhase.IDLE) {
            returnToReferenceArea();
            return;
        }
        republishWithCurrentPhase();
    }

    @Override
    public void onSample(LocationSample sample) {
        GeoPoint acceptedOrigin = sample.getPosition();
        boolean firstFix = lastQueryOrigin == null;
        currentOrigin = acceptedOrigin;
        originLabel = liveLabel;

        if (firstFix
                || GeoCalculator.distanceMeters(lastQueryOrigin, acceptedOrigin)
                        >= REQUERY_DISTANCE_METERS) {
            query(acceptedOrigin);
        } else {
            // Same candidate set, new distances and therefore possibly a new order.
            publishContent(loadedAttractions);
        }
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        switch (reason) {
            case PERMISSION_DENIED:
                publishBlocked(NearbyUiState.Status.PERMISSION_DENIED);
                break;
            case PROVIDER_DISABLED:
                publishBlocked(NearbyUiState.Status.LOCATION_UNAVAILABLE);
                break;
            case APPROXIMATE_ONLY:
                // A downgrade in precision relabels the existing list. If nothing has loaded yet
                // there is no list to relabel, and publishing here would show a false "no results"
                // instead of the waiting state.
                liveLabel = NearbyUiState.OriginLabel.LIVE_APPROXIMATE;
                originLabel = liveLabel;
                if (loadedAttractions.isEmpty()) {
                    publishBlocked(NearbyUiState.Status.WAITING_FOR_FIX);
                } else {
                    publishContent(loadedAttractions);
                }
                break;
            case NO_RECENT_FIX:
                publishBlocked(NearbyUiState.Status.WAITING_FOR_FIX);
                break;
            default:
                throw new IllegalArgumentException("unhandled reason: " + reason);
        }
    }

    // --- internals -----------------------------------------------------------------------------

    private static NearbyUiState.SessionPhase toPhase(SessionSummary summary) {
        SessionState state = summary.getState();
        switch (state) {
            case STARTING:
                return NearbyUiState.SessionPhase.STARTING;
            case ACTIVE:
                return NearbyUiState.SessionPhase.ACTIVE;
            case PAUSED:
                return summary.getPauseReason().isResolvedByReturningToApp()
                        ? NearbyUiState.SessionPhase.PAUSED_BACKGROUND
                        : NearbyUiState.SessionPhase.PAUSED_ACCESS_LOST;
            case STOPPING:
                return NearbyUiState.SessionPhase.STOPPING;
            case IDLE:
            case COMPLETED:
            default:
                // A completed session is offered as Start again; the screen draws no distinction.
                return NearbyUiState.SessionPhase.IDLE;
        }
    }

    /** Returns to reference-area browsing after a session ends. */
    private void returnToReferenceArea() {
        originLabel = NearbyUiState.OriginLabel.REFERENCE_AREA;
        currentOrigin = referenceOrigin;
        lastQueryOrigin = null;
        query(referenceOrigin);
    }

    /** Re-emits the current state under a new phase, without asking the repository again. */
    private void republishWithCurrentPhase() {
        NearbyUiState current = uiState.getValue();
        if (current == null) {
            return;
        }
        if (current.getStatus() == NearbyUiState.Status.CONTENT) {
            publishContent(loadedAttractions);
        } else if (current.getStatus() == NearbyUiState.Status.LOADING) {
            uiState.setValue(NearbyUiState.loading(originLabel, sessionPhase));
        } else {
            uiState.setValue(NearbyUiState.blocked(
                    current.getStatus(), originLabel, sessionPhase, current.getItems()));
        }
    }

    private void query(GeoPoint origin) {
        final int generation = ++requestGeneration;
        lastQueryOrigin = origin;
        uiState.setValue(NearbyUiState.loading(originLabel, sessionPhase));
        repository.loadNearbyAttractions(origin, radiusMeters,
                new RepositoryCallback<List<Attraction>>() {
                    @Override
                    public void onSuccess(List<Attraction> attractions) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        loadedAttractions = attractions;
                        publishContent(attractions);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        uiState.setValue(NearbyUiState.error(originLabel, sessionPhase));
                    }
                });
    }

    private void publishContent(List<Attraction> attractions) {
        uiState.setValue(
                NearbyUiState.content(originLabel, sessionPhase, toItems(attractions)));
    }

    private void publishBlocked(NearbyUiState.Status status) {
        List<AttractionListItem> retained = uiState.getValue() == null
                ? Collections.<AttractionListItem>emptyList()
                : uiState.getValue().getItems();
        uiState.setValue(NearbyUiState.blocked(status, originLabel, sessionPhase, retained));
    }

    private List<AttractionListItem> toItems(List<Attraction> attractions) {
        List<AttractionListItem> items = new ArrayList<>(attractions.size());
        for (Attraction attraction : attractions) {
            items.add(new AttractionListItem(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getCategory(),
                    GeoCalculator.distanceMeters(currentOrigin, attraction.getLocation())));
        }
        // The repository sorts by distance from the origin it was queried with. After the user
        // moves without triggering a re-query, that order is stale, so sort by the distances just
        // computed. Ties break on id to keep the order reproducible.
        Collections.sort(items, (first, second) -> {
            int byDistance = Double.compare(
                    first.getDistanceMeters(), second.getDistanceMeters());
            return byDistance != 0 ? byDistance : first.getId().compareTo(second.getId());
        });
        return items;
    }
}
