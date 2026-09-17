package com.comp90018.storystep.ui.nearby;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.domain.attraction.AttractionFreshness;
import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.attraction.AttractionFilter;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.HistoryRepository;
import com.comp90018.storystep.domain.repository.AttractionUnavailableException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        implements ExplorationCoordinator.Observer, SavedAttractionRepository.Observer {

    /**
     * Movement required before asking the repository again. Smaller moves only recompute the
     * distances already on screen, which is free and cannot fail.
     */
    private static final double REQUERY_DISTANCE_METERS = 250.0;

    private final AttractionRepository repository;
    private final SavedAttractionRepository savedAttractions;
    private final GeoPoint referenceOrigin;
    private final double radiusMeters;
    private final ExplorationCoordinator coordinator;

    private final MutableLiveData<NearbyUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> saveFailed = new MutableLiveData<>(false);
    // The origin the map centres on: the reference area until a real fix arrives, then the
    // accepted position. Separate from the list state so the map can move without a re-query.
    private final MutableLiveData<GeoPoint> mapOrigin = new MutableLiveData<>();

    /** Identifiers the saved store last reported. Drives each row's bookmark. */
    private Set<String> savedIds = Collections.emptySet();
    // Places a visit has ever been recorded at. Read by the Unvisited filter and the unlock count.
    private Set<String> visitedIds = Collections.emptySet();
    @androidx.annotation.Nullable private final HistoryRepository history;
    private final HistoryRepository.Observer historyObserver = this::reloadVisited;
    private AttractionFilter filter = AttractionFilter.NONE;
    private final MutableLiveData<AttractionFilter> filterState =
            new MutableLiveData<>(AttractionFilter.NONE);
    // {visited, total} among the loaded places, before the filter is applied.
    private final MutableLiveData<int[]> unlockProgress = new MutableLiveData<>(new int[] {0, 0});

    /** Rows whose save is still being written; a further tap on one of them is ignored. */
    private final Set<String> pendingSaveIds = new HashSet<>();

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
     * How current {@link #loadedAttractions} is.
     *
     * <p>Held alongside the list because the list is republished without being reloaded — every
     * time the user moves less than the re-query threshold, distances are recomputed from the same
     * records. Recomputing a distance does not make stored content current, so the label has to
     * survive that republish rather than being re-derived as fresh.
     */
    private AttractionFreshness loadedFreshness = AttractionFreshness.FRESH;

    /**
     * Incremented for every query. A callback carrying an older value is discarded, so a slow
     * response from a position the user has already left cannot restore the old list.
     */
    private int requestGeneration;

    public NearbyAttractionsViewModel(AttractionRepository repository,
                                      SavedAttractionRepository savedAttractions,
                                      GeoPoint referenceOrigin,
                                      double radiusMeters,
                                      ExplorationCoordinator coordinator) {
        this(repository, savedAttractions, referenceOrigin, radiusMeters, coordinator, null);
    }

    public NearbyAttractionsViewModel(AttractionRepository repository,
                                      SavedAttractionRepository savedAttractions,
                                      GeoPoint referenceOrigin,
                                      double radiusMeters,
                                      ExplorationCoordinator coordinator,
                                      @androidx.annotation.Nullable HistoryRepository history) {
        this.repository = repository;
        this.savedAttractions = savedAttractions;
        this.referenceOrigin = referenceOrigin;
        this.radiusMeters = radiusMeters;
        this.coordinator = coordinator;
        this.history = history;
        this.currentOrigin = referenceOrigin;
        mapOrigin.setValue(referenceOrigin);
        coordinator.addObserver(this);
        savedAttractions.addObserver(this);
        if (history != null) {
            history.addObserver(historyObserver);
            reloadVisited();
        }
    }

    public LiveData<AttractionFilter> getFilter() {
        return filterState;
    }

    /** {visited, total} among the places currently loaded, independent of the filter. */
    public LiveData<int[]> getUnlockProgress() {
        return unlockProgress;
    }

    /** Applies a new filter to what is already loaded; nothing is asked of the repository. */
    public void setFilter(AttractionFilter newFilter) {
        if (newFilter.equals(filter)) {
            return;
        }
        filter = newFilter;
        filterState.setValue(newFilter);
        NearbyUiState current = uiState.getValue();
        if (current != null && current.getStatus() == NearbyUiState.Status.CONTENT) {
            publishContent(loadedAttractions);
        }
    }

    private void reloadVisited() {
        if (history == null) {
            return;
        }
        history.loadVisitedAttractionIds(new RepositoryCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                visitedIds = ids;
                NearbyUiState current = uiState.getValue();
                if (current != null && current.getStatus() == NearbyUiState.Status.CONTENT) {
                    publishContent(loadedAttractions);
                }
            }

            @Override
            public void onError(Throwable error) {
                // Visits are a refinement of the list, not the list: keep what we have.
            }
        });
    }

    @Override
    protected void onCleared() {
        // Both collaborators outlive this ViewModel; leaving the observers registered would keep a
        // destroyed screen's state alive and publishing.
        coordinator.removeObserver(this);
        savedAttractions.removeObserver(this);
        if (history != null) {
            history.removeObserver(historyObserver);
        }
        super.onCleared();
    }

    public LiveData<NearbyUiState> getUiState() {
        return uiState;
    }

    /** True once a save or unsave has failed; the screen consumes it and reports it once. */
    public LiveData<GeoPoint> getMapOrigin() {
        return mapOrigin;
    }

    public LiveData<Boolean> getSaveFailed() {
        return saveFailed;
    }

    public void consumeSaveFailure() {
        if (Boolean.TRUE.equals(saveFailed.getValue())) {
            saveFailed.setValue(false);
        }
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

    // --- saving --------------------------------------------------------------------------------

    /**
     * The user pressed a row's bookmark.
     *
     * <p>Ignored while a write for that row is outstanding, so a rapid double tap resolves to one
     * write per completed round trip rather than two racing at the same record. The rows the user
     * is not touching stay interactive.
     */
    public void onSaveToggleRequested(AttractionListItem item) {
        if (item == null || pendingSaveIds.contains(item.getId())) {
            return;
        }
        final String attractionId = item.getId();
        Attraction attraction = findLoaded(attractionId);
        boolean wasSaved = savedIds.contains(attractionId);
        if (attraction == null && !wasSaved) {
            // Nothing to copy into the saved record; the list and the loaded set have diverged.
            return;
        }
        pendingSaveIds.add(attractionId);

        RepositoryCallback<Boolean> callback = new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean changed) {
                pendingSaveIds.remove(attractionId);
            }

            @Override
            public void onError(Throwable error) {
                pendingSaveIds.remove(attractionId);
                // The identifier set was never updated, so the rows already show the truth; only
                // the failure itself still needs reporting.
                saveFailed.setValue(true);
            }
        };
        if (wasSaved) {
            savedAttractions.unsave(attractionId, callback);
        } else {
            savedAttractions.save(attraction, callback);
        }
    }

    @Override
    public void onSavedIdsChanged(Set<String> ids) {
        savedIds = ids;
        // Rebuild the rows from what is already loaded: the saved state changed, the candidate set
        // did not, so there is nothing to ask the repository for.
        NearbyUiState current = uiState.getValue();
        if (current != null && current.getStatus() == NearbyUiState.Status.CONTENT) {
            publishContent(loadedAttractions);
        }
    }

    private Attraction findLoaded(String attractionId) {
        for (Attraction attraction : loadedAttractions) {
            if (attraction.getId().equals(attractionId)) {
                return attraction;
            }
        }
        return null;
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
        mapOrigin.setValue(acceptedOrigin);
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
        mapOrigin.setValue(referenceOrigin);
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
                new RepositoryCallback<AttractionResult>() {
                    @Override
                    public void onSuccess(AttractionResult result) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        loadedAttractions = result.getAttractions();
                        loadedFreshness = result.getFreshness();
                        publishContent(loadedAttractions);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (generation != requestGeneration) {
                            return;
                        }
                        uiState.setValue(NearbyUiState.error(
                                originLabel, sessionPhase, reasonOf(error)));
                    }
                });
    }

    /**
     * The provider outcome behind a failure, or {@code null} when it was not one.
     *
     * <p>An error this app caused — a malformed request built here, say — is deliberately left
     * without a reason rather than being labelled as a network problem the user could act on.
     */
    private static AttractionUnavailableException.Reason reasonOf(Throwable error) {
        return error instanceof AttractionUnavailableException
                ? ((AttractionUnavailableException) error).getReason()
                : null;
    }

    private void publishContent(List<Attraction> attractions) {
        int visited = 0;
        for (Attraction attraction : attractions) {
            if (visitedIds.contains(attraction.getId())) {
                visited++;
            }
        }
        unlockProgress.setValue(new int[] {visited, attractions.size()});
        uiState.setValue(NearbyUiState.content(originLabel, sessionPhase, toItems(attractions),
                loadedFreshness, provenancesOf(attractions)));
    }

    /** The sources on screen, so the view can credit each one its licence requires crediting. */
    private static Set<AttractionProvenance> provenancesOf(List<Attraction> attractions) {
        Set<AttractionProvenance> sources = EnumSet.noneOf(AttractionProvenance.class);
        for (Attraction attraction : attractions) {
            sources.add(attraction.getProvenance());
        }
        return sources;
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
            boolean visited = visitedIds.contains(attraction.getId());
            if (!filter.matches(attraction.getName(), attraction.getIndoor(), visited)) {
                continue;
            }
            items.add(new AttractionListItem(
                    attraction.getId(),
                    attraction.getName(),
                    attraction.getCategory(),
                    GeoCalculator.distanceMeters(currentOrigin, attraction.getLocation()),
                    savedIds.contains(attraction.getId()),
                    attraction.getLocation(),
                    attraction.getIndoor(),
                    visited,
                    attraction.getImageUrl(),
                    attraction.getPopularity()));
        }
        // The repository sorts by distance from the origin it was queried with. After the user
        // moves without triggering a re-query, that order is stale, so sort by the distances just
        // computed. Ties break on id to keep the order reproducible.
        // The best-known places first - Wikidata's sitelink count - with distance deciding
        // among places nobody has written about, and the id keeping the order reproducible.
        // Then the top thirty: a list of everything within a kilometre is a directory, not a
        // recommendation.
        Collections.sort(items, (first, second) -> {
            int byPopularity = Integer.compare(second.getPopularity(), first.getPopularity());
            if (byPopularity != 0) {
                return byPopularity;
            }
            int byDistance = Double.compare(
                    first.getDistanceMeters(), second.getDistanceMeters());
            return byDistance != 0 ? byDistance : first.getId().compareTo(second.getId());
        });
        return items.size() > TOP_PLACES ? new ArrayList<>(items.subList(0, TOP_PLACES)) : items;
    }

    /** How many places the nearby list shows. */
    static final int TOP_PLACES = 30;
}
