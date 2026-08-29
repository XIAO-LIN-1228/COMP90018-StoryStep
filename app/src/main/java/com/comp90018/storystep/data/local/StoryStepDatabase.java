package com.comp90018.storystep.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * The application database.
 *
 * <p>Schema version 1 holds the cached attraction table only. Saved attractions, visit history
 * and exploration sessions are added in a later stage.
 *
 * <p><b>Migration policy:</b> every schema change must bump {@code version} and ship a
 * {@link androidx.room.migration.Migration} added to {@link #create(Context)} — the database
 * carries user-owned history, so destructive migration is not acceptable once history exists.
 * Turn on {@code exportSchema} together with the {@code room.schemaLocation} annotation
 * processor argument at that point, and commit the generated schema JSON so migrations can be
 * tested with {@code MigrationTestHelper}.
 */
@Database(entities = {AttractionEntity.class}, version = 1, exportSchema = false)
public abstract class StoryStepDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "storystep.db";

    public abstract AttractionDao attractionDao();

    public static StoryStepDatabase create(Context context) {
        return Room.databaseBuilder(
                        context.getApplicationContext(),
                        StoryStepDatabase.class,
                        DATABASE_NAME)
                .build();
    }
}
