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
import com.comp90018.storystep.domain.model.SessionState;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import com.comp90018.storystep.platform.location.FakeLocationSource;
import com.comp90018.storystep.platform.location.LocationAccess;

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
        assertEquals(true, state.getAttraction().isIndoor());
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
