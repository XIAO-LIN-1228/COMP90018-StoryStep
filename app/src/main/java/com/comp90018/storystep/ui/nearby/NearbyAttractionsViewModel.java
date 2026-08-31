package com.comp90018.storystep.ui.nearby;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationSample;
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
 * such. Once accepted location samples arrive it follows the user. Distances are recomputed from
 * the retained domain objects on every accepted sample, but the repository is re-queried only after
 * meaningful movement, so walking does not produce a query per fix.
 *
 * <p>The Fragment only renders what this class publishes; it performs no geographic calculation.
 */
public class NearbyAttractionsViewModel extends ViewModel {

    /**
     * Movement required before asking the repository again. Smaller moves only recompute the
     * distances already on screen, which is free and cannot fail.
     */
    private static final double REQUERY_DISTANCE_METERS = 250.0;

    private final AttractionRepository repository;
    private final GeoPoint referenceOrigin;
    private final double radiusMeters;
    private final LocationSampleFilter locationFilter;

    private final MutableLiveData<NearbyUiState> uiState = new MutableLiveData<>();

    /** Origin the currently displayed distances are measured from. */
    private GeoPoint currentOrigin;

    /** Origin the last repository query used; drives the movement threshold. */
    private GeoPoint lastQueryOrigin;

    private NearbyUiState.OriginLabel originLabel = NearbyUiState.OriginLabel.REFERENCE_AREA;

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
                                      LocationSampleFilter locationFilter) {
        this.repository = repository;
        this.referenceOrigin = referenceOrigin;
        this.radiusMeters = radiusMeters;
        this.locationFilter = locationFilter;
        this.currentOrigin = referenceOrigin;
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

    /**
     * Reports the user's location-permission level.
     *
     * <p>Browsing the reference area stays available in every denied case: losing location must
     * reduce what the screen can claim, not empty it.
     */
    public void onLocationAccessChanged(LocationAccess access) {
        switch (access) {
            case PRECISE:
                originLabel = NearbyUiState.OriginLabel.LIVE_PRECISE;
                break;
            case APPROXIMATE:
                originLabel = NearbyUiState.OriginLabel.LIVE_APPROXIMATE;
                break;
            case NOT_REQUESTED:
                publishBlocked(NearbyUiState.Status.PERMISSION_REQUIRED);
                return;
            case DENIED:
                publishBlocked(NearbyUiState.Status.PERMISSION_DENIED);
                return;
            case PERMANENTLY_DENIED:
                publishBlocked(NearbyUiState.Status.PERMISSION_PERMANENTLY_DENIED);
                return;
            default:
                throw new IllegalArgumentException("unhandled access: " + access);
        }
        // Granted, but no fix has arrived yet.
        publishBlocked(NearbyUiState.Status.WAITING_FOR_FIX);
    }

    /**
     * Offers a new position observation.
     *
     * @param sample             the reported sample
     * @param nowMonotonicMillis current monotonic time, injected so tests control the clock
     */
    public void onLocationSample(LocationSample sample, long nowMonotonicMillis) {
        if (!locationFilter.offer(sample, nowMonotonicMillis).isAccepted()) {
            // A rejected sample changes nothing on screen. It is not an error either: the next
            // fix is usually fine, and flashing a failure state on every noisy reading is worse
            // than showing slightly older distances.
            return;
        }
        GeoPoint acceptedOrigin = sample.getPosition();
        boolean firstFix = lastQueryOrigin == null;
        currentOrigin = acceptedOrigin;

        if (firstFix
                || GeoCalculator.distanceMeters(lastQueryOrigin, acceptedOrigin)
                        >= REQUERY_DISTANCE_METERS) {
            query(acceptedOrigin);
        } else {
            // Same candidate set, new distances and therefore possibly a new order.
            publishContent(loadedAttractions);
        }
    }

    /** Reports that location updates cannot currently produce a usable position. */
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
                originLabel = NearbyUiState.OriginLabel.LIVE_APPROXIMATE;
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

    /**
     * Returns to reference-area browsing, for example when a session stops.
     *
     * <p>The filter is reset so a later session does not compare its first fix against a position
     * from the previous one.
     */
    public void onLocationTrackingStopped() {
        locationFilter.reset();
        originLabel = NearbyUiState.OriginLabel.REFERENCE_AREA;
        currentOrigin = referenceOrigin;
        lastQueryOrigin = null;
        query(referenceOrigin);
    }

    private void query(GeoPoint origin) {
        final int generation = ++requestGeneration;
        lastQueryOrigin = origin;
        uiState.setValue(NearbyUiState.loading(originLabel));
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
                        uiState.setValue(NearbyUiState.error(originLabel));
                    }
                });
    }

    private void publishContent(List<Attraction> attractions) {
        uiState.setValue(NearbyUiState.content(originLabel, toItems(attractions)));
    }

    private void publishBlocked(NearbyUiState.Status status) {
        List<AttractionListItem> retained = uiState.getValue() == null
                ? Collections.<AttractionListItem>emptyList()
                : uiState.getValue().getItems();
        uiState.setValue(NearbyUiState.blocked(status, originLabel, retained));
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