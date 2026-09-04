package com.comp90018.storystep.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.data.local.FakeSavedAttractionDao;
import com.comp90018.storystep.data.local.SavedAttractionEntity;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.SavedAttraction;
import com.comp90018.storystep.domain.repository.RepositoryCallback;
import com.comp90018.storystep.domain.repository.SavedAttractionRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The saved store's rules.
 *
 * <p>What matters here is that a user's decision is recorded exactly once, that a failed write is
 * reported rather than assumed, and that the identifier set every screen renders from never claims
 * something the database does not hold.
 */
public class RoomSavedAttractionRepositoryTest {

    private static final Attraction MUSEUM = new Attraction("museum", "Melbourne Museum",
            AttractionCategory.MUSEUM, "Natural history and culture.", true,
            new GeoPoint(-37.80320, 144.97170));

    private static final Attraction GARDENS = new Attraction("gardens", "Carlton Gardens",
            AttractionCategory.PARK, "Heritage-listed gardens.", false,
            new GeoPoint(-37.80500, 144.97100));

    private FakeSavedAttractionDao dao;
    private RoomSavedAttractionRepository repository;

    @Before
    public void setUp() {
        dao = new FakeSavedAttractionDao();
        repository = TestSavedAttractions.over(dao);
    }

    @Test
    public void savingKeepsACopyOfTheAttractionRatherThanAReference() {
        assertTrue(save(MUSEUM));

        SavedAttractionEntity stored = dao.findById("museum");
        assertNotNull(stored);
        assertEquals("Melbourne Museum", stored.name);
        assertEquals("museum", stored.category);
        assertEquals("Natural history and culture.", stored.description);
        assertTrue(stored.indoor);
        assertEquals(-37.80320, stored.latitude, 1e-9);
        assertEquals(144.97170, stored.longitude, 1e-9);
        assertEquals("the clock supplies the saved time", 1_000L, stored.savedAtMillis);
    }

    @Test
    public void savingTheSamePlaceTwiceLeavesOneRecordAndReportsNoChange() {
        assertTrue(save(MUSEUM));
        assertFalse("a repeat save is the state the user already asked for", save(MUSEUM));

        assertEquals(1, dao.count());
    }

    @Test
    public void unsavingRemovesOnlyThatPlace() {
        save(MUSEUM);
        save(GARDENS);

        assertTrue(unsave("museum"));

        assertEquals(1, dao.count());
        assertNotNull(dao.findById("gardens"));
    }

    @Test
    public void unsavingSomethingThatWasNeverSavedIsNotAnError() {
        List<Boolean> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        repository.unsave("never-saved", new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean removed) {
                results.add(removed);
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        });

        assertTrue("a failed removal would be reported as an error", errors.isEmpty());
        assertEquals(Arrays.asList(false), results);
    }

    @Test
    public void theSavedListIsOrderedMostRecentlySavedFirst() {
        SavedAttractionEntity older = new SavedAttractionEntity();
        older.attractionId = "gardens";
        older.name = "Carlton Gardens";
        older.savedAtMillis = 500L;
        dao.insertIgnoring(older);
        save(MUSEUM);

        List<SavedAttraction> saved = loadSaved();

        assertEquals(2, saved.size());
        assertEquals("museum", saved.get(0).getId());
        assertEquals("gardens", saved.get(1).getId());
    }

    @Test
    public void clearingRemovesEverythingAndReportsHowMuch() {
        save(MUSEUM);
        save(GARDENS);

        List<Integer> removed = new ArrayList<>();
        repository.clearAll(new RepositoryCallback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                removed.add(count);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });

        assertEquals(Arrays.asList(2), removed);
        assertEquals(0, dao.count());
    }

    @Test
    public void aFailedWriteIsReportedAndLeavesTheIdentifiersAlone() {
        Set<String> observed = observeIds();
        save(MUSEUM);
        dao.failWrites = new IllegalStateException("disk full");

        List<Throwable> errors = new ArrayList<>();
        repository.save(GARDENS, new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean saved) {
                throw new AssertionError("the write failed and must not report success");
            }

            @Override
            public void onError(Throwable error) {
                errors.add(error);
            }
        });

        assertEquals(1, errors.size());
        assertEquals(new LinkedHashSet<>(Arrays.asList("museum")), observed);
    }

    @Test
    public void anObserverIsGivenTheIdentifiersItMissedAndEveryLaterChange() {
        save(MUSEUM);

        // Registering after the fact: a screen opened now must not start out blank.
        Set<String> observed = observeIds();
        assertEquals(new LinkedHashSet<>(Arrays.asList("museum")), observed);

        save(GARDENS);
        assertEquals(new LinkedHashSet<>(Arrays.asList("museum", "gardens")), observed);

        unsave("museum");
        assertEquals(new LinkedHashSet<>(Arrays.asList("gardens")), observed);
    }

    // --- helpers -----------------------------------------------------------------------------

    /** A live view of what the store last published, so assertions read as "what a screen sees". */
    private Set<String> observeIds() {
        Set<String> latest = new LinkedHashSet<>();
        repository.addObserver(new SavedAttractionRepository.Observer() {
            @Override
            public void onSavedIdsChanged(Set<String> ids) {
                latest.clear();
                latest.addAll(ids);
            }
        });
        return latest;
    }

    private boolean save(Attraction attraction) {
        List<Boolean> results = new ArrayList<>();
        repository.save(attraction, callbackInto(results));
        return results.get(0);
    }

    private boolean unsave(String attractionId) {
        List<Boolean> results = new ArrayList<>();
        repository.unsave(attractionId, callbackInto(results));
        return results.get(0);
    }

    private List<SavedAttraction> loadSaved() {
        List<List<SavedAttraction>> results = new ArrayList<>();
        repository.loadSaved(new RepositoryCallback<List<SavedAttraction>>() {
            @Override
            public void onSuccess(List<SavedAttraction> saved) {
                results.add(saved);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        });
        return results.get(0);
    }

    private static RepositoryCallback<Boolean> callbackInto(List<Boolean> results) {
        return new RepositoryCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                results.add(result);
            }

            @Override
            public void onError(Throwable error) {
                throw new AssertionError(error);
            }
        };
    }
}
