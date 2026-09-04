package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.GeoPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * The saved table against real SQLite.
 *
 * <p>The JVM fake reproduces these rules; this is where the constraint that actually enforces them
 * runs. A primary key that failed to reject a duplicate would let the same place be saved twice for
 * as long as the install lasted.
 */
@RunWith(AndroidJUnit4.class)
public class SavedAttractionDaoTest {

    private StoryStepDatabase database;
    private SavedAttractionDao dao;

    private static Attraction attraction(String id, String name) {
        return new Attraction(id, name, AttractionCategory.MUSEUM, "Description", true,
                new GeoPoint(-37.79750, 144.96400));
    }

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.savedAttractionDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void aSavedAttractionCanBeReadBackWithItsCopiedContent() {
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 1_000L));

        SavedAttractionEntity stored = dao.findById("museum");
        assertNotNull(stored);
        assertEquals("Melbourne Museum", stored.name);
        assertEquals("museum", stored.category);
        assertEquals(1_000L, stored.savedAtMillis);
    }

    @Test
    public void aSecondSaveOfTheSamePlaceIsIgnoredAndKeepsTheOriginalTime() {
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 1_000L));

        long rowId = dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 9_000L));

        assertEquals("an ignored insert reports no row", -1L, rowId);
        assertEquals(1, dao.count());
        assertEquals("re-saving must not reshuffle the list", 1_000L,
                dao.findById("museum").savedAtMillis);
    }

    @Test
    public void savedPlacesComeBackMostRecentlySavedFirst() {
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("older", "Older"), 1_000L));
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("newer", "Newer"), 2_000L));

        List<SavedAttractionEntity> saved = dao.findAllByRecency();

        assertEquals(2, saved.size());
        assertEquals("newer", saved.get(0).attractionId);
        assertEquals("older", saved.get(1).attractionId);
    }

    @Test
    public void removingOneSavedPlaceLeavesTheOthers() {
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 1_000L));
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("gardens", "Carlton Gardens"), 2_000L));

        assertEquals(1, dao.deleteById("museum"));

        assertNull(dao.findById("museum"));
        assertNotNull(dao.findById("gardens"));
        assertEquals(0, dao.deleteById("museum"));
    }

    /**
     * The saved table has no foreign key onto {@code attractions}, so clearing the cache must
     * leave saved places untouched. This is the behaviour REQ-17.3 exists for, and the one a
     * cascade would silently break.
     */
    @Test
    public void clearingTheAttractionCacheDoesNotUnsaveAnything() {
        database.attractionDao().upsertAll(LocalAttractionMapper.toEntities(
                java.util.Collections.singletonList(attraction("museum", "Melbourne Museum"))));
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 1_000L));

        database.attractionDao().deleteAll();

        assertEquals(1, dao.count());
        assertEquals("the saved copy stays readable with no cached row behind it",
                "Melbourne Museum", dao.findById("museum").name);
    }

    @Test
    public void clearingReportsHowManySavedPlacesItRemoved() {
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("museum", "Melbourne Museum"), 1_000L));
        dao.insertIgnoring(LocalSavedAttractionMapper.toEntity(
                attraction("gardens", "Carlton Gardens"), 2_000L));

        assertEquals(2, dao.deleteAll());
        assertEquals(0, dao.count());
    }
}
