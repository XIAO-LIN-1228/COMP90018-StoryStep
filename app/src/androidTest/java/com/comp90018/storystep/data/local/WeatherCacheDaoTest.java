package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The weather cache against real SQLite.
 *
 * <p>The JVM fake reproduces these rules; this is where the primary key that actually enforces
 * them runs. A key that failed to replace would grow one row per refresh, which on a walking user
 * is one row every half hour for as long as the install lasts.
 */
@RunWith(AndroidJUnit4.class)
public class WeatherCacheDaoTest {

    private static final String MELBOURNE = "-757:2899";
    private static final String SYDNEY = "-678:3024";
    private static final long FETCHED_AT = 1_700_000_000_000L;

    private StoryStepDatabase database;
    private WeatherCacheDao dao;

    private static WeatherCacheEntity row(String areaKey,
                                          WeatherCondition condition,
                                          double celsius,
                                          long fetchedAtMillis) {
        return LocalWeatherMapper.toEntity(new WeatherSnapshot(areaKey, condition, celsius,
                fetchedAtMillis, fetchedAtMillis, WeatherSnapshot.Provenance.NETWORK));
    }

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.weatherCacheDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void anObservationSurvivesTheRoundTrip() {
        dao.upsert(row(MELBOURNE, WeatherCondition.RAIN, 14.3, FETCHED_AT));

        WeatherSnapshot read = LocalWeatherMapper.toDomain(dao.findByArea(MELBOURNE));

        assertNotNull(read);
        assertEquals(MELBOURNE, read.getAreaKey());
        assertEquals(WeatherCondition.RAIN, read.getCondition());
        assertEquals(14.3, read.getTemperatureCelsius(), 1e-9);
        assertEquals(FETCHED_AT, read.getFetchedAtMillis());
        // Whatever it was when written, a row read back is cached content, and every screen has
        // to be able to tell that from a live answer.
        assertEquals(WeatherSnapshot.Provenance.CACHE, read.getProvenance());
    }

    @Test
    public void refreshingAnAreaReplacesItsRowRatherThanAddingOne() {
        dao.upsert(row(MELBOURNE, WeatherCondition.RAIN, 14.3, FETCHED_AT));
        dao.upsert(row(MELBOURNE, WeatherCondition.CLEAR, 19.0, FETCHED_AT + 60_000L));

        assertEquals(1, dao.count());
        WeatherCacheEntity stored = dao.findByArea(MELBOURNE);
        assertNotNull(stored);
        assertEquals(WeatherCondition.CLEAR.name(), stored.condition);
        assertEquals(19.0, stored.temperatureCelsius, 1e-9);
    }

    @Test
    public void separateAreasKeepSeparateRows() {
        dao.upsert(row(MELBOURNE, WeatherCondition.RAIN, 14.3, FETCHED_AT));
        dao.upsert(row(SYDNEY, WeatherCondition.CLEAR, 22.0, FETCHED_AT));

        assertEquals(2, dao.count());
        assertEquals(WeatherCondition.RAIN.name(), dao.findByArea(MELBOURNE).condition);
        assertEquals(WeatherCondition.CLEAR.name(), dao.findByArea(SYDNEY).condition);
    }

    @Test
    public void anUncachedAreaReadsAsAbsentRatherThanEmpty() {
        assertNull(dao.findByArea("0:0"));
        assertNull(LocalWeatherMapper.toDomain(dao.findByArea("0:0")));
    }

    @Test
    public void clearingRemovesEveryAreaAndReportsHowMany() {
        dao.upsert(row(MELBOURNE, WeatherCondition.RAIN, 14.3, FETCHED_AT));
        dao.upsert(row(SYDNEY, WeatherCondition.CLEAR, 22.0, FETCHED_AT));

        assertEquals(2, dao.deleteAll());
        assertEquals(0, dao.count());
    }

    @Test
    public void aRowStoredWithAnUnrecognisedConditionReadsBackAsUnknown() {
        // Guards the forward path: a future version adding a condition, then the user downgrading.
        // The row must degrade to unknown rather than crash the screen that reads it.
        WeatherCacheEntity written = row(MELBOURNE, WeatherCondition.CLEAR, 14.0, FETCHED_AT);
        written.condition = "HAIL_OF_FROGS";
        dao.upsert(written);

        WeatherSnapshot read = LocalWeatherMapper.toDomain(dao.findByArea(MELBOURNE));

        assertNotNull(read);
        assertEquals(WeatherCondition.UNKNOWN, read.getCondition());
        assertEquals(14.0, read.getTemperatureCelsius(), 1e-9);
    }
}
