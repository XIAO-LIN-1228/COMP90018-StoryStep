package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionResult;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.repository.AttractionNotFoundException;
import com.comp90018.storystep.domain.repository.RepositoryCallback;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Deterministic tests for the Stage 1 repository implementation. */
public class InMemoryAttractionRepositoryTest {

    private static final GeoPoint ORIGIN = new GeoPoint(-37.79630, 144.96140);

    private static Attraction attraction(String id, double latitude, double longitude) {
        return Attraction.seeded(id, "Name " + id, AttractionCategory.PARK, "Description",
                IndoorClassification.OUTDOOR, new GeoPoint(latitude, longitude));
    }

    private static final class Recorder<T> implements RepositoryCallback<T> {
        T result;
        Throwable error;

        @Override
        public void onSuccess(T result) {
            this.result = result;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }

    @Test
    public void nearby_returnsSampleDataSortedByDistance() {
        Recorder<AttractionResult> recorder = new Recorder<>();
        new InMemoryAttractionRepository()
                .loadNearbyAttractions(ORIGIN, 3000.0, recorder);

        assertNull(recorder.error);
        assertNotNull(recorder.result);
        assertTrue("expected at least three sample attractions",
                recorder.result.getAttractions().size() >= 3);
        assertEquals("system-garden", recorder.result.getAttractions().get(0).getId());
    }

    @Test
    public void nearby_excludesAttractionsOutsideTheRadius() {
        InMemoryAttractionRepository repository = new InMemoryAttractionRepository(Arrays.asList(
                attraction("close", -37.79640, 144.96150),
                attraction("far", -37.90000, 145.10000)));

        Recorder<AttractionResult> recorder = new Recorder<>();
        repository.loadNearbyAttractions(ORIGIN, 500.0, recorder);

        assertEquals(1, recorder.result.getAttractions().size());
        assertEquals("close", recorder.result.getAttractions().get(0).getId());
    }

    @Test
    public void nearby_reportsEmptyListRatherThanErrorWhenNothingIsInRange() {
        InMemoryAttractionRepository repository = new InMemoryAttractionRepository(
                Arrays.asList(attraction("far", -37.90000, 145.10000)));

        Recorder<AttractionResult> recorder = new Recorder<>();
        repository.loadNearbyAttractions(ORIGIN, 100.0, recorder);

        assertNull(recorder.error);
        assertTrue(recorder.result.isEmpty());
    }

    @Test
    public void nearby_reportsErrorForNonPositiveRadius() {
        Recorder<AttractionResult> recorder = new Recorder<>();
        new InMemoryAttractionRepository().loadNearbyAttractions(ORIGIN, 0.0, recorder);

        assertNull(recorder.result);
        assertTrue(recorder.error instanceof IllegalArgumentException);
    }

    @Test
    public void loadAttraction_returnsTheRequestedAttraction() {
        Recorder<Attraction> recorder = new Recorder<>();
        new InMemoryAttractionRepository().loadAttraction("old-arts", recorder);

        assertNull(recorder.error);
        assertEquals("Old Arts Building", recorder.result.getName());
    }

    @Test
    public void loadAttraction_reportsNotFoundForAnUnknownIdentifier() {
        Recorder<Attraction> recorder = new Recorder<>();
        new InMemoryAttractionRepository().loadAttraction("missing", recorder);

        assertNull(recorder.result);
        assertTrue(recorder.error instanceof AttractionNotFoundException);
    }

    @Test
    public void sampleData_containsAtLeastThreeDistinctAttractions() {
        List<Attraction> samples = SampleAttractions.get();
        assertTrue(samples.size() >= 3);
        for (Attraction attraction : samples) {
            assertTrue(attraction.getId().length() > 0);
            assertTrue(attraction.getDescription().length() > 0);
        }
    }
}
