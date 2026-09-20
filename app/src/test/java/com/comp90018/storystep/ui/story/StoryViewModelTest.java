package com.comp90018.storystep.ui.story;

import static org.junit.Assert.*;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.comp90018.storystep.application.TestCoordinators;
import com.comp90018.storystep.data.repository.InMemoryAttractionRepository;
import com.comp90018.storystep.data.repository.InMemorySessionStore;
import com.comp90018.storystep.domain.model.*;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.session.FakeClock;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;

/** Provenance must describe the content actually loaded, including preserved arrival text. */
public class StoryViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();
    private final InMemorySessionStore store = new InMemorySessionStore(FakeClock.startingAt(1000));

    @Test public void cachedStoryUsesItsActualSourceRatherThanAlwaysClaimingSeedData() {
        for (AttractionProvenance provenance : AttractionProvenance.values()) {
            Attraction place = new Attraction("place", "Place", AttractionCategory.PARK, "Current text",
                    IndoorClassification.OUTDOOR, new GeoPoint(0, 0), provenance, null);
            StoryViewModel model = new StoryViewModel(new InMemoryAttractionRepository(Collections.singletonList(place)),
                    store, TestCoordinators.idle());
            model.load("place");
            assertEquals(provenance, model.getUiState().getValue().getProvenance());
            assertEquals("Current text", model.getUiState().getValue().getStory());
            assertFalse(model.getUiState().getValue().isArchived());
        }
    }

    @Test public void arrivalSnapshotSurvivesAbsentCacheWithoutInventingHistoricalMetadata() {
        store.create(success());
        store.recordVisit(new Visit("session-1", "place", 1000, "Old name", "Saved text", true), success());
        StoryViewModel model = new StoryViewModel(new InMemoryAttractionRepository(Collections.emptyList()),
                store, TestCoordinators.idle());
        model.load("place", "session-1");
        assertTrue(model.getUiState().getValue().isArchived());
        assertEquals("Saved text", model.getUiState().getValue().getStory());
        assertNull(model.getUiState().getValue().getProvenance());
    }

    @Test public void archivedOsmPlaceKeepsProviderCreditAndSnapshotLabel() {
        store.create(success());
        store.recordVisit(new Visit("session-1", "osm:node/1", 1000, "Place", "Saved text", true), success());
        StoryViewModel model = new StoryViewModel(new InMemoryAttractionRepository(Collections.emptyList()),
                store, TestCoordinators.idle());
        model.load("osm:node/1", "session-1");
        assertTrue(model.getUiState().getValue().isArchived());
        assertEquals(AttractionProvenance.OPENSTREETMAP, model.getUiState().getValue().getProvenance());
    }

    private static <T> RepositoryCallback<T> success() {
        return new RepositoryCallback<T>() {
            public void onSuccess(T value) { }
            public void onError(Throwable error) { throw new AssertionError(error); }
        };
    }
}
