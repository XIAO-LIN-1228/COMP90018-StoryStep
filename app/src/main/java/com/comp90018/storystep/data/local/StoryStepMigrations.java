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

    /**
     * Version 2 to 3: adds the saved-attraction table.
     *
     * <p>Purely additive — there is nothing to back-fill, because nothing before this version
     * recorded a save. The table deliberately has no foreign key onto {@code attractions}; see
     * {@link SavedAttractionEntity} for why a cascade there would be a silent unsave.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `saved_attractions` ("
                    + "`attraction_id` TEXT NOT NULL, "
                    + "`name` TEXT, "
                    + "`category` TEXT, "
                    + "`description` TEXT, "
                    + "`indoor` INTEGER NOT NULL, "
                    + "`latitude` REAL NOT NULL, "
                    + "`longitude` REAL NOT NULL, "
                    + "`saved_at_millis` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`attraction_id`))");
        }
    };

    /**
     * Version 3 to 4: adds the weather cache.
     *
     * <p>Additive, and the new table holds nothing the user owns — it is the last observation
     * fetched per area, keyed by a coarse grid cell rather than by a position. Nothing is
     * back-filled because there is no earlier weather to carry forward, and losing the whole table
     * would cost one network request.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `weather_cache` ("
                    + "`area_key` TEXT NOT NULL, "
                    + "`condition` TEXT, "
                    + "`temperature_celsius` REAL NOT NULL, "
                    + "`observed_at_millis` INTEGER NOT NULL, "
                    + "`fetched_at_millis` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`area_key`))");
        }
    };


    /**
     * Version 4 to 5: records the weather observed while a session ran, the movement totals it
     * accumulated and whether it was ever interrupted.
     *
     * <p>Additive, and the only back-fill that is honest is the interrupted flag: a session still
     * paused for a process interruption was in fact interrupted, and history must keep saying so
     * after {@code finish} clears its pause reason. Nothing here invents weather for a walk that
     * happened before the app recorded any — those rows keep a null area key, which is exactly how
     * "no weather was recorded" is spelled.
     */
    public static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE `sessions` ADD COLUMN `weather_area_key` TEXT");
            database.execSQL("ALTER TABLE `sessions` ADD COLUMN `weather_condition` TEXT");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `weather_temperature_celsius` REAL NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `weather_observed_at_millis` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `weather_fetched_at_millis` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `sessions` ADD COLUMN `movement_state` TEXT");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `movement_stationary_millis` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `movement_moving_millis` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE `sessions` "
                    + "ADD COLUMN `interrupted` INTEGER NOT NULL DEFAULT 0");
            // The one fact version 4 did record: a session still carrying this pause reason was
            // interrupted, so the rows that can be labelled honestly are labelled.
            database.execSQL("UPDATE `sessions` SET `interrupted` = 1 "
                    + "WHERE `pause_reason` = 'PROCESS_INTERRUPTED'");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_sessions_finished_end_wall_clock_millis` "
                    + "ON `sessions` (`finished`, `end_wall_clock_millis`)");
        }
    };

    /**
     * Version 5 to 6: makes the indoor classification tri-state everywhere a place is stored, and
     * records which areas have been fetched.
     *
     * <p>{@code attractions.indoor} was an {@code INTEGER NOT NULL} boolean. SQLite cannot change a
     * column's type in place, so the table is rebuilt in the documented way — create, copy, drop,
     * rename — inside the transaction Room already wraps a migration in. Existing rows are all
     * hand-written seed content whose classification was deliberate, so {@code 1} becomes
     * {@code 'indoor'} and {@code 0} becomes {@code 'outdoor'}: no row is downgraded to unknown for
     * something it did in fact record.
     *
     * <p>The new {@code source_ref} column is left null for those rows, which is accurate — the
     * seed set has no provider reference to keep.
     *
     * <p>{@code attraction_areas} starts empty. That is the correct state and not a loss: it says
     * no area has been fetched yet, which was true of version 5 in any case, and the first query
     * after the upgrade fetches one.
     */
    public static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `attractions_new` ("
                    + "`id` TEXT NOT NULL, "
                    + "`name` TEXT, "
                    + "`category` TEXT, "
                    + "`description` TEXT, "
                    + "`indoor` TEXT, "
                    + "`latitude` REAL NOT NULL, "
                    + "`longitude` REAL NOT NULL, "
                    + "`provenance` TEXT, "
                    + "`fetched_at_millis` INTEGER NOT NULL, "
                    + "`source_ref` TEXT, "
                    + "PRIMARY KEY(`id`))");
            database.execSQL("INSERT INTO `attractions_new` ("
                    + "`id`, `name`, `category`, `description`, `indoor`, "
                    + "`latitude`, `longitude`, `provenance`, `fetched_at_millis`, `source_ref`) "
                    + "SELECT `id`, `name`, `category`, `description`, "
                    + "CASE `indoor` WHEN 1 THEN 'indoor' ELSE 'outdoor' END, "
                    + "`latitude`, `longitude`, `provenance`, `fetched_at_millis`, NULL "
                    + "FROM `attractions`");
            database.execSQL("DROP TABLE `attractions`");
            database.execSQL("ALTER TABLE `attractions_new` RENAME TO `attractions`");

            // The saved copy of a place is a self-contained snapshot, so it carries its own
            // classification and needs the same tri-state. Leaving it a boolean would mean saving
            // an unclassified place and finding it outdoors when it came back.
            database.execSQL("CREATE TABLE IF NOT EXISTS `saved_attractions_new` ("
                    + "`attraction_id` TEXT NOT NULL, "
                    + "`name` TEXT, "
                    + "`category` TEXT, "
                    + "`description` TEXT, "
                    + "`indoor` TEXT, "
                    + "`latitude` REAL NOT NULL, "
                    + "`longitude` REAL NOT NULL, "
                    + "`saved_at_millis` INTEGER NOT NULL, "
                    + "`provenance` TEXT, "
                    + "`source_ref` TEXT, "
                    + "PRIMARY KEY(`attraction_id`))");
            // Provenance joins the snapshot too. A saved place is shown from this row alone, and
            // a licence that requires its source to be credited does not stop requiring it because
            // the user pressed Save. Existing rows are back-filled to the seed marker, which is
            // what every one of them in fact is: there was no provider to save from before this.
            database.execSQL("INSERT INTO `saved_attractions_new` ("
                    + "`attraction_id`, `name`, `category`, `description`, `indoor`, "
                    + "`latitude`, `longitude`, `saved_at_millis`, `provenance`, `source_ref`) "
                    + "SELECT `attraction_id`, `name`, `category`, `description`, "
                    + "CASE `indoor` WHEN 1 THEN 'indoor' ELSE 'outdoor' END, "
                    + "`latitude`, `longitude`, `saved_at_millis`, 'seed', NULL "
                    + "FROM `saved_attractions`");
            database.execSQL("DROP TABLE `saved_attractions`");
            database.execSQL("ALTER TABLE `saved_attractions_new` RENAME TO `saved_attractions`");

            // And so does a session's destination snapshot, for the same reason: history has to
            // stay readable without the attraction cache, so it keeps its own copy of the place.
            database.execSQL("CREATE TABLE IF NOT EXISTS `sessions_new` ("
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
                    + "`destination_indoor` TEXT, "
                    + "`destination_latitude` REAL NOT NULL, "
                    + "`destination_longitude` REAL NOT NULL, "
                    + "`weather_area_key` TEXT, "
                    + "`weather_condition` TEXT, "
                    + "`weather_temperature_celsius` REAL NOT NULL, "
                    + "`weather_observed_at_millis` INTEGER NOT NULL, "
                    + "`weather_fetched_at_millis` INTEGER NOT NULL, "
                    + "`movement_state` TEXT, "
                    + "`movement_stationary_millis` INTEGER NOT NULL, "
                    + "`movement_moving_millis` INTEGER NOT NULL, "
                    + "`interrupted` INTEGER NOT NULL, "
                    + "`destination_provenance` TEXT, "
                    + "`destination_source_ref` TEXT, "
                    + "PRIMARY KEY(`id`))");
            database.execSQL("INSERT INTO `sessions_new` SELECT "
                    + "`id`, `state`, `pause_reason`, `start_wall_clock_millis`, "
                    + "`end_wall_clock_millis`, `active_duration_millis`, `distance_meters`, "
                    + "`segment`, `segment_count`, `finished`, "
                    + "`last_checkpoint_wall_clock_millis`, `destination_id`, `destination_name`, "
                    + "`destination_category`, `destination_description`, "
                    + "CASE `destination_indoor` WHEN 1 THEN 'indoor' ELSE 'outdoor' END, "
                    + "`destination_latitude`, `destination_longitude`, `weather_area_key`, "
                    + "`weather_condition`, `weather_temperature_celsius`, "
                    + "`weather_observed_at_millis`, `weather_fetched_at_millis`, "
                    + "`movement_state`, `movement_stationary_millis`, "
                    + "`movement_moving_millis`, `interrupted`, "
                    + "CASE WHEN `destination_id` IS NULL OR `destination_id` = '' "
                    + "THEN NULL ELSE 'seed' END, NULL FROM `sessions`");
            // Route points and visits reference sessions by a cascading foreign key. Room runs a
            // migration with foreign keys deferred, so dropping the old table here does not take
            // them with it, and the rename restores the target before the constraint is rechecked.
            database.execSQL("DROP TABLE `sessions`");
            database.execSQL("ALTER TABLE `sessions_new` RENAME TO `sessions`");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_finished` "
                    + "ON `sessions` (`finished`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_sessions_finished_end_wall_clock_millis` "
                    + "ON `sessions` (`finished`, `end_wall_clock_millis`)");

            database.execSQL("CREATE TABLE IF NOT EXISTS `attraction_areas` ("
                    + "`area_key` TEXT NOT NULL, "
                    + "`fetched_at_millis` INTEGER NOT NULL, "
                    + "`record_count` INTEGER NOT NULL, "
                    + "PRIMARY KEY(`area_key`))");
        }
    };

    /** Every migration, in order, for the builder and the migration test. */
    public static Migration[] all() {
        return new Migration[]{
                MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6};
    }
}
