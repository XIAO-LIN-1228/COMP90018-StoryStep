package com.comp90018.storystep.ui.nearby;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
import com.comp90018.storystep.domain.recommendation.RecommendationEngine;
import com.comp90018.storystep.domain.recommendation.RecommendationEngine.Mode;
import com.comp90018.storystep.domain.time.Clock;
import com.comp90018.storystep.domain.weather.WeatherResult;
import com.comp90018.storystep.domain.model.Visit;
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
 * <p>Foreground home discovery owns a location subscription independent of journey recording.
 * Until a device fix arrives, the reference area is only a labelled browsing fallback.
 * Accepted positions update distances; meaningful movement triggers a repository query.
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
    private final Clock clock;
    private LocationSource homeLocationSource;
    private int homeLocationGeneration;

    /** Foreground discovery has its own subscription and never records a journey. */
    public void startHomeLocation(LocationSource source, LocationAccess access) {
        stopHomeLocation();
        mapOrigin.setValue(null);
        if (!access.isGranted()) {
            returnToReferenceArea();
            publishAccessProblem(access);
            return;
        }
        liveLabel = access == LocationAccess.APPROXIMATE
                ? NearbyUiState.OriginLabel.LIVE_APPROXIMATE : NearbyUiState.OriginLabel.LIVE_PRECISE;
        homeLocationSource = source;
        final int generation = ++homeLocationGeneration;
        // Discovery accepts coarse fixes but still rejects stale and out-of-order observations.
        final com.comp90018.storystep.domain.geo.LocationSampleFilter filter =
                new com.comp90018.storystep.domain.geo.LocationSampleFilter(
                        new com.comp90018.storystep.domain.geo.LocationQualityPolicy(
                                access == LocationAccess.APPROXIMATE ? 3000.0 : 100.0,
                                15_000L, 0.0, 100.0));
        homeLocationUnavailable(LocationSource.Reason.NO_RECENT_FIX);
        source.startUpdates(new LocationSource.Listener() {
            @Override public void onLocationChanged(LocationSample sample) {
                if (generation != homeLocationGeneration) { return; }
                if (sample.getOrigin() == com.comp90018.storystep.domain.model.LocationOrigin.DEVICE
                        && filter.offer(sample, clock.monotonicMillis()).isAccepted()) {
                    applySample(sample);
                }
            }
            @Override public void onLocationUnavailable(LocationSource.Reason reason) {
                if (generation == homeLocationGeneration) { homeLocationUnavailable(reason); }
            }
        });
    }

    public void stopHomeLocation() {
        ++homeLocationGeneration;
        if (homeLocationSource != null) {
            homeLocationSource.stopUpdates();
            homeLocationSource = null;
        }
        if (originLabel != NearbyUiState.OriginLabel.REFERENCE_AREA) {
            originLabel = NearbyUiState.OriginLabel.LIVE_PAUSED;
        }
        mapOrigin.setValue(null);
        republishWithCurrentPhase();
    }

    private void homeLocationUnavailable(LocationSource.Reason reason) {
        if (originLabel != NearbyUiState.OriginLabel.REFERENCE_AREA) {
            originLabel = NearbyUiState.OriginLabel.LIVE_PAUSED;
        }
        mapOrigin.setValue(null);
        publishLocationUnavailable(reason);
    }

    private final RecommendationEngine recommendations = new RecommendationEngine();
    private final MutableLiveData<Mode> ordering = new MutableLiveData<>(Mode.RECOMMENDED);
    private WeatherResult recommendationWeather;
    private boolean savedKnown;
    private boolean savedReadInFlight;
    private int savedVersion;
    private boolean visitedKnown;
    private int visitedGeneration;

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
    private GeoPoint lastRankOrigin;
    private static final double RERANK_DISTANCE_METERS = 10.0;

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
        this(repository, savedAttractions, referenceOrigin, radiusMeters, coordinator, history,
                new Clock() {
                    public long wallClockMillis() { return System.currentTimeMillis(); }
                    public long monotonicMillis() { return android.os.SystemClock.elapsedRealtime(); }
                });
    }

    public NearbyAttractionsViewModel(AttractionRepository repository,
            SavedAttractionRepository savedAttractions, GeoPoint referenceOrigin,
            double radiusMeters, ExplorationCoordinator coordinator, HistoryRepository history,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.savedAttractions = savedAttractions;
        this.referenceOrigin = referenceOrigin;
        this.radiusMeters = radiusMeters;
        this.coordinator = coordinator;
        this.history = history;
        this.currentOrigin = referenceOrigin;
        LocationSample lastSample = coordinator.routeSnapshot().getLastSample();
        SessionState initialState = coordinator.summary().getState();
        if (lastSample != null && (initialState == SessionState.ACTIVE || initialState == SessionState.PAUSED)) {
            currentOrigin = lastSample.getPosition();
            liveLabel = coordinator.getAccess() == LocationAccess.APPROXIMATE
                    ? NearbyUiState.OriginLabel.LIVE_APPROXIMATE : NearbyUiState.OriginLabel.LIVE_PRECISE;
            originLabel = liveLabel;
        }
        mapOrigin.setValue(originLabel == NearbyUiState.OriginLabel.REFERENCE_AREA ? null : currentOrigin);
        coordinator.addObserver(this);
        savedAttractions.addObserver(this);
        // The immediate observer replay can be an unloaded repository's empty placeholder.
        savedKnown = false;
        reloadSavedIfUnknown();
        if (history != null) {
            history.addObserver(historyObserver);
            reloadVisited();
        }
    }

    public LiveData<AttractionFilter> getFilter() {
        return filterState;
    }

    public LiveData<Mode> getOrdering() { return ordering; }

    public void setOrdering(Mode mode) {
        if (mode != null && mode != ordering.getValue()) {
            ordering.setValue(mode);
            refreshContext();
        }
    }

    public void onWeatherChanged(WeatherResult result) {
        recommendationWeather = result;
        refreshContext();
    }

    /** Called while visible, including after returning from settings; never issues HTTP calls. */
    public void refreshContext() {
        NearbyUiState current = uiState.getValue();
        if (current == null) { return; }
        if (current.getStatus() == NearbyUiState.Status.CONTENT
                || current.getStatus() == NearbyUiState.Status.EMPTY) {
            publishContent(loadedAttractions);
        } else if (!loadedAttractions.isEmpty() && current.getStatus() != NearbyUiState.Status.LOADING
                && current.getStatus() != NearbyUiState.Status.ERROR) {
            uiState.setValue(current.withItems(toItems(loadedAttractions)));
        }
    }

    /** Reconcile changes made while this destination was not visible. */
    public void onVisible() {
        reloadSavedIfUnknown();
        reloadVisited();
        refreshContext();
    }

    /** Retry a failed local read on return, without duplicating a read or replacing a newer save. */
    private void reloadSavedIfUnknown() {
        if (savedKnown || savedReadInFlight) { return; }
        savedReadInFlight = true;
        final int savedReadVersion = savedVersion;
        savedAttractions.loadSavedIds(new RepositoryCallback<Set<String>>() {
            @Override public void onSuccess(Set<String> ids) {
                savedReadInFlight = false;
                if (savedVersion == savedReadVersion) { onSavedIdsChanged(ids); }
            }
            @Override public void onError(Throwable error) {
                savedReadInFlight = false;
                // Unknown remains neutral until a later visible-screen read or observer update.
            }
        });
    }

    @Override public void onPendingStoryChanged(Visit visit) {
        if (visit != null) {
            visitedIds = new HashSet<>(visitedIds);
            visitedIds.add(visit.getAttractionId());
            refreshContext();
            reloadVisited();
        }
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
        refreshContext();
    }

    private void reloadVisited() {
        if (history == null) {
            return;
        }
        final int generation = ++visitedGeneration;
        history.loadVisitedAttractionIds(new RepositoryCallback<Set<String>>() {
            @Override
            public void onSuccess(Set<String> ids) {
                if (generation != visitedGeneration) { return; }
                visitedKnown = true;
                visitedIds = ids;
                refreshContext();
            }

            @Override
            public void onError(Throwable error) {
                if (generation != visitedGeneration) { return; }
                visitedKnown = false;
                refreshContext();
            }
        });
    }

    @Override
    protected void onCleared() {
        ++homeLocationGeneration;
        if (homeLocationSource != null) {
            homeLocationSource.stopUpdates();
            homeLocationSource = null;
        }
        ++requestGeneration;
        ++visitedGeneration;
        ++savedVersion;
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
        if (mapOrigin.getValue() != null && sessionPhase == NearbyUiState.SessionPhase.ACTIVE) {
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
        ++savedVersion;
        savedKnown = true;
        savedIds = ids;
        // Rebuild the rows from what is already loaded: the saved state changed, the candidate set
        // did not, so there is nothing to ask the repository for.
        refreshContext();
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
        if (homeLocationSource != null) {
            republishWithCurrentPhase();
            return;
        }

        if (phase == NearbyUiState.SessionPhase.ACTIVE) {
            if (mapOrigin.getValue() != null) { originLabel = liveLabel; }
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
        if (homeLocationSource != null) { return; }
        applySample(sample);
    }

    private void applySample(LocationSample sample) {
        GeoPoint acceptedOrigin = sample.getPosition();
        boolean firstFix = lastQueryOrigin == null;
        boolean relabel = originLabel != liveLabel;
        currentOrigin = acceptedOrigin;
        mapOrigin.setValue(acceptedOrigin);
        originLabel = liveLabel;

        if (firstFix
                || GeoCalculator.distanceMeters(lastQueryOrigin, acceptedOrigin)
                        >= REQUERY_DISTANCE_METERS) {
            query(acceptedOrigin);
        } else if (relabel || lastRankOrigin == null
                || GeoCalculator.distanceMeters(lastRankOrigin, acceptedOrigin) >= RERANK_DISTANCE_METERS
                || uiState.getValue() == null
                || (uiState.getValue().getStatus() != NearbyUiState.Status.CONTENT
                        && uiState.getValue().getStatus() != NearbyUiState.Status.EMPTY)) {
            publishContent(loadedAttractions);
        }
    }

    @Override
    public void onLocationUnavailable(LocationSource.Reason reason) {
        if (homeLocationSource != null) { return; }
        publishLocationUnavailable(reason);
    }

    private void publishLocationUnavailable(LocationSource.Reason reason) {
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
                if (mapOrigin.getValue() != null) { originLabel = liveLabel; }
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
        mapOrigin.setValue(null);
        lastQueryOrigin = null;
        query(referenceOrigin);
    }

    /** Re-emits the current state under a new phase, without asking the repository again. */
    private void republishWithCurrentPhase() {
        NearbyUiState current = uiState.getValue();
        if (current == null) {
            return;
        }
        if ((current.getStatus() == NearbyUiState.Status.CONTENT || current.getStatus() == NearbyUiState.Status.EMPTY)) {
            publishContent(loadedAttractions);
        } else if (current.getStatus() == NearbyUiState.Status.LOADING) {
            uiState.setValue(NearbyUiState.loading(originLabel, sessionPhase));
        } else {
            uiState.setValue(NearbyUiState.blocked(
                    current.getStatus(), originLabel, sessionPhase, current.getItems(),
                    loadedFreshness, provenancesOf(loadedAttractions)));
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
        lastRankOrigin = currentOrigin;
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
        uiState.setValue(NearbyUiState.blocked(status, originLabel, sessionPhase, retained,
                loadedFreshness, provenancesOf(loadedAttractions)));
    }

    private List<AttractionListItem> toItems(List<Attraction> attractions) {
        List<Attraction> candidates = new ArrayList<>();
        for (Attraction attraction : attractions) {
            if (filter.matches(attraction.getName(), attraction.getIndoor(),
                    visitedIds.contains(attraction.getId()), attraction.getCategory(),
                    savedIds.contains(attraction.getId()))) {
                candidates.add(attraction);
            }
        }
        long nowMillis = clock.wallClockMillis();
        ZonedDateTime localNow = Instant.ofEpochMilli(nowMillis)
                .atZone(ZoneId.systemDefault());
        List<RecommendationEngine.RankedAttraction> ranked = recommendations.rank(
                candidates, currentOrigin, radiusMeters,
                RecommendationEngine.usableWeather(recommendationWeather, currentOrigin,
                        nowMillis), localNow.getHour(), localNow.getDayOfWeek(),
                localNow.getHour() * 60 + localNow.getMinute(),
                savedKnown ? savedIds : null, visitedKnown ? visitedIds : null,
                ordering.getValue(), TOP_PLACES);
        List<AttractionListItem> items = new ArrayList<>();
        for (RecommendationEngine.RankedAttraction rankedItem : ranked) {
            Attraction attraction = rankedItem.attraction;
            items.add(new AttractionListItem(attraction.getId(), attraction.getName(),
                    attraction.getCategory(), rankedItem.distanceMeters,
                    savedIds.contains(attraction.getId()), attraction.getLocation(),
                    attraction.getIndoor(), visitedIds.contains(attraction.getId()),
                    attraction.getImageUrl(), attraction.getPopularity(), attraction.getDescription(),
                    ordering.getValue() == Mode.RECOMMENDED
                            ? rankedItem.score.reasons : Collections.emptyList()));
        }
        return items;
    }

    /** How many places the nearby list shows. */
    static final int TOP_PLACES = 30;
}
