package com.comp90018.storystep.ui.exploration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.SensingSources;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.LocationOrigin;
import com.comp90018.storystep.domain.model.LocationSample;
import com.comp90018.storystep.domain.model.MotionSample;
import com.comp90018.storystep.domain.model.MovementState;
import com.comp90018.storystep.domain.model.PauseReason;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.platform.location.LocationSource;
import com.comp90018.storystep.platform.sensor.FakeHeadingSource;
import com.comp90018.storystep.platform.sensor.FakeMotionSource;
import com.comp90018.storystep.platform.sensor.FixedDeclinationSource;
import com.comp90018.storystep.platform.sensor.HeadingAccuracy;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers the dashboard's contract with the user: never invent a reading that is not available, and
 * never offer a control that would act twice on the same session.
 */
public class ExplorationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private FakeClock clock;
    private FakeLocationSource locationSource;
    private ExplorationCoordinator coordinator;
    private ManualTicker ticker;
    private ExplorationViewModel viewModel;

    @Before
    public void setUp() {
        clock = FakeClock.startingAt(1_000L);
        locationSource = new FakeLocationSource();
        coordinator = TestCoordinators.with(clock, locationSource);
        ticker = new ManualTicker();
        viewModel = new ExplorationViewModel(coordinator, ticker);
        viewModel.getUiState().observeForever(state -> {
        });
    }

    private ExplorationUiState state() {
        return viewModel.getUiState().getValue();
    }

    // --- unavailable readings ------------------------------------------------------------------

    @Test
    public void beforeAnyFixTheDistancesAreUnavailableRatherThanZero() {
        startTowardsMuseum();

        assertFalse("0 m remaining would tell the user they had arrived",
                state().hasRemainingDistance());
        assertFalse(state().hasAccuracy());
        assertEquals(0.0, state().getTravelledDistanceMeters(), 0.0);
    }

    @Test
    public void withoutADestinationTheRemainingDistanceStaysUnavailable() {
        becomeUsable();
        coordinator.requestStart();

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertFalse(state().hasRemainingDistance());
        assertNull(state().getDestinationName());
        assertTrue("a fix without a destination is still a usable accuracy reading",
                state().hasAccuracy());
    }

    @Test
    public void aFixProducesTheRemainingDistanceToTheDestination() {
        startTowardsMuseum();

        locationSource.emit(sampleAt(ORIGIN, 8.0f, 1_000L));

        assertTrue(state().hasRemainingDistance());
        assertTrue(state().getRemainingDistanceMeters() > 0.0);
        assertEquals(8.0f, state().getAccuracyMeters(), 0.001f);
        assertEquals("Melbourne Museum", state().getDestinationName());
    }

    @Test
    public void anUnavailableReasonIsReportedWhileNoFixHasArrived() {
        startTowardsMuseum();

        locationSource.emitUnavailable(LocationSource.Reason.NO_RECENT_FIX);

        assertEquals(LocationSource.Reason.NO_RECENT_FIX, state().getUnavailableReason());
    }

    @Test
    public void anAcceptedFixClearsAPreviousUnavailableReason() {
        startTowardsMuseum();
        locationSource.emitUnavailable(LocationSource.Reason.NO_RECENT_FIX);

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertNull(state().getUnavailableReason());
    }

    // --- duration ------------------------------------------------------------------------------

    @Test
    public void theTickRedrawsTheGrowingDuration() {
        startTowardsMuseum();
        assertEquals(0L, state().getActiveDurationMillis());

        clock.advance(30_000L);
        ticker.tick();

        assertEquals(30_000L, state().getActiveDurationMillis());
    }

    @Test
    public void aPausedSessionStopsAccumulatingTime() {
        startTowardsMuseum();
        clock.advance(30_000L);
        coordinator.onVisibilityChanged(false);

        clock.advance(600_000L);
        ticker.tick();

        assertEquals("ten minutes in the background is not ten minutes of exploring",
                30_000L, state().getActiveDurationMillis());
        assertEquals(SessionState.PAUSED, state().getState());
        assertEquals(PauseReason.APP_NOT_VISIBLE, state().getPauseReason());
    }

    // --- controls ------------------------------------------------------------------------------

    @Test
    public void resumeIsOfferedOnlyWhilePaused() {
        startTowardsMuseum();
        assertFalse(state().isResumeVisible());

        coordinator.onVisibilityChanged(false);

        assertTrue(state().isResumeVisible());
    }

    @Test
    public void stopStaysAvailableWhileTheSessionIsPaused() {
        startTowardsMuseum();
        coordinator.onVisibilityChanged(false);

        assertTrue("a user must always be able to end collection", state().isStopEnabled());
    }

    @Test
    public void stopIsDisabledOnceTheSessionIsSaved() {
        startTowardsMuseum();

        viewModel.onStopRequested();

        assertTrue(state().isFinished());
        assertFalse(state().isStopEnabled());
    }

    @Test
    public void discardingLeavesNoSessionAndNoRoute() {
        startTowardsMuseum();
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        viewModel.onDiscardRequested();

        assertTrue(state().isIdle());
        assertEquals(0.0, state().getTravelledDistanceMeters(), 0.0);
    }

    @Test
    public void resumingAPausedSessionCollectsAgain() {
        startTowardsMuseum();
        coordinator.onVisibilityChanged(false);
        coordinator.onVisibilityChanged(true);

        viewModel.onResumeRequested();

        assertEquals(SessionState.ACTIVE, state().getState());
        assertTrue(state().isCollecting());
    }

    @Test
    public void theTickerIsReleasedWithTheViewModel() {
        viewModel.onCleared();

        assertFalse(ticker.running);
    }

    // --- sensing ------------------------------------------------------------------------------

    @Test
    public void withoutAnAccelerometerMovementReadsAsUnavailable() {
        startTowardsMuseum();

        assertEquals("this fixture has no sensing at all, which is a supported device",
                MovementState.UNAVAILABLE, state().getMovementState());
    }

    @Test
    public void aWalkingUserIsReportedAsMoving() {
        FakeMotionSource motionSource = new FakeMotionSource();
        useCoordinator(new SensingSources(motionSource, null, null));
        startTowardsMuseum();

        for (int i = 0; i < 200; i++) {
            // A 2 Hz stride at about 3 m/s squared, 50 ms apart: ten seconds of walking.
            double seconds = i * 0.05;
            float z = (float) (9.81 + 3.0 * Math.sin(2.0 * Math.PI * 2.0 * seconds));
            motionSource.emit(new MotionSample(0.0f, 0.0f, z, (long) (seconds * 1000)));
        }

        assertEquals(MovementState.MOVING, state().getMovementState());
        assertTrue(state().getMovingMillis() > 0L);
    }

    @Test
    public void withoutACompassTheDirectionIsGivenWithoutAnArrow() {
        startTowardsMuseum();

        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        assertFalse("no compass means no arrow to rotate", state().hasDirectionArrow());
        assertTrue("but the user can still be told which way the place lies",
                state().hasCardinal());
        assertTrue("and the distance is unaffected by the missing sensor",
                state().hasRemainingDistance());
    }

    @Test
    public void aUsableHeadingProducesAnArrow() {
        FakeHeadingSource headingSource = new FakeHeadingSource();
        useCoordinator(new SensingSources(null, headingSource, FixedDeclinationSource.melbourne()));
        startTowardsMuseum();
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));

        headingSource.emit(0.0f);

        assertTrue(state().hasDirectionArrow());
    }

    @Test
    public void anUnreliableHeadingHidesTheArrowButKeepsTheDirection() {
        FakeHeadingSource headingSource = new FakeHeadingSource();
        useCoordinator(new SensingSources(null, headingSource, FixedDeclinationSource.melbourne()));
        startTowardsMuseum();
        locationSource.emit(sampleAt(ORIGIN, 5.0f, 1_000L));
        headingSource.emit(0.0f);

        headingSource.emit(0.0f, HeadingAccuracy.UNRELIABLE);

        assertFalse(state().hasDirectionArrow());
        assertTrue(state().hasCardinal());
    }

    @Test
    public void withNoFixThereIsNoDirectionAtAll() {
        FakeHeadingSource headingSource = new FakeHeadingSource();
        useCoordinator(new SensingSources(null, headingSource, FixedDeclinationSource.melbourne()));
        startTowardsMuseum();

        headingSource.emit(0.0f);

        assertFalse("a heading with nowhere to stand says nothing about where to go",
                state().hasCardinal());
        assertFalse(state().hasDirectionArrow());
    }

    // --- helpers -------------------------------------------------------------------------------

    /**
     * Rebuilds the coordinator and the ViewModel with a different sensing configuration.
     *
     * <p>Called before the session starts, so each test describes one device rather than a device
     * that grew a compass halfway through a walk.
     */
    private void useCoordinator(SensingSources sensing) {
        viewModel.onCleared();
        coordinator = TestCoordinators.with(clock, locationSource, sensing);
        ticker = new ManualTicker();
        viewModel = new ExplorationViewModel(coordinator, ticker);
        viewModel.getUiState().observeForever(state -> {
        });
    }

    private void becomeUsable() {
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
    }

    private void startTowardsMuseum() {
        becomeUsable();
        coordinator.requestStart(museum());
    }

    private static Attraction museum() {
        return new Attraction("melbourne-museum", "Melbourne Museum",
                AttractionCategory.MUSEUM, "A museum.", true,
                new GeoPoint(-37.80330, 144.97160));
    }

    private static LocationSample sampleAt(GeoPoint position,
                                           float accuracyMeters,
                                           long monotonicMillis) {
        return new LocationSample(position, accuracyMeters, 1_700_000_000_000L, monotonicMillis,
                LocationOrigin.DEVICE);
    }

    /** A ticker the test fires by hand, so a second of elapsed time costs no real time. */
    private static final class ManualTicker implements ExplorationViewModel.Ticker {

        private Runnable onTick;
        boolean running;

        @Override
        public void start(Runnable runnable, long intervalMillis) {
            onTick = runnable;
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        void tick() {
            onTick.run();
        }
    }
}
