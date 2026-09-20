package com.comp90018.storystep.ui.detail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.application.ExplorationCoordinator;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.DeferredSavedAttractions;
import com.comp90018.storystep.data.repository.RoomSavedAttractionRepository;
import com.comp90018.storystep.data.repository.TestSavedAttractions;
import com.comp90018.storystep.data.local.FakeSavedAttractionDao;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Detail screen state, exercised straight through the repository interface. */
public class AttractionDetailViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static AttractionDetailUiState observe(AttractionDetailViewModel viewModel) {
        final List<AttractionDetailUiState> seen = new ArrayList<>();
        viewModel.getUiState().observeForever(seen::add);
        return seen.isEmpty() ? null : seen.get(seen.size() - 1);
    }

    @Test
    public void loadsAKnownAttraction() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), TestCoordinators.idle());

        viewModel.load("melbourne-museum");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.CONTENT, state.getStatus());
        assertNotNull(state.getAttraction());
        assertEquals("Melbourne Museum", state.getAttraction().getName());
        assertEquals(IndoorClassification.INDOOR, state.getAttraction().getIndoor());
    }

    @Test
    public void anUnknownIdentifierProducesTheErrorState() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), TestCoordinators.idle());

        viewModel.load("does-not-exist");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.ERROR, state.getStatus());
        assertNull(state.getAttraction());
    }

    @Test
    public void aMissingArgumentProducesTheErrorStateInsteadOfCrashing() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), TestCoordinators.idle());

        viewModel.load(null);

        assertEquals(AttractionDetailUiState.Status.ERROR, observe(viewModel).getStatus());
    }

    // --- falling back to the user's saved copy -------------------------------------------------

    /**
     * Saves {@code attraction} into {@code store} and returns it. The attraction is deliberately
     * unknown to {@link InMemoryAttractionRepository}, which is what a place evicted from the
     * cache looks like to the detail screen.
     */
    private static Attraction saveOnly(RoomSavedAttractionRepository store, Attraction attraction) {
        store.save(attraction, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean written) {
                // Direct executors: already written by the time save returns.
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        return attraction;
    }

    private static Attraction evictedPlace() {
        return Attraction.seeded("evicted-place", "Evicted Place", AttractionCategory.PARK,
                "A place the cache no longer holds.", IndoorClassification.OUTDOOR,
                new GeoPoint(-37.8, 144.96));
    }

    @Test
    public void aSavedPlaceMissingFromTheCacheIsReadBackFromTheSavedCopy() {
        RoomSavedAttractionRepository store = TestSavedAttractions.empty();
        Attraction saved = saveOnly(store, evictedPlace());
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(), store,
                        TestCoordinators.idle());

        viewModel.load(saved.getId());
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.CONTENT, state.getStatus());
        assertNotNull(state.getAttraction());
        assertEquals("Evicted Place", state.getAttraction().getName());
        assertTrue("the screen must say the content is the user's saved copy",
                state.isFromSavedSnapshot());
        assertTrue(state.isSaved());
    }

    @Test
    public void anUnsavedPlaceMissingFromTheCacheStillFails() {
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), TestCoordinators.idle());

        viewModel.load("evicted-place");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.ERROR, state.getStatus());
        assertFalse(state.isFromSavedSnapshot());
    }

    @Test
    public void aFailingSavedLookupLeavesTheErrorStateRatherThanBlankContent() {
        FakeSavedAttractionDao dao = new FakeSavedAttractionDao();
        RoomSavedAttractionRepository store = TestSavedAttractions.over(dao);
        saveOnly(store, evictedPlace());
        dao.failSingleReads = new IllegalStateException("database unavailable");
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(), store,
                        TestCoordinators.idle());

        viewModel.load("evicted-place");
        AttractionDetailUiState state = observe(viewModel);

        assertEquals(AttractionDetailUiState.Status.ERROR, state.getStatus());
        assertNull(state.getAttraction());
    }

    @Test
    public void unsavingWhileReadingTheSavedCopyClearsTheBookmarkButKeepsTheContent() {
        RoomSavedAttractionRepository store = TestSavedAttractions.empty();
        saveOnly(store, evictedPlace());
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(), store,
                        TestCoordinators.idle());
        viewModel.load("evicted-place");
        assertTrue(observe(viewModel).isSaved());

        viewModel.onSaveToggleRequested();
        AttractionDetailUiState state = observe(viewModel);

        assertFalse(state.isSaved());
        assertEquals(AttractionDetailUiState.Status.CONTENT, state.getStatus());
        assertNotNull("the copy already on screen should not vanish under the user",
                state.getAttraction());
    }

    // --- starting exploration from the detail screen -------------------------------------------

    @Test
    public void startingWithoutLocationAccessRemembersTheDestinationButOpensNoSession() {
        ExplorationCoordinator coordinator =
                TestCoordinators.with(FakeClock.startingAt(1_000L), new FakeLocationSource());
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.DENIED);
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), coordinator);
        viewModel.load("melbourne-museum");

        assertFalse("the dashboard must not open for a session that never started",
                viewModel.onStartExplorationRequested());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
        assertEquals("the choice survives the trip through the permission flow",
                "melbourne-museum", coordinator.getDestination().getId());
    }

    @Test
    public void startingWithAccessSelectsTheDestinationAndOpensASession() {
        ExplorationCoordinator coordinator =
                TestCoordinators.with(FakeClock.startingAt(1_000L), new FakeLocationSource());
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), coordinator);
        viewModel.load("melbourne-museum");

        assertTrue(viewModel.onStartExplorationRequested());
        assertEquals(SessionState.ACTIVE, coordinator.summary().getState());
        assertEquals("melbourne-museum", coordinator.getDestination().getId());
    }

    @Test
    public void startingBeforeTheAttractionLoadedDoesNothing() {
        ExplorationCoordinator coordinator =
                TestCoordinators.with(FakeClock.startingAt(1_000L), new FakeLocationSource());
        coordinator.onVisibilityChanged(true);
        coordinator.onLocationAccessChanged(LocationAccess.PRECISE);
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.empty(), coordinator);

        assertFalse(viewModel.onStartExplorationRequested());
        assertEquals(SessionState.IDLE, coordinator.summary().getState());
        assertNull(coordinator.getDestination());
    }

    // --- saving ------------------------------------------------------------------------------

    @Test
    public void savingAndUnsavingFlipTheBookmarkAndTheStore() {
        FakeSavedAttractionDao dao = new FakeSavedAttractionDao();
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.over(dao), TestCoordinators.idle());
        viewModel.load("melbourne-museum");
        assertFalse(observe(viewModel).isSaved());

        viewModel.onSaveToggleRequested();

        assertTrue(observe(viewModel).isSaved());
        assertEquals(1, dao.count());

        viewModel.onSaveToggleRequested();

        assertFalse(observe(viewModel).isSaved());
        assertEquals(0, dao.count());
    }

    @Test
    public void aSaveByAnotherScreenUpdatesThisOne() {
        RoomSavedAttractionRepository saved = TestSavedAttractions.empty();
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(), saved,
                        TestCoordinators.idle());
        viewModel.load("melbourne-museum");

        // Stands in for the nearby list saving the same place while the detail screen is open.
        saved.save(observe(viewModel).getAttraction(), new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertTrue(observe(viewModel).isSaved());
    }

    @Test
    public void aSecondTapWhileTheWriteIsOutstandingIsIgnored() {
        DeferredSavedAttractions saved = new DeferredSavedAttractions();
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(), saved,
                        TestCoordinators.idle());
        viewModel.load("melbourne-museum");

        viewModel.onSaveToggleRequested();
        assertTrue("the screen must show the write is under way",
                observe(viewModel).isSaveInFlight());

        // The second tap arrives before the first write has answered. Without serialisation this
        // would issue an unsave for a record the first write has not created yet.
        viewModel.onSaveToggleRequested();

        assertEquals("only one write may be outstanding", 1, saved.pending.size());

        saved.completeNext();
        AttractionDetailUiState state = observe(viewModel);
        assertTrue("the one write that ran was the save", state.isSaved());
        assertFalse("the screen accepts input again once the write resolves",
                state.isSaveInFlight());
    }

    @Test
    public void aFailedSaveIsReportedAndLeavesTheBookmarkAlone() {
        FakeSavedAttractionDao dao = new FakeSavedAttractionDao();
        dao.failWrites = new IllegalStateException("disk full");
        AttractionDetailViewModel viewModel =
                new AttractionDetailViewModel(new InMemoryAttractionRepository(),
                        TestSavedAttractions.over(dao), TestCoordinators.idle());
        viewModel.load("melbourne-museum");

        viewModel.onSaveToggleRequested();

        AttractionDetailUiState state = observe(viewModel);
        assertFalse("a failed write must not look like a successful one", state.isSaved());
        assertFalse("the screen must accept another attempt", state.isSaveInFlight());
        assertTrue(Boolean.TRUE.equals(viewModel.getSaveFailed().getValue()));

        viewModel.consumeSaveFailure();
        assertFalse(Boolean.TRUE.equals(viewModel.getSaveFailed().getValue()));
    }
}
