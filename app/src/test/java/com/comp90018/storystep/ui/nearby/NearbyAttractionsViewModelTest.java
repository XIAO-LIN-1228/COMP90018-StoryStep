package com.comp90018.storystep.ui.nearby;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.repository.AttractionRepository;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Verifies the Fragment -> ViewModel -> Repository path without any Android framework. */
public class NearbyAttractionsViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private static Attraction attraction(String id, double latitude, double longitude) {
        return new Attraction(id, "Name " + id, AttractionCategory.PARK, "Description",
                false, new GeoPoint(latitude, longitude));
    }

    private static NearbyUiState observe(NearbyAttractionsViewModel viewModel) {
        final List<NearbyUiState> seen = new ArrayList<>();
        viewModel.getUiState().observeForever(seen::add);
        return seen.isEmpty() ? null : seen.get(seen.size() - 1);
    }

    @Test
    public void loadPublishesContentWithCalculatedDistances() {
        NearbyAttractionsViewModel viewModel = new NearbyAttractionsViewModel(
                new InMemoryAttractionRepository(), ORIGIN, 3000.0);

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
        NearbyAttractionsViewModel viewModel = new NearbyAttractionsViewModel(
                new InMemoryAttractionRepository(), ORIGIN, 3000.0);

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
        NearbyAttractionsViewModel viewModel =
                new NearbyAttractionsViewModel(repository, ORIGIN, 100.0);

        viewModel.load();

        assertEquals(NearbyUiState.Status.EMPTY, observe(viewModel).getStatus());
    }

    @Test
    public void aRepositoryFailureProducesTheErrorState() {
        NearbyAttractionsViewModel viewModel =
                new NearbyAttractionsViewModel(new FailingRepository(), ORIGIN, 3000.0);

        viewModel.load();

        assertEquals(NearbyUiState.Status.ERROR, observe(viewModel).getStatus());
    }

    @Test
    public void loadIfNeededDoesNotRefetchOnceAStateExists() {
        CountingRepository repository = new CountingRepository();
        NearbyAttractionsViewModel viewModel =
                new NearbyAttractionsViewModel(repository, ORIGIN, 3000.0);

        viewModel.loadIfNeeded();
        viewModel.loadIfNeeded();

        assertEquals(1, repository.nearbyCallCount);
    }

    @Test
    public void retryAfterAFailureCanSucceed() {
        SwitchableRepository repository = new SwitchableRepository();
        NearbyAttractionsViewModel viewModel =
                new NearbyAttractionsViewModel(repository, ORIGIN, 3000.0);

        viewModel.load();
        assertEquals(NearbyUiState.Status.ERROR, observe(viewModel).getStatus());

        repository.failing = false;
        viewModel.load();
        assertEquals(NearbyUiState.Status.CONTENT, observe(viewModel).getStatus());
    }

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

        @Override
        public void loadNearbyAttractions(GeoPoint origin, double radiusMeters,
                                          RepositoryCallback<List<Attraction>> callback) {
            nearbyCallCount++;
            callback.onSuccess(Collections.singletonList(
                    attraction("close", -37.79640, 144.96150)));
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
}
