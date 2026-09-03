package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Every shipped schema migration, kept together so the list registered on the builder and the list
 * covered by the migration test are visibly the same list.
 *
 * <p>Destructive fallback is never registered. The database carries user-owned exploration
 * history, and dropping it to make a schema change compile would delete the exact data this
 * feature exists to protect.
 */
public final class StoryStepMigrations {

    private StoryStepMigrations() {
        // Utility class.
    }

    /**
     * Version 1 to 2: adds sessions, their route points and their visits, and records where each
     * cached attraction came from.
     *
     * <p>The existing {@code attractions} rows are kept and back-filled rather than re-seeded:
     * they are valid content, and the only thing version 1 failed to record about them is their
     * provenance, which is set to the seed marker because that is what they in fact are.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `attractions` ADD COLUMN `provenance` TEXT");
            database.execSQL("ALTER TABLE `attractions` "
                    + "ADD COLUMN `fetched_at_millis` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE `attractions` SET `provenance` = 'seed' "
                    + "WHERE `provenance` IS NULL");

            database.execSQL("CREATE TABLE IF NOT EXISTS `sessions` ("
                    + "`id` TEXT NOT NULL, "
                    + "`state` TEXT, "
                    + "`pause_reason` TEXT, "
                    + "`start_wall_clock_millis` INTEGER NOT NULL, "
                    + "`end_wall_clock_millis` INTEGER NOT NULL, "
                    + "`active_duration_millis` INTEGER NOT NULL, "
                    + "`distance_meters` REAL NOT NULL, "
                    + "`segment` INTEGER NOT NULL, "
                    + "`segment_count` INTEGER NOT NULL, "
                    + "`finished` INTEGER NOT NULL, "
                    + "`last_checkpoint_wall_clock_millis` INTEGER NOT NULL, "
                    + "`destination_id` TEXT, "
                    + "`destination_name` TEXT, "
                    + "`destination_category` TEXT, "
                    + "`destination_description` TEXT, "
                    + "`destination_indoor` INTEGER NOT NULL, "
                    + "`destination_latitude` REAL NOT NULL, "
                    + "`destination_longitude` REAL NOT NULL, "
                    + "PRIMARY KEY(`id`))");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sessions_finished` "
                            + "ON `sessions` (`finished`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `route_points` ("
                    + "`row_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`session_id` TEXT NOT NULL, "
                    + "`sequence` INTEGER NOT NULL, "
                    + "`segment` INTEGER NOT NULL, "
                    + "`latitude` REAL NOT NULL, "
                    + "`longitude` REAL NOT NULL, "
                    + "`accuracy_meters` REAL NOT NULL, "
                    + "`wall_clock_millis` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_route_points_session_id_sequence` "
                    + "ON `route_points` (`session_id`, `sequence`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_route_points_session_id` "
                    + "ON `route_points` (`session_id`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `visits` ("
                    + "`row_id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`session_id` TEXT NOT NULL, "
                    + "`attraction_id` TEXT NOT NULL, "
                    + "`arrival_wall_clock_millis` INTEGER NOT NULL, "
                    + "`attraction_name` TEXT, "
                    + "`story` TEXT, "
                    + "`unread` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`session_id`) REFERENCES `sessions`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE )");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_visits_session_id_attraction_id` "
                    + "ON `visits` (`session_id`, `attraction_id`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_visits_session_id` "
                    + "ON `visits` (`session_id`)");
        }
    };

    /** Every migration, in order, for the builder and the migration test. */
    public static Migration[] all() {
        return new Migration[]{MIGRATION_1_2};
    }
}
