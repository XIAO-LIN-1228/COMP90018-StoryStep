package com.comp90018.storystep.ui.saved;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.data.local.FakeSavedAttractionDao;
import com.comp90018.storystep.data.repository.RoomSavedAttractionRepository;
import com.comp90018.storystep.data.repository.TestSavedAttractions;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import org.junit.Rule;
import org.junit.Test;

/**
 * The saved list's states.
 *
 * <p>The distinction that matters is EMPTY against ERROR: "you have saved nothing" and "this did
 * not load" have to look different, or a storage failure reads as the user having no saved places.
 */
public class SavedViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private static final Attraction MUSEUM = new Attraction("museum", "Melbourne Museum",
            AttractionCategory.MUSEUM, "Natural history and culture.", true,
            new GeoPoint(-37.80320, 144.97170));

    @Test
    public void anEmptyStoreIsReportedAsEmptyRatherThanAsContent() {
        SavedViewModel viewModel = new SavedViewModel(TestSavedAttractions.empty(), ORIGIN);

        viewModel.load();

        assertEquals(SavedUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aStorageFailureIsReportedAsAnError() {
        FakeSavedAttractionDao dao = new FakeSavedAttractionDao();
        dao.failWrites = new IllegalStateException("unreadable");
        SavedViewModel viewModel = new SavedViewModel(TestSavedAttractions.over(dao), ORIGIN);

        viewModel.load();

        assertEquals(SavedUiState.Status.ERROR, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void savedPlacesAreListedWithTheirDistanceAndBookmark() {
        RoomSavedAttractionRepository saved = TestSavedAttractions.empty();
        save(saved, MUSEUM);
        SavedViewModel viewModel = new SavedViewModel(saved, ORIGIN);

        viewModel.load();

        SavedUiState state = viewModel.getUiState().getValue();
        assertEquals(SavedUiState.Status.CONTENT, state.getStatus());
        assertEquals(1, state.getItems().size());
        assertEquals("museum", state.getItems().get(0).getId());
        assertTrue("every row on this screen is saved by definition",
                state.getItems().get(0).isSaved());
        assertTrue("distance is measured from the reference area",
                state.getItems().get(0).getDistanceMeters() > 0.0);
    }

    @Test
    public void removingTheLastPlaceLeavesTheEmptyState() {
        RoomSavedAttractionRepository saved = TestSavedAttractions.empty();
        save(saved, MUSEUM);
        SavedViewModel viewModel = new SavedViewModel(saved, ORIGIN);
        viewModel.load();

        viewModel.onSaveToggleRequested(viewModel.getUiState().getValue().getItems().get(0));

        assertEquals(SavedUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());
    }

    @Test
    public void aFailedRemovalIsReportedAndTheRowStays() {
        FakeSavedAttractionDao dao = new FakeSavedAttractionDao();
        RoomSavedAttractionRepository saved = TestSavedAttractions.over(dao);
        save(saved, MUSEUM);
        SavedViewModel viewModel = new SavedViewModel(saved, ORIGIN);
        viewModel.load();
        dao.failWrites = new IllegalStateException("disk full");

        viewModel.onSaveToggleRequested(viewModel.getUiState().getValue().getItems().get(0));

        assertEquals(SavedUiState.Status.CONTENT, viewModel.getUiState().getValue().getStatus());
        assertTrue(Boolean.TRUE.equals(viewModel.getSaveFailed().getValue()));

        viewModel.consumeSaveFailure();
        assertFalse(Boolean.TRUE.equals(viewModel.getSaveFailed().getValue()));
    }

    @Test
    public void aSaveMadeOnAnotherScreenAppearsInTheList() {
        RoomSavedAttractionRepository saved = TestSavedAttractions.empty();
        SavedViewModel viewModel = new SavedViewModel(saved, ORIGIN);
        viewModel.load();
        assertEquals(SavedUiState.Status.EMPTY, viewModel.getUiState().getValue().getStatus());

        // Stands in for the detail screen saving a place while this list is on the stack.
        save(saved, MUSEUM);

        SavedUiState state = viewModel.getUiState().getValue();
        assertEquals(SavedUiState.Status.CONTENT, state.getStatus());
        assertEquals("museum", state.getItems().get(0).getId());
    }

    private static void save(RoomSavedAttractionRepository repository, Attraction attraction) {
        repository.save(attraction, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
    }
}
