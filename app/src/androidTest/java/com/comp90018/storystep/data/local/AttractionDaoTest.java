package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.AttractionCategory;
import com.comp90018.storystep.domain.model.AttractionProvenance;
import com.comp90018.storystep.domain.model.GeoPoint;
import com.comp90018.storystep.domain.model.IndoorClassification;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Instrumentation tests for the Room boundary, run against an in-memory database. */
@RunWith(AndroidJUnit4.class)
public class AttractionDaoTest {

    private static final long FETCHED_AT = 1_700_000_000_000L;

    private StoryStepDatabase database;
    private AttractionDao dao;

    private static Attraction attraction(String id, String name) {
        return Attraction.seeded(id, name, AttractionCategory.MUSEUM, "Description",
                IndoorClassification.INDOOR, new GeoPoint(-37.79750, 144.96400));
    }

    private static Attraction fetched(String id, double latitude, double longitude) {
        return new Attraction(id, "Fetched " + id, AttractionCategory.MUSEUM, "",
                IndoorClassification.UNKNOWN, new GeoPoint(latitude, longitude),
                AttractionProvenance.OPENSTREETMAP, null);
    }

    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, StoryStepDatabase.class)
                .allowMainThreadQueries()
                .build();
        dao = database.attractionDao();
    }

    @After
    public void closeDatabase() {
        database.close();
    }

    @Test
    public void insertedAttractionsCanBeReadBack() {
        dao.upsertAll(LocalAttractionMapper.toEntities(Arrays.asList(
                attraction("b-id", "Beta"),
                attraction("a-id", "Alpha")), FETCHED_AT));

        List<Attraction> stored = LocalAttractionMapper.toDomainList(dao.findAll());

        assertEquals(2, stored.size());
        assertEquals("Alpha", stored.get(0).getName());
    }

    @Test
    public void findByIdReturnsNullForAnUnknownIdentifier() {
        assertNull(dao.findById("missing"));
    }

    @Test
    public void upsertReplacesAnExistingRow() {
        dao.upsertAll(Collections.singletonList(
                LocalAttractionMapper.toEntity(attraction("a-id", "Original"), FETCHED_AT)));
        dao.upsertAll(Collections.singletonList(
                LocalAttractionMapper.toEntity(attraction("a-id", "Updated"), FETCHED_AT)));

        AttractionEntity stored = dao.findById("a-id");

        assertNotNull(stored);
        assertEquals("Updated", stored.name);
        assertEquals(1, dao.findAll().size());
    }

    @Test
    public void deleteAllClearsTheCache() {
        dao.upsertAll(Collections.singletonList(
                LocalAttractionMapper.toEntity(attraction("a-id", "Alpha"), FETCHED_AT)));

        dao.deleteAll();

        assertTrue(dao.findAll().isEmpty());
    }

    @Test
    public void theScopedDeleteRemovesProviderRowsInTheBoxAndNothingElse() {
        dao.upsertAll(LocalAttractionMapper.toEntities(Arrays.asList(
                attraction("seeded", "Bundled Place"),
                fetched("osm:node/inside", -37.79750, 144.96400),
                fetched("osm:node/outside", -37.85000, 145.05000)), FETCHED_AT));

        int removed = dao.deleteProviderRowsWithin(
                AttractionEntity.PROVENANCE_SEED, -37.80, -37.79, 144.96, 144.97);

        assertEquals(1, removed);
        assertNull("a provider row inside the box is replaced", dao.findById("osm:node/inside"));
        assertNotNull("a provider row in another area must survive",
                dao.findById("osm:node/outside"));
        assertNotNull("the bundled set is never removed by a refresh", dao.findById("seeded"));
    }

    @Test
    public void theIndoorClassificationRoundTripsThroughStorageIncludingUnknown() {
        dao.upsertAll(LocalAttractionMapper.toEntities(
                Collections.singletonList(fetched("osm:node/1", -37.79750, 144.96400)),
                FETCHED_AT));

        Attraction stored = LocalAttractionMapper.toDomain(dao.findById("osm:node/1"));

        assertEquals(IndoorClassification.UNKNOWN, stored.getIndoor());
        assertEquals(AttractionProvenance.OPENSTREETMAP, stored.getProvenance());
    }
}
