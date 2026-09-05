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
    public void migratingFromFourToFiveAddsTheSessionContextAndLabelsInterruptedSessions()
            throws IOException {
        SupportSQLiteDatabase version4 = helper.createDatabase(TEST_DATABASE, 4);

        ContentValues interrupted = sessionValues("interrupted-walk", "PROCESS_INTERRUPTED");
        version4.insert("sessions", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                interrupted);
        ContentValues ordinary = sessionValues("ordinary-walk", null);
        version4.insert("sessions", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                ordinary);

        ContentValues point = new ContentValues();
        point.put("session_id", "interrupted-walk");
        point.put("sequence", 0);
        point.put("segment", 1);
        point.put("latitude", -37.79630);
        point.put("longitude", 144.96140);
        point.put("accuracy_meters", 8.0);
        point.put("wall_clock_millis", 1_700_000_000_000L);
        version4.insert("route_points", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT,
                point);

        ContentValues visit = new ContentValues();
        visit.put("session_id", "interrupted-walk");
        visit.put("attraction_id", "old-arts");
        visit.put("arrival_wall_clock_millis", 1_700_000_000_000L);
        visit.put("attraction_name", "Old Arts Building");
        visit.put("story", "A recorded story.");
        visit.put("unread", 1);
        version4.insert("visits", android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, visit);

        ContentValues saved = new ContentValues();
        saved.put("attraction_id", "museum");
        saved.put("name", "Melbourne Museum");
        saved.put("category", "museum");
        saved.put("description", "Natural history and culture.");
        saved.put("indoor", 1);
        saved.put("latitude", -37.80320);
        saved.put("longitude", 144.97170);
        saved.put("saved_at_millis", 1_700_000_000_000L);
        version4.insert("saved_attractions",
                android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT, saved);
        version4.close();

        SupportSQLiteDatabase version5 = helper.runMigrationsAndValidate(
                TEST_DATABASE, 5, true, StoryStepMigrations.MIGRATION_4_5);

        // Every user-owned record survives an additive migration.
        assertEquals(2, count(version5, "SELECT COUNT(*) FROM sessions"));
        assertEquals(1, count(version5, "SELECT COUNT(*) FROM route_points"));
        assertEquals(1, count(version5, "SELECT COUNT(*) FROM visits"));
        assertEquals("a saved place has nothing to do with a session schema change",
                1, count(version5, "SELECT COUNT(*) FROM saved_attractions"));

        // The one fact version 4 recorded that can be carried forward.
        assertEquals("a session still paused for a process interruption was in fact interrupted",
                1, count(version5,
                        "SELECT interrupted FROM sessions WHERE id = 'interrupted-walk'"));
        assertEquals("a session with no such pause reason must not be labelled",
                0, count(version5, "SELECT interrupted FROM sessions WHERE id = 'ordinary-walk'"));

        // Nothing invents weather for a walk that happened before the app recorded any.
        Cursor weather = version5.query(
                "SELECT weather_area_key, weather_observed_at_millis, movement_state "
                        + "FROM sessions WHERE id = 'ordinary-walk'");
        assertTrue(weather.moveToFirst());
        assertTrue("a null area key is how 'no weather was recorded' is spelled",
                weather.isNull(0));
        assertEquals("a zero instant can never produce a snapshot, which is the point",
                0L, weather.getLong(1));
        assertTrue("movement that was never recorded stays null", weather.isNull(2));
        weather.close();

        version5.close();
    }

    private static ContentValues sessionValues(String id, String pauseReason) {
        ContentValues session = new ContentValues();
        session.put("id", id);
        session.put("state", pauseReason == null ? "COMPLETED" : "PAUSED");
        session.put("pause_reason", pauseReason);
        session.put("start_wall_clock_millis", 1_700_000_000_000L);
        session.put("end_wall_clock_millis", pauseReason == null ? 1_700_000_100_000L : 0L);
        session.put("active_duration_millis", 100_000L);
        session.put("distance_meters", 250.0);
        session.put("segment", 1);
        session.put("segment_count", 1);
        session.put("finished", pauseReason == null ? 1 : 0);
        session.put("last_checkpoint_wall_clock_millis", 1_700_000_050_000L);
        session.put("destination_indoor", 0);
        session.put("destination_latitude", 0.0);
        session.put("destination_longitude", 0.0);
        return session;
    }

    private static int count(SupportSQLiteDatabase database, String query) {
        Cursor cursor = database.query(query);
        assertTrue(cursor.moveToFirst());
        int value = cursor.getInt(0);
        cursor.close();
        return value;
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
        // The history queries added at version 5 must run against the migrated schema too.
        assertEquals(0, database.sessionDao().countFinished());
        assertTrue(database.sessionDao().findFinished().isEmpty());
        database.close();
    }
}
