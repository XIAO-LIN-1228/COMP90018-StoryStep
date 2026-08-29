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
import com.comp90018.storystep.domain.model.GeoPoint;

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

    private StoryStepDatabase database;
    private AttractionDao dao;

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
                attraction("a-id", "Alpha"))));

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
                LocalAttractionMapper.toEntity(attraction("a-id", "Original"))));
        dao.upsertAll(Collections.singletonList(
                LocalAttractionMapper.toEntity(attraction("a-id", "Updated"))));

        AttractionEntity stored = dao.findById("a-id");

        assertNotNull(stored);
        assertEquals("Updated", stored.name);
        assertEquals(1, dao.findAll().size());
    }

    @Test
    public void deleteAllClearsTheCache() {
        dao.upsertAll(Collections.singletonList(
                LocalAttractionMapper.toEntity(attraction("a-id", "Alpha"))));

        dao.deleteAll();

        assertTrue(dao.findAll().isEmpty());
    }
}
