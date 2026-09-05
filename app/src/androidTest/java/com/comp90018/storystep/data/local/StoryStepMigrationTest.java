package com.comp90018.storystep.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Every shipped migration, run against a database built from the exported schema of the version
 * it starts from.
 *
 * <p>The point is not that the migration executes. It is that the rows a user already had are
 * still there and still correct afterwards — a migration that dropped the table would pass a test
 * that only checked for the absence of an exception.
 */
@RunWith(AndroidJUnit4.class)
public class StoryStepMigrationTest {

    private static final String TEST_DATABASE = "storystep-migration-test.db";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            StoryStepDatabase.class,
            java.util.Collections.emptyList(),
            new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void migratingFromOneToTwoKeepsTheCachedAttractionsAndMarksTheirProvenance()
            throws IOException {
        SupportSQLiteDatabase version1 = helper.createDatabase(TEST_DATABASE, 1);
        ContentValues row = new ContentValues();
        row.put("id", "old-arts");
        row.put("name", "Old Arts Building");
        row.put("category", "university_landmark");
        row.put("description", "The 1924 clocktower building.");
        row.put("indoor", 0);
        row.put("latitude", -37.79810);
        row.put("longitude", 144.95910);
        version1.insert("attractions", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, row);
        version1.close();

        // runMigrationsAndValidate also checks the resulting schema against the exported 2.json,
        // which is what catches a hand-written CREATE TABLE that has drifted from the entity.
        SupportSQLiteDatabase version2 = helper.runMigrationsAndValidate(
                TEST_DATABASE, 2, true, StoryStepMigrations.MIGRATION_1_2);

        Cursor cursor = version2.query(
                "SELECT name, latitude, provenance, fetched_at_millis FROM attractions "
                        + "WHERE id = 'old-arts'");
        assertTrue("the user's cached row must survive the migration", cursor.moveToFirst());
        assertEquals("Old Arts Building", cursor.getString(0));
        assertEquals(-37.79810, cursor.getDouble(1), 1e-9);
        // Version 1 rows really were the bundled set, so that is what they are recorded as.
        assertEquals("seed", cursor.getString(2));
        assertEquals(0L, cursor.getLong(3));
        cursor.close();
        version2.close();
    }

    @Test
    public void migratingFromTwoToThreeAddsTheSavedTableAndKeepsWhatWasThere() throws IOException {
        SupportSQLiteDatabase version2 = helper.createDatabase(TEST_DATABASE, 2);
        ContentValues row = new ContentValues();
        row.put("id", "old-arts");
        row.put("name", "Old Arts Building");
        row.put("category", "university_landmark");
        row.put("description", "The 1924 clocktower building.");
        row.put("indoor", 0);
        row.put("latitude", -37.79810);
        row.put("longitude", 144.95910);
        row.put("provenance", "seed");
        row.put("fetched_at_millis", 0L);
        version2.insert("attractions", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, row);
        version2.close();

        SupportSQLiteDatabase version3 = helper.runMigrationsAndValidate(
                TEST_DATABASE, 3, true, StoryStepMigrations.MIGRATION_2_3);

        Cursor cached = version3.query("SELECT name FROM attractions WHERE id = 'old-arts'");
        assertTrue("an additive migration must not disturb existing rows", cached.moveToFirst());
        assertEquals("Old Arts Building", cached.getString(0));
        cached.close();

        Cursor saved = version3.query("SELECT COUNT(*) FROM saved_attractions");
        assertTrue(saved.moveToFirst());
        assertEquals("the new table starts empty; nothing before this version recorded a save",
                0, saved.getInt(0));
        saved.close();
        version3.close();
    }

    @Test
    public void migratingFromThreeToFourAddsTheWeatherCacheAndKeepsUserRecords() throws IOException {
        SupportSQLiteDatabase version3 = helper.createDatabase(TEST_DATABASE, 3);
        ContentValues saved = new ContentValues();
        saved.put("attraction_id", "old-arts");
        saved.put("name", "Old Arts Building");
        saved.put("category", "university_landmark");
        saved.put("description", "The 1924 clocktower building.");
        saved.put("indoor", 0);
        saved.put("latitude", -37.79810);
        saved.put("longitude", 144.95910);
        saved.put("saved_at_millis", 1_700_000_000_000L);
        version3.insert("saved_attractions",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, saved);
        version3.close();

        SupportSQLiteDatabase version4 = helper.runMigrationsAndValidate(
                TEST_DATABASE, 4, true, StoryStepMigrations.MIGRATION_3_4);

        // Adding a cache table must not touch what the user chose to keep.
        Cursor keptSave = version4.query(
                "SELECT name FROM saved_attractions WHERE attraction_id = 'old-arts'");
        assertTrue("an additive migration must not disturb a saved place", keptSave.moveToFirst());
        assertEquals("Old Arts Building", keptSave.getString(0));
        keptSave.close();

        Cursor weather = version4.query("SELECT COUNT(*) FROM weather_cache");
        assertTrue(weather.moveToFirst());
        assertEquals("the new table starts empty; there is no earlier weather to carry forward",
                0, weather.getInt(0));
        weather.close();
        version4.close();
    }

    @Test
    public void theMigratedDatabaseCarriesTheNewSessionTables() throws IOException {
        helper.createDatabase(TEST_DATABASE, 1).close();
        helper.runMigrationsAndValidate(
                TEST_DATABASE, 2, true, StoryStepMigrations.MIGRATION_1_2);

        // Opening through Room proves the migrated schema satisfies the entities, not only the
        // raw SQL: a mismatch here is a destructive-fallback bug waiting to happen in production.
        StoryStepDatabase database = Room.databaseBuilder(
                        ApplicationProvider.getApplicationContext(),
                        StoryStepDatabase.class,
                        TEST_DATABASE)
                .addMigrations(StoryStepMigrations.all())
                .allowMainThreadQueries()
                .build();
        assertEquals(0, database.sessionDao().countUnfinished());
        assertNotNull(database.routePointDao().findBySession("none"));
        assertEquals(0, database.visitDao().countBySession("none"));
        assertEquals(0, database.savedAttractionDao().count());
        assertEquals(0, database.weatherCacheDao().count());
        database.close();
    }
}
