package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The fetched-area bookkeeping at the Room boundary.
 *
 * <p>The case worth having instrumentation for is the empty one: an area recorded as fetched with
 * no records in it. That row is the only thing distinguishing a neighbourhood the provider genuinely
 * has nothing in from one that has never been asked about, and a schema or DAO change that lost it
 * would make the two indistinguishable without any test failing elsewhere.
 */
@RunWith(AndroidJUnit4.class)
public class AttractionAreaDaoTest {

    private static final String AREA_KEY = "-3780:14496";
    private static final long FETCHED_AT = 1_700_000_000_000L;

    private StoryStepDatabase database;
    private AttractionAreaDao dao;

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.attractionAreaDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void anUnfetchedAreaHasNoRow() {
        assertNull(dao.findByKey(AREA_KEY));
        assertEquals(0, dao.count());
    }

    @Test
    public void aFetchedAreaRoundTripsWithItsTimeAndCount() {
        dao.upsert(area(AREA_KEY, FETCHED_AT, 12));

        AttractionAreaEntity stored = dao.findByKey(AREA_KEY);

        assertNotNull(stored);
        assertEquals(FETCHED_AT, stored.fetchedAtMillis);
        assertEquals(12, stored.recordCount);
    }

    @Test
    public void anAreaFetchedWithNothingInItIsStillRecordedAsFetched() {
        dao.upsert(area(AREA_KEY, FETCHED_AT, 0));

        AttractionAreaEntity stored = dao.findByKey(AREA_KEY);

        assertNotNull("zero records is an answer, not an absence of one", stored);
        assertEquals(0, stored.recordCount);
    }

    @Test
    public void refetchingAnAreaReplacesItsRowRatherThanAddingOne() {
        dao.upsert(area(AREA_KEY, FETCHED_AT, 3));
        dao.upsert(area(AREA_KEY, FETCHED_AT + 1_000L, 5));

        assertEquals(1, dao.count());
        assertEquals(5, dao.findByKey(AREA_KEY).recordCount);
        assertEquals(FETCHED_AT + 1_000L, dao.findByKey(AREA_KEY).fetchedAtMillis);
    }

    @Test
    public void areasAreKeptSeparately() {
        dao.upsert(area(AREA_KEY, FETCHED_AT, 3));
        dao.upsert(area("-3785:14505", FETCHED_AT, 1));

        assertEquals(2, dao.count());
        assertEquals(3, dao.findByKey(AREA_KEY).recordCount);
    }

    @Test
    public void clearingForgetsEveryFetchAndReportsHowMany() {
        dao.upsert(area(AREA_KEY, FETCHED_AT, 3));
        dao.upsert(area("-3785:14505", FETCHED_AT, 1));

        assertEquals(2, dao.deleteAll());
        assertEquals(0, dao.count());
    }

    private static AttractionAreaEntity area(String key, long fetchedAtMillis, int recordCount) {
        AttractionAreaEntity entity = new AttractionAreaEntity();
        entity.areaKey = key;
        entity.fetchedAtMillis = fetchedAtMillis;
        entity.recordCount = recordCount;
        return entity;
    }
}
