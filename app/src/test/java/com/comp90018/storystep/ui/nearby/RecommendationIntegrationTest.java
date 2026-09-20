package com.comp90018.storystep.ui.nearby;

import static org.junit.Assert.*;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.repository.TestSavedAttractions;
import com.comp90018.storystep.domain.attraction.AttractionFilter;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.recommendation.RecommendationEngine.*;
import com.comp90018.storystep.domain.repository.*;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.weather.*;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;

public class RecommendationIntegrationTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private static final GeoPoint ORIGIN = new GeoPoint(-37.7963, 144.9614);
    private final FakeClock clock = FakeClock.startingAt(midday());
    private final Places places = new Places();
    private final Visits visits = new Visits();
    private final ExplorationCoordinator coordinator = TestCoordinators.with(clock, new FakeLocationSource());

    private NearbyAttractionsViewModel model() {
        NearbyAttractionsViewModel model = new NearbyAttractionsViewModel(places,
                TestSavedAttractions.empty(), ORIGIN, 3000, coordinator, visits, clock);
        model.load();
        return model;
    }

    @Test public void rainExpiryModeAndSavedChangesReorderWithoutAQueryOrTargetChange() {
        NearbyAttractionsViewModel model = model();
        coordinator.selectDestination(places.data.get(0));
        assertEquals("out", first(model));
        model.onWeatherChanged(rain());
        assertEquals("in", first(model));
        assertTrue(model.getUiState().getValue().getItems().get(0).getReasons().contains(Reason.INDOOR_WET));
        model.setOrdering(Mode.NEARBY);
        assertEquals("in", first(model)); // Same coordinates: stable id tie-break.
        assertTrue(model.getUiState().getValue().getItems().get(0).getReasons().isEmpty());
        model.setOrdering(Mode.RECOMMENDED);
        clock.advance(31 * 60_000L);
        model.refreshContext();
        assertEquals("out", first(model));
        assertFalse(model.getUiState().getValue().getItems().get(1).getReasons().contains(Reason.INDOOR_WET));
        model.onSavedIdsChanged(Collections.singleton("in"));
        assertEquals("in", first(model));
        assertTrue(model.getUiState().getValue().getItems().get(0).getReasons().contains(Reason.SAVED));
        assertEquals(1, places.requests);
        assertEquals("in", coordinator.getDestination().getId());
    }

    @Test public void noResultsCanRecoverAndFiltersSurviveModeChanges() {
        NearbyAttractionsViewModel model = model();
        model.setFilter(AttractionFilter.NONE.withQuery("absent"));
        assertEquals(NearbyUiState.Status.EMPTY, model.getUiState().getValue().getStatus());
        model.setOrdering(Mode.NEARBY);
        assertEquals("absent", model.getFilter().getValue().getQuery());
        model.setFilter(AttractionFilter.NONE.withVenue(AttractionFilter.Venue.INDOOR));
        assertEquals(Collections.singletonList("in"), ids(model));
        model.setFilter(AttractionFilter.NONE);
        assertEquals(2, ids(model).size());
        assertEquals(1, places.requests);
    }

    @Test public void recordedVisitAndDeletionUpdateReasonsAndUnvisitedFilter() {
        NearbyAttractionsViewModel model = model();
        model.setFilter(AttractionFilter.NONE.withUnvisitedOnly(true));
        visits.ids = Collections.singleton("out");
        model.onPendingStoryChanged(new Visit("session", "out", clock.wallClockMillis(), "Out", "", true));
        assertEquals(Collections.singletonList("in"), ids(model));
        visits.ids = Collections.emptySet();
        visits.observer.onHistoryChanged();
        assertEquals(2, ids(model).size());
        assertEquals(1, places.requests);
    }

    @Test public void lateHistoryReadCannotUndoANewerVisit() {
        NearbyAttractionsViewModel model = model();
        visits.defer = true;
        model.onVisible();
        RepositoryCallback<Set<String>> old = visits.pending;
        model.onVisible();
        visits.pending.onSuccess(Collections.singleton("out"));
        old.onSuccess(Collections.emptySet());
        model.setFilter(AttractionFilter.NONE.withUnvisitedOnly(true));
        assertEquals(Collections.singletonList("in"), ids(model));
    }

    @Test public void oldAreaWeatherAndUnknownHistoryNeverInventReasons() {
        NearbyAttractionsViewModel model = model();
        model.onWeatherChanged(new WeatherResult(new WeatherSnapshot("other-area", WeatherCondition.RAIN,
                12, clock.wallClockMillis(), clock.wallClockMillis(), WeatherSnapshot.Provenance.CACHE), WeatherFreshness.FRESH));
        assertEquals("out", first(model));
        visits.fail = true;
        model.onVisible();
        for (AttractionListItem item : model.getUiState().getValue().getItems()) {
            assertFalse(item.getReasons().contains(Reason.UNVISITED));
            assertFalse(item.getReasons().contains(Reason.INDOOR_WET));
        }
    }

    @Test public void lateCallbacksAfterClearDoNotPublish() {
        NearbyAttractionsViewModel model = model();
        visits.defer = true;
        model.onVisible();
        NearbyUiState before = model.getUiState().getValue();
        model.onCleared();
        visits.pending.onSuccess(Collections.singleton("out"));
        assertSame(before, model.getUiState().getValue());
    }

    @Test public void smallFixChangesDoNotRebuildTheListForEverySample() {
        NearbyAttractionsViewModel model = model();
        model.onSample(new LocationSample(ORIGIN, 8f, clock.wallClockMillis(), clock.monotonicMillis(), LocationOrigin.DEVICE));
        NearbyUiState before = model.getUiState().getValue();
        model.onSample(new LocationSample(new GeoPoint(-37.796299, 144.9614), 8f,
                clock.wallClockMillis(), clock.monotonicMillis() + 5000, LocationOrigin.DEVICE));
        assertSame(before, model.getUiState().getValue());
        model.onSample(new LocationSample(new GeoPoint(-37.7961, 144.9614), 8f,
                clock.wallClockMillis(), clock.monotonicMillis() + 10000, LocationOrigin.DEVICE));
        assertNotSame(before, model.getUiState().getValue());
        assertEquals(1, places.requests);
    }

    @Test public void retainedRowsLoseWetWeatherReasonsEvenDuringPermissionRecovery() {
        NearbyAttractionsViewModel model = model();
        model.onWeatherChanged(rain());
        model.onLocationUnavailable(com.comp90018.storystep.platform.location.LocationSource.Reason.PERMISSION_DENIED);
        clock.advance(31 * 60_000L);
        model.refreshContext();
        assertEquals(NearbyUiState.Status.PERMISSION_DENIED, model.getUiState().getValue().getStatus());
        for (AttractionListItem item : model.getUiState().getValue().getItems()) {
            assertFalse(item.getReasons().contains(Reason.INDOOR_WET));
        }
    }

    @Test public void failedInitialSavedReadCanRecoverWhenTheScreenReturns() {
        DeferredSaves saves = new DeferredSaves();
        NearbyAttractionsViewModel model = new NearbyAttractionsViewModel(places,
                saves, ORIGIN, 3000, coordinator, visits, clock);
        model.load();
        saves.pending.onError(new IllegalStateException("temporarily unavailable"));
        assertEquals("out", first(model));
        model.onVisible();
        assertEquals(2, saves.reads);
        saves.pending.onSuccess(Collections.singleton("in"));
        assertEquals("in", first(model));
        assertTrue(model.getUiState().getValue().getItems().get(0).getReasons().contains(Reason.SAVED));
        assertEquals(1, places.requests);
    }

    @Test public void savedRecoveryCoalescesReadsAndCannotUndoANewerSaveEvent() {
        DeferredSaves saves = new DeferredSaves();
        NearbyAttractionsViewModel model = new NearbyAttractionsViewModel(places,
                saves, ORIGIN, 3000, coordinator, visits, clock);
        model.load();
        model.onVisible();
        model.onVisible();
        assertEquals(1, saves.reads);
        saves.pending.onError(new IllegalStateException("temporarily unavailable"));
        model.onVisible();
        assertEquals(2, saves.reads);
        saves.observer.onSavedIdsChanged(Collections.singleton("in"));
        saves.pending.onSuccess(Collections.emptySet());
        assertEquals("in", first(model));
        assertTrue(model.getUiState().getValue().getItems().get(0).isSaved());
        model.onVisible();
        assertEquals(2, saves.reads);
    }

    @Test public void searchAndVenueFilteringUseCandidatesBeyondTheDisplayLimit() {
        List<Attraction> data = new ArrayList<>();
        for (int i = 0; i < 35; i++) {
            data.add(Attraction.seeded("park" + i, "Park " + i, AttractionCategory.PARK,
                    "", IndoorClassification.OUTDOOR, ORIGIN));
        }
        data.add(Attraction.seeded("museum", "Museum", AttractionCategory.MUSEUM,
                "", IndoorClassification.INDOOR, ORIGIN));
        places.data = data;
        NearbyAttractionsViewModel model = model();
        assertEquals(30, ids(model).size());
        model.setFilter(AttractionFilter.NONE.withQuery("Museum")
                .withVenue(AttractionFilter.Venue.INDOOR));
        assertEquals(Collections.singletonList("museum"), ids(model));
        model.setOrdering(Mode.NEARBY);
        assertEquals(Collections.singletonList("museum"), ids(model));
        assertEquals(1, places.requests);
    }

    @Test public void localEveningChangesRankingWithoutARepositoryRequest() {
        NearbyAttractionsViewModel model = model();
        assertEquals("out", first(model));
        clock.advance(6 * 60 * 60_000L);
        model.refreshContext();
        assertEquals("in", first(model));
        assertTrue(model.getUiState().getValue().getItems().get(0).getReasons()
                .contains(Reason.INDOOR_EVENING));
        assertEquals(1, places.requests);
    }

    @Test public void cachedAttractionLabelsSurviveContextChangesAndPermissionRecovery() {
        places.freshness = com.comp90018.storystep.domain.attraction.AttractionFreshness.STALE;
        NearbyAttractionsViewModel model = model();
        model.onWeatherChanged(rain());
        model.setOrdering(Mode.NEARBY);
        model.onSavedIdsChanged(Collections.singleton("in"));
        model.onLocationUnavailable(com.comp90018.storystep.platform.location.LocationSource.Reason.PERMISSION_DENIED);
        clock.advance(31 * 60_000L);
        model.refreshContext();
        assertEquals(places.freshness, model.getUiState().getValue().getFreshness());
        assertEquals(2, ids(model).size());
        assertEquals(1, places.requests);
    }

    private WeatherResult rain() {
        return new WeatherResult(new WeatherSnapshot(WeatherAreaBucket.of(ORIGIN).key(), WeatherCondition.RAIN,
                12, clock.wallClockMillis(), clock.wallClockMillis(), WeatherSnapshot.Provenance.CACHE), WeatherFreshness.FRESH);
    }
    private static String first(NearbyAttractionsViewModel model) { return ids(model).get(0); }
    private static List<String> ids(NearbyAttractionsViewModel model) {
        List<String> ids = new ArrayList<>();
        for (AttractionListItem item : model.getUiState().getValue().getItems()) { ids.add(item.getId()); }
        return ids;
    }
    private static long midday() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(2026, Calendar.SEPTEMBER, 20, 12, 0, 0);
        return calendar.getTimeInMillis();
    }
    private static class Places implements AttractionRepository {
        int requests;
        com.comp90018.storystep.domain.attraction.AttractionFreshness freshness =
                com.comp90018.storystep.domain.attraction.AttractionFreshness.FRESH;
        List<Attraction> data = Arrays.asList(
                Attraction.seeded("in", "Inside", AttractionCategory.MUSEUM, "", IndoorClassification.INDOOR, ORIGIN),
                Attraction.seeded("out", "Outside", AttractionCategory.PARK, "", IndoorClassification.OUTDOOR, ORIGIN));
        public void loadNearbyAttractions(GeoPoint origin, double radius, RepositoryCallback<AttractionResult> callback) {
            requests++;
            callback.onSuccess(new AttractionResult(data, freshness));
        }
        public void loadAttraction(String id, RepositoryCallback<Attraction> callback) { throw new AssertionError(); }
    }
    private static class DeferredSaves implements SavedAttractionRepository {
        int reads;
        Observer observer;
        RepositoryCallback<Set<String>> pending;
        public void loadSavedIds(RepositoryCallback<Set<String>> callback) { reads++; pending = callback; }
        public void loadSaved(RepositoryCallback<List<SavedAttraction>> callback) { throw new AssertionError(); }
        public void save(Attraction attraction, RepositoryCallback<Boolean> callback) { throw new AssertionError(); }
        public void unsave(String id, RepositoryCallback<Boolean> callback) { throw new AssertionError(); }
        public void clearAll(RepositoryCallback<Integer> callback) { throw new AssertionError(); }
        public void addObserver(Observer observer) {
            this.observer = observer;
            observer.onSavedIdsChanged(Collections.emptySet());
        }
        public void removeObserver(Observer observer) { this.observer = null; }
    }
    private static class Visits implements HistoryRepository {
        Set<String> ids = Collections.emptySet();
        Observer observer;
        boolean defer, fail;
        RepositoryCallback<Set<String>> pending;
        public void loadVisitedAttractionIds(RepositoryCallback<Set<String>> callback) {
            if (defer) { pending = callback; }
            else if (fail) { callback.onError(new IllegalStateException()); }
            else { callback.onSuccess(ids); }
        }
        public void loadHistory(RepositoryCallback<List<SessionHistoryEntry>> callback) { callback.onSuccess(Collections.emptyList()); }
        public void loadDetail(String id, RepositoryCallback<SessionHistoryDetail> callback) { throw new AssertionError(); }
        public void delete(String id, RepositoryCallback<Boolean> callback) { throw new AssertionError(); }
        public void clearAll(RepositoryCallback<Integer> callback) { throw new AssertionError(); }
        public void addObserver(Observer observer) { this.observer = observer; }
        public void removeObserver(Observer observer) { this.observer = null; }
    }
}
