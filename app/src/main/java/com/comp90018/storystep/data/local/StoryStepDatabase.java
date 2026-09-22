package com.comp90018.storystep.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * The application database.
 *
 * <p>Schema version 10 holds the cached attraction table and the record of which areas have been
 * fetched, exploration sessions with the weather and movement recorded while they ran, their
 * sampled route points, their visits, the user's saved attractions and the cached weather
 * observation for each area, journey publication metadata, and versioned theme-run snapshots with
 * station skips and observation answers. Theme progress derives from the session's committed visits.
 *
 * <p><b>Migration policy:</b> every schema change must bump {@code version} and ship a
 * {@link androidx.room.migration.Migration} added to {@link StoryStepMigrations#all()} — the
 * database carries user-owned history, so destructive migration is not acceptable. Schemas are
 * exported to {@code app/schemas} and committed so {@code MigrationTestHelper} can build an old
 * database and prove the migration preserves its rows.
 *
 * <p>Route points and visits declare a cascading foreign key onto their session, so deleting a
 * session removes exactly the records it owns. Room turns SQLite's foreign-key enforcement on for
 * a schema that declares them, which is what makes that cascade actually run.
 */
@Database(
        entities = {
                AttractionEntity.class,
                SessionEntity.class,
                RoutePointEntity.class,
                VisitEntity.class,
                SavedAttractionEntity.class,
                WeatherCacheEntity.class,
                AttractionAreaEntity.class,
                ThemeRunEntity.class,
                ThemeStopEntity.class
        },
        version = 10,
        exportSchema = true)
public abstract class StoryStepDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "storystep.db";

    public abstract AttractionDao attractionDao();

    public abstract AttractionAreaDao attractionAreaDao();

    public abstract ThemeDao themeDao();

    public abstract SessionDao sessionDao();

    public abstract RoutePointDao routePointDao();

    public abstract VisitDao visitDao();

    public abstract SavedAttractionDao savedAttractionDao();

    public abstract WeatherCacheDao weatherCacheDao();

    public static StoryStepDatabase create(Context context) {
        return Room.databaseBuilder(
                        context.getApplicationContext(),
                        StoryStepDatabase.class,
                        DATABASE_NAME)
                .addMigrations(StoryStepMigrations.all())
                .build();
    }
}
