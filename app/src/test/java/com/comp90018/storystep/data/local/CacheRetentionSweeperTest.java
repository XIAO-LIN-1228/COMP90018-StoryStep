package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.comp90018.storystep.data.IndoorClassificationCodec;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.IndoorClassification;
import com.comp90018.storystep.domain.privacy.RetentionPolicy;
import com.comp90018.storystep.domain.session.FakeClock;

import org.junit.Test;

import java.util.Collections;

/**
 * The D-06 sweep: provider copies age out, the bundled set and the user's own records do not.
 *
 * <p>What this protects is not disk space. It is the rule that a cleanup can never take away
 * something the user chose to keep, which is only visible when both kinds of row sit in the same
 * database and only one of them is removed.
 */
public class CacheRetentionSweeperTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long THIRTY_DAYS_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final long LONG_AGO = NOW - THIRTY_DAYS_MILLIS - 1L;
    private static final long RECENT = NOW - 1_000L;

    private final FakeAttractionDao attractions = new FakeAttractionDao();
    private final FakeAttractionAreaDao areas = new FakeAttractionAreaDao();
    private final FakeWeatherCacheDao weather = new FakeWeatherCacheDao();
    private final FakeSavedAttractionDao saved = new FakeSavedAttractionDao();

    private CacheRetentionSweeper sweeper() {
        return new CacheRetentionSweeper(new DirectTransactions(), attractions, areas, weather,
                RetentionPolicy.defaultPolicy(), FakeClock.startingAt(NOW));
    }

    private void givenAttraction(String id, String provenance, long fetchedAtMillis) {
        AttractionEntity entity = new AttractionEntity();
        entity.id = id;
        entity.name = id;
        entity.category = "MUSEUM";
        entity.description = "";
        entity.indoor = IndoorClassificationCodec.toCode(IndoorClassification.UNKNOWN);
        entity.latitude = -37.8;
        entity.longitude = 144.96;
        entity.provenance = provenance;
        entity.fetchedAtMillis = fetchedAtMillis;
        attractions.upsertAll(Collections.singletonList(entity));
    }

    private void givenArea(String key, long fetchedAtMillis) {
        AttractionAreaEntity area = new AttractionAreaEntity();
        area.areaKey = key;
        area.fetchedAtMillis = fetchedAtMillis;
        area.recordCount = 1;
        areas.upsert(area);
    }

    private void givenWeather(String key, long fetchedAtMillis) {
        WeatherCacheEntity entity = new WeatherCacheEntity();
        entity.areaKey = key;
        entity.condition = "CLEAR";
        entity.temperatureCelsius = 18.0;
        entity.observedAtMillis = fetchedAtMillis;
        entity.fetchedAtMillis = fetchedAtMillis;
        weather.put(entity);
    }

    @Test
    public void expiredProviderCopiesAreRemovedAcrossAllThreeCaches() {
        givenAttraction("old", AttractionProvenance.OPENSTREETMAP.name(), LONG_AGO);
        givenArea("old-area", LONG_AGO);
        givenWeather("old-weather", LONG_AGO);

        int removed = sweeper().sweep();

        assertEquals(3, removed);
        assertEquals(0, attractions.count());
        assertEquals(0, areas.count());
        assertEquals(0, weather.count());
    }

    @Test
    public void copiesInsideTheWindowAreKept() {
        givenAttraction("recent", AttractionProvenance.OPENSTREETMAP.name(), RECENT);
        givenArea("recent-area", RECENT);
        givenWeather("recent-weather", RECENT);

        assertEquals(0, sweeper().sweep());
        assertEquals(1, attractions.count());
        assertEquals(1, areas.count());
        assertEquals(1, weather.count());
    }

    @Test
    public void theBundledSeedSetIsNeverRemovedHoweverOldItIs() {
        // The seed set has no meaningful fetch time — it shipped with the app — so it would look
        // infinitely old to a sweep that judged by age alone. It is what the app shows with no
        // network, and removing it would leave a first launch in a new area with nothing at all.
        givenAttraction("seeded", AttractionEntity.PROVENANCE_SEED, 0L);
        givenAttraction("fetched", AttractionProvenance.OPENSTREETMAP.name(), LONG_AGO);

        assertEquals(1, sweeper().sweep());
        assertEquals(1, attractions.count());
        assertNotNull(attractions.findById("seeded"));
        assertNull(attractions.findById("fetched"));
    }

    @Test
    public void aSavedPlaceSurvivesTheRemovalOfItsCachedRecord() {
        // The point of the saved snapshot. The cached copy ages out; the place the user kept does
        // not, and the detail screen still has something to show for it.
        givenAttraction("kept-place", AttractionProvenance.OPENSTREETMAP.name(), LONG_AGO);
        SavedAttractionEntity snapshot = new SavedAttractionEntity();
        snapshot.attractionId = "kept-place";
        snapshot.name = "Kept Place";
        snapshot.category = "MUSEUM";
        snapshot.description = "";
        snapshot.indoor = IndoorClassificationCodec.toCode(IndoorClassification.UNKNOWN);
        snapshot.latitude = -37.8;
        snapshot.longitude = 144.96;
        snapshot.savedAtMillis = LONG_AGO;
        snapshot.provenance = AttractionProvenance.OPENSTREETMAP.name();
        saved.insertIgnoring(snapshot);

        sweeper().sweep();

        assertNull("the cached copy should have aged out", attractions.findById("kept-place"));
        assertEquals("the user's saved copy must survive it", 1, saved.count());
        assertNotNull(saved.findById("kept-place"));
    }

    @Test
    public void anEmptyDatabaseSweepsToNothingWithoutFailing() {
        assertEquals(0, sweeper().sweep());
    }
}
