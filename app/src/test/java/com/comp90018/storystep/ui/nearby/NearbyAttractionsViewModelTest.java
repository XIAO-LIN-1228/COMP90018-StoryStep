package com.comp90018.storystep.ui.nearby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.geo.LocationQualityPolicy;
import com.comp90018.storystep.domain.geo.LocationSampleFilter;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.model.SessionSummary;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.domain.session.SessionStateMachine;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Verifies the Fragment -> ViewModel -> Repository path without any Android framework. */
public class NearbyAttractionsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    /** Metres per degree of latitude on the mean sphere the calculator uses. */
    private static final double METERS_PER_DEGREE_LATITUDE = 111_194.9;

    private static final long START_MONOTONIC = 1_000L;

    private static Attraction attraction(String id, double latitude, double longitude) {
        return new Attraction(id, "Name " + id, AttractionCategory.PARK, "Description",
                false, new GeoPoint(latitude, longitude));
    }

    /** A point the given number of metres due north of {@link #ORIGIN}. */
    private static GeoPoint northOfOrigin(double meters) {
        return new GeoPoint(
                ORIGIN.getLatitude() + meters / METERS_PER_DEGREE_LATITUDE,
                ORIGIN.getLongitude());
    }

    private static LocationSample sampleAt(GeoPoint position,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        return new LocationSample(position, accuracyMeters, 1_700_000_000_000L, monotonicMillis,
                LocationOrigin.DEVICE);
    }

    private static NearbyAttractionsViewModel viewModelFor(AttractionRepository repository) {
        return viewModelFor(repository, 3000.0);
    }

    private static NearbyAttractionsViewModel viewModelFor(AttractionRepository repository,
                                                           double radiusMeters) {
        return new NearbyAttractionsViewModel(repository, ORIGIN, radiusMeters, coordinator());
    }

    /**
     * A real coordinator over fakes. The session transitions themselves are covered by
     * {@code ExplorationCoordinatorTest}; here it exists so the ViewModel has the collaborator it
     * publishes through, and the tests drive the ViewModel's observer methods directly.
     */
    private static ExplorationCoordinator coordinator() {
        FakeClock clock = FakeClock.startingAt(1_000L);
        return new ExplorationCoordinator(
                new SessionStateMachine(clock),
                new InMemorySessionStore(clock),
                new FakeLocationSource(),
                new LocationSampleFilter(LocationQualityPolicy.defaultPolicy()),
                clock);
    }

    private static SessionSummary sessionIn(SessionState state, PauseReason reason) {
        return new SessionSummary("session-1", state, reason, 1_000L, 0L, 1);
    }

    private static NearbyUiState observe(NearbyAttractionsViewModel viewModel) {
        final List<NearbyUiState> seen = new ArrayList<>();
        viewModel.getUiState().observeForever(seen::add);
        return seen.isEmpty() ? null : seen.get(seen.size() - 1);
    }

    private static List<String> idsOf(NearbyUiState state) {
        List<String> ids = new ArrayList<>();
        for (AttractionListItem item : state.getItems()) {
            ids.add(item.getId());
        }
        return ids;
    }

    // --- baseline list behaviour ---------------------------------------------------------------

    @Test
    public void loadPublishesContentWithCalculatedDistances() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());

        viewModel.load();
        NearbyUiState state = observe(viewModel);

        assertEquals(NearbyUiState.Status.CONTENT, state.getStatus());
        assertTrue(state.getItems().size() >= 3);

        AttractionListItem nearest = state.getItems().get(0);
        assertEquals("system-garden", nearest.getId());
        assertTrue("distance should be a plausible walking distance",
                nearest.getDistanceMeters() > 0.0 && nearest.getDistanceMeters() < 500.0);
    }

    @Test
    public void itemsStayOrderedNearestFirst() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());

        viewModel.load();
        List<AttractionListItem> items = observe(viewModel).getItems();

        for (int i = 1; i < items.size(); i++) {
            assertTrue("item " + i + " is closer than its predecessor",
                    items.get(i).getDistanceMeters()
                            >= items.get(i - 1).getDistanceMeters());
        }
    }

    @Test
    public void noResultsInRangeProduceTheEmptyState() {
        AttractionRepository repository = new InMemoryAttractionRepository(
                Collections.singletonList(attraction("far", -37.90000, 145.10000)));
        NearbyAttractionsViewModel viewModel = viewModelFor(repository, 100.0);

        viewModel.load();

        assertEquals(NearbyUiState.Status.EMPTY, observe(viewModel).getStatus());
    }

    @Test
    public void aRepositoryFailureProducesTheErrorState() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new FailingRepository());

        viewModel.load();

        assertEquals(NearbyUiState.Status.ERROR, observe(viewModel).getStatus());
    }

    @Test
    public void loadIfNeededDoesNotRefetchOnceAStateExists() {
        CountingRepository repository = new CountingRepository();
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.loadIfNeeded();
        viewModel.loadIfNeeded();

        assertEquals(1, repository.nearbyCallCount);
    }

    @Test
    public void retryAfterAFailureCanSucceed() {
        SwitchableRepository repository = new SwitchableRepository();
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.load();
        assertEquals(NearbyUiState.Status.ERROR, observe(viewModel).getStatus());

        repository.failing = false;
        viewModel.load();
        assertEquals(NearbyUiState.Status.CONTENT, observe(viewModel).getStatus());
    }

    // --- origin labelling ----------------------------------------------------------------------

    @Test
    public void beforeAnyFixDistancesAreLabelledAsTheReferenceArea() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());

        viewModel.load();
        NearbyUiState state = observe(viewModel);

        assertEquals(NearbyUiState.OriginLabel.REFERENCE_AREA, state.getOriginLabel());
        assertTrue(state.isReferenceArea());
    }

    @Test
    public void preciseAccessLabelsDistancesAsLive() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();

        viewModel.onLocationAccessChanged(LocationAccess.PRECISE);
        viewModel.onSample(sampleAt(ORIGIN, 10.0f, START_MONOTONIC));

        assertEquals(NearbyUiState.OriginLabel.LIVE_PRECISE, observe(viewModel).getOriginLabel());
    }

    @Test
    public void approximateAccessIsLabelledSeparatelyFromPrecise() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();

        viewModel.onLocationAccessChanged(LocationAccess.APPROXIMATE);
        viewModel.onSample(sampleAt(ORIGIN, 25.0f, START_MONOTONIC));

        assertEquals(NearbyUiState.OriginLabel.LIVE_APPROXIMATE,
                observe(viewModel).getOriginLabel());
    }

    @Test
    public void endingTheSessionReturnsToReferenceAreaBrowsing() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.onLocationAccessRequested(LocationAccess.PRECISE);
        viewModel.onSessionChanged(sessionIn(SessionState.ACTIVE, null));
        viewModel.onSample(sampleAt(ORIGIN, 10.0f, START_MONOTONIC));

        viewModel.onSessionChanged(sessionIn(SessionState.COMPLETED, null));
        NearbyUiState state = observe(viewModel);

        assertEquals(NearbyUiState.OriginLabel.REFERENCE_AREA, state.getOriginLabel());
        assertEquals(NearbyUiState.Status.CONTENT, state.getStatus());
        assertEquals(NearbyUiState.SessionPhase.IDLE, state.getSessionPhase());
    }

    @Test
    public void aPausedSessionStopsClaimingACurrentPosition() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.onLocationAccessRequested(LocationAccess.PRECISE);
        viewModel.onSessionChanged(sessionIn(SessionState.ACTIVE, null));
        viewModel.onSample(sampleAt(ORIGIN, 10.0f, START_MONOTONIC));
        assertEquals(NearbyUiState.OriginLabel.LIVE_PRECISE, observe(viewModel).getOriginLabel());

        viewModel.onSessionChanged(sessionIn(SessionState.PAUSED, PauseReason.APP_NOT_VISIBLE));
        NearbyUiState state = observe(viewModel);

        // This is the defect the session work exists to fix: a screen that kept saying "your
        // current location" after collection had already stopped.
        assertEquals(NearbyUiState.OriginLabel.LIVE_PAUSED, state.getOriginLabel());
        assertEquals(NearbyUiState.SessionPhase.PAUSED_BACKGROUND, state.getSessionPhase());
        assertTrue("the list the user was reading must survive the pause",
                state.getItems().size() > 0);
    }

    @Test
    public void losingAccessAndLeavingTheAppArePausedForDifferentReasons() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();
        viewModel.onSessionChanged(sessionIn(SessionState.ACTIVE, null));

        viewModel.onSessionChanged(sessionIn(SessionState.PAUSED, PauseReason.PERMISSION_LOST));
        assertEquals(NearbyUiState.SessionPhase.PAUSED_ACCESS_LOST,
                observe(viewModel).getSessionPhase());

        viewModel.onSessionChanged(sessionIn(SessionState.ACTIVE, null));
        viewModel.onSessionChanged(sessionIn(SessionState.PAUSED, PauseReason.APP_NOT_VISIBLE));
        assertEquals(NearbyUiState.SessionPhase.PAUSED_BACKGROUND,
                observe(viewModel).getSessionPhase());
    }

    @Test
    public void aStartInFlightIsReportedSoTheControlCanRefuseASecondPress() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();

        viewModel.onSessionChanged(sessionIn(SessionState.STARTING, null));

        assertTrue(observe(viewModel).getSessionPhase().isBusy());
    }

    // --- moving origin -------------------------------------------------------------------------

    @Test
    public void movingWithinTheRequeryThresholdReordersWithoutAnotherQuery() {
        // "near" is 100 m north of the origin, "far" is 240 m north.
        CountingRepository repository = new CountingRepository(Arrays.asList(
                attraction("near", northOfOrigin(100.0).getLatitude(), ORIGIN.getLongitude()),
                attraction("far", northOfOrigin(240.0).getLatitude(), ORIGIN.getLongitude())));
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.onSample(sampleAt(ORIGIN, 10.0f, START_MONOTONIC));
        assertEquals(Arrays.asList("near", "far"), idsOf(observe(viewModel)));
        assertEquals(1, repository.nearbyCallCount);

        // Walk 200 m north: below the 250 m threshold, so no new query, but "far" is now closer.
        long later = START_MONOTONIC + 60_000L;
        viewModel.onSample(sampleAt(northOfOrigin(200.0), 10.0f, later));

        assertEquals(Arrays.asList("far", "near"), idsOf(observe(viewModel)));
        assertEquals("distances are recomputed locally, not refetched",
                1, repository.nearbyCallCount);
    }

    @Test
    public void movingBeyondTheRequeryThresholdAsksTheRepositoryAgain() {
        CountingRepository repository = new CountingRepository();
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.onSample(sampleAt(ORIGIN, 10.0f, START_MONOTONIC));
        assertEquals(1, repository.nearbyCallCount);

        long later = START_MONOTONIC + 120_000L;
        viewModel.onSample(sampleAt(northOfOrigin(400.0), 10.0f, later));

        assertEquals(2, repository.nearbyCallCount);
    }

    @Test
    public void aLateResponseCannotRestoreAnOlderList() {
        DeferredRepository repository = new DeferredRepository();
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.load();
        viewModel.load();

        repository.complete(1, Collections.singletonList(
                attraction("current", northOfOrigin(50.0).getLatitude(), ORIGIN.getLongitude())));
        assertEquals(Collections.singletonList("current"), idsOf(observe(viewModel)));

        // The first, slower request finally answers with the list the user has already moved past.
        repository.complete(0, Collections.singletonList(
                attraction("stale", northOfOrigin(80.0).getLatitude(), ORIGIN.getLongitude())));

        assertEquals(Collections.singletonList("current"), idsOf(observe(viewModel)));
    }

    @Test
    public void aLateFailureCannotOverwriteACurrentList() {
        DeferredRepository repository = new DeferredRepository();
        NearbyAttractionsViewModel viewModel = viewModelFor(repository);

        viewModel.load();
        viewModel.load();
        repository.complete(1, Collections.singletonList(
                attraction("current", northOfOrigin(50.0).getLatitude(), ORIGIN.getLongitude())));

        repository.fail(0, new IOException("slow request finally timed out"));

        assertEquals(NearbyUiState.Status.CONTENT, observe(viewModel).getStatus());
    }

    // --- degraded access -----------------------------------------------------------------------

    @Test
    public void permissionNotYetRequestedAsksForItWithoutClearingTheList() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();
        int itemCount = observe(viewModel).getItems().size();

        viewModel.onExplanationRequired();
        NearbyUiState state = observe(viewModel);

        assertEquals(NearbyUiState.Status.PERMISSION_REQUIRED, state.getStatus());
        assertEquals("browsing the reference area must survive a permission prompt",
                itemCount, state.getItems().size());
    }

    @Test
    public void aPermanentDenialIsDistinguishedFromARecoverableOne() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());

        viewModel.onLocationAccessRequested(LocationAccess.DENIED);
        assertEquals(NearbyUiState.Status.PERMISSION_DENIED, observe(viewModel).getStatus());

        viewModel.onLocationAccessRequested(LocationAccess.PERMANENTLY_DENIED);
        assertEquals(NearbyUiState.Status.PERMISSION_PERMANENTLY_DENIED,
                observe(viewModel).getStatus());
    }

    @Test
    public void registeredButFixlessUpdatesShowWaitingRatherThanAPosition() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.onLocationAccessRequested(LocationAccess.PRECISE);

        viewModel.onLocationUnavailable(LocationSource.Reason.NO_RECENT_FIX);

        assertEquals(NearbyUiState.Status.WAITING_FOR_FIX, observe(viewModel).getStatus());
    }

    @Test
    public void aRoutineRecheckWithoutASessionDoesNotNagAboutADenial() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();

        // The screen became visible again; nothing was asked for and no session is running.
        viewModel.onLocationAccessChanged(LocationAccess.DENIED);

        assertEquals("re-showing the denial on every launch would be nagging, not informing",
                NearbyUiState.Status.CONTENT, observe(viewModel).getStatus());
    }

    @Test
    public void losingAccessDuringASessionIsAlwaysReported() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();
        viewModel.onSessionChanged(sessionIn(SessionState.ACTIVE, null));

        viewModel.onLocationAccessChanged(LocationAccess.DENIED);

        assertEquals(NearbyUiState.Status.PERMISSION_DENIED, observe(viewModel).getStatus());
    }

    @Test
    public void aDisabledProviderIsReportedAsUnavailableLocation() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();

        viewModel.onLocationUnavailable(LocationSource.Reason.PROVIDER_DISABLED);

        assertEquals(NearbyUiState.Status.LOCATION_UNAVAILABLE, observe(viewModel).getStatus());
    }

    @Test
    public void losingLocationRetainsTheAttractionsAlreadyOnScreen() {
        NearbyAttractionsViewModel viewModel = viewModelFor(new InMemoryAttractionRepository());
        viewModel.load();
        int itemCount = observe(viewModel).getItems().size();
        assertTrue("precondition: the list has content", itemCount > 0);

        viewModel.onLocationUnavailable(LocationSource.Reason.PROVIDER_DISABLED);

        assertEquals(itemCount, observe(viewModel).getItems().size());
    }

    // --- fakes ---------------------------------------------------------------------------------

    private static class FailingRepository implements AttractionRepository {
        @Override
        public void loadNearbyAttractions(GeoPoint origin, double radiusMeters,
                                          RepositoryCallback<List<Attraction>> callback) {
            callback.onError(new IOException("offline"));
        }

        @Override
        public void loadAttraction(String attractionId,
                                   RepositoryCallback<Attraction> callback) {
            callback.onError(new IOException("offline"));
        }
    }

    private static class CountingRepository implements AttractionRepository {
        int nearbyCallCount;
        private final List<Attraction> results;

        CountingRepository() {
            this(Collections.singletonList(attraction("close", -37.79640, 144.96150)));
        }

        CountingRepository(List<Attraction> results) {
            this.results = results;
        }

        @Override
        public void loadNearbyAttractions(GeoPoint origin, double radiusMeters,
                                          RepositoryCallback<List<Attraction>> callback) {
            nearbyCallCount++;
            callback.onSuccess(results);
        }

        @Override
        public void loadAttraction(String attractionId,
                                   RepositoryCallback<Attraction> callback) {
            callback.onError(new UnsupportedOperationException());
        }
    }

    private static class SwitchableRepository implements AttractionRepository {
        boolean failing = true;

        @Override
        public void loadNearbyAttractions(GeoPoint origin, double radiusMeters,
                                          RepositoryCallback<List<Attraction>> callback) {
            if (failing) {
                callback.onError(new IOException("offline"));
            } else {
                callback.onSuccess(Collections.singletonList(
                        attraction("close", -37.79640, 144.96150)));
            }
        }

        @Override
        public void loadAttraction(String attractionId,
                                   RepositoryCallback<Attraction> callback) {
            callback.onError(new UnsupportedOperationException());
        }
    }

    /** Holds every callback so a test can answer requests out of order. */
    private static class DeferredRepository implements AttractionRepository {
        private final List<RepositoryCallback<List<Attraction>>> pending = new ArrayList<>();

        @Override
        public void loadNearbyAttractions(GeoPoint origin, double radiusMeters,
                                          RepositoryCallback<List<Attraction>> callback) {
            pending.add(callback);
        }

        @Override
        public void loadAttraction(String attractionId,
                                   RepositoryCallback<Attraction> callback) {
            callback.onError(new UnsupportedOperationException());
        }

        void complete(int index, List<Attraction> results) {
            pending.get(index).onSuccess(results);
        }

        void fail(int index, Throwable error) {
            pending.get(index).onError(error);
        }
    }
}