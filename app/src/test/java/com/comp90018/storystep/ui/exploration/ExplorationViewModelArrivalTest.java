package com.comp90018.storystep.ui.exploration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.SensingSources;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * How the dashboard offers a revealed story, and when it stops offering it.
 *
 * <p>The prompt is one-shot in the sense that matters to a user: it appears once per arrival, and
 * re-observing the state — which is what a rotation does — must not bring it back. The story
 * itself stays recoverable, because that is a fact about the visit rather than about the screen.
 */
public class ExplorationViewModelArrivalTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final double METERS_PER_DEGREE_LATITUDE = 111_195.0;

    private static final GeoPoint TARGET_POSITION = new GeoPoint(-37.79810, 144.95910);

    private static final Attraction TARGET = new Attraction(
            "old-arts",
            "Old Arts Building",
            AttractionCategory.UNIVERSITY_LANDMARK,
            "The 1924 clocktower building.",
            false,
            TARGET_POSITION);

    private FakeClock clock;
    private FakeLocationSource locationSource;
    private InMemorySessionStore sessionStore;
    private ExplorationCoordinator coordinator;
    private ManualTicker ticker;
    private ExplorationViewModel viewModel;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        locationSource = new FakeLocationSource();
        sessionStore = new InMemorySessionStore(clock);
        coordinator = TestCoordinators.with(clock, locationSource, SensingSources.none(),
                sessionStore);
        ticker = new ManualTicker();
        viewModel = new ExplorationViewModel(coordinator, ticker);
        viewModel.getUiState().observeForever(state -> {
        });
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    @Test
    public void beforeAnyArrivalNoBannerIsShown() {
        coordinator.requestStart(TARGET);

        assertFalse(state().isArrivalBannerVisible());
        assertNull(state().getArrivedAttractionName());
    }

    @Test
    public void anArrivalShowsTheBannerNamingThePlace() {
        arrive();

        assertTrue(state().isArrivalBannerVisible());
        assertEquals("Old Arts Building", state().getArrivedAttractionName());
        assertEquals("old-arts", state().getArrivedAttractionId());
    }

    @Test
    public void openingTheStoryHidesTheBanner() {
        arrive();

        viewModel.onStoryRequested();

        assertFalse(state().isArrivalBannerVisible());
    }

    @Test
    public void dismissingTheBannerHidesItWithoutMarkingTheStoryRead() {
        arrive();

        viewModel.onArrivalDismissed();

        assertFalse(state().isArrivalBannerVisible());
        // "Not now" is not "never": the story is still there to be opened later.
        assertTrue(hasUnreadVisit());
    }

    @Test
    public void aConsumedBannerDoesNotComeBackWhenTheScreenIsRebuilt() {
        arrive();
        viewModel.onStoryRequested();

        // What a rotation does: a fresh ViewModel over the same, still-running session.
        ExplorationViewModel rebuilt = new ExplorationViewModel(coordinator, new ManualTicker());
        rebuilt.getUiState().observeForever(state -> {
        });

        assertFalse("a consumed arrival must not be announced twice",
                rebuilt.getUiState().getValue().isArrivalBannerVisible());
    }

    @Test
    public void aTickDoesNotResurrectAConsumedBanner() {
        arrive();
        viewModel.onStoryRequested();

        ticker.tick();

        assertFalse(state().isArrivalBannerVisible());
    }

    @Test
    public void stayingAtThePlaceDoesNotReopenAConsumedBanner() {
        arrive();
        viewModel.onArrivalDismissed();

        emit(8.0, 5.0f, 15_000L);
        emit(8.0, 5.0f, 20_000L);
        emit(8.0, 5.0f, 25_000L);

        assertFalse(state().isArrivalBannerVisible());
    }

    @Test
    public void theBannerAndTheFailureNoticeAreNeverShownTogether() {
        arrive();

        assertTrue(state().isArrivalBannerVisible());
        assertFalse(state().isVisitSaveFailed());
    }

    // --- helpers -------------------------------------------------------------------------------

    private ExplorationUiState state() {
        return viewModel.getUiState().getValue();
    }

    private void arrive() {
        coordinator.requestStart(TARGET);
        emit(8.0, 5.0f, 0L);
        emit(8.0, 5.0f, 5_000L);
        emit(8.0, 5.0f, 10_000L);
    }

    private boolean hasUnreadVisit() {
        final boolean[] found = new boolean[1];
        sessionStore.findUnreadVisit(coordinator.summary().getId(),
                new com.comp90018.storystep.domain.repository.RepositoryCallback<
                        com.comp90018.storystep.domain.model.Visit>() {
                    @Override
                    public void onSuccess(com.comp90018.storystep.domain.model.Visit visit) {
                        found[0] = visit != null;
                    }

                    @Override
                    public void onError(Throwable error) {
                    }
                });
        return found[0];
    }

    private void emit(double metresNorth, float accuracyMeters, long monotonicMillis) {
        clock.advance(monotonicMillis - (clock.monotonicMillis() - 1_000L));
        GeoPoint position = new GeoPoint(
                TARGET_POSITION.getLatitude() + metresNorth / METERS_PER_DEGREE_LATITUDE,
                TARGET_POSITION.getLongitude());
        locationSource.emit(new LocationSample(position, accuracyMeters,
                1_000L + monotonicMillis, 1_000L + monotonicMillis, LocationOrigin.DEVICE));
    }

    private static final class ManualTicker implements ExplorationViewModel.Ticker {

        private Runnable onTick;

        @Override
        public void start(Runnable runnable, long intervalMillis) {
            onTick = runnable;
        }

        @Override
        public void stop() {
        }

        void tick() {
            onTick.run();
        }
    }
}
