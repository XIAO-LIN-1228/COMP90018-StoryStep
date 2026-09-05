package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room row for one exploration session.
 *
 * <p>The destination is stored as a flattened snapshot rather than a foreign key into
 * {@code attractions}: the cache is refreshed and evicted on its own schedule, and a session that
 * could no longer name the place it was heading towards would have lost user-owned history to a
 * cache decision.
 *
 * <p>{@code finished} is indexed and stored explicitly instead of being derived from
 * {@code state}, because "is there an unfinished session?" is asked on every launch and is the
 * query that enforces the at-most-one rule. The composite index over {@code finished} and the end
 * time serves the history list, which is the one query whose cost grows with how much the user has
 * explored; the single-column index stays because {@code findUnfinished} still uses it.
 */
@Entity(tableName = "sessions",
        indices = {@Index("finished"), @Index({"finished", "end_wall_clock_millis"})})
public class SessionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    public String id = "";

    /** {@link com.comp90018.storystep.domain.model.SessionState} name. */
    @ColumnInfo(name = "state")
    public String state;

    /** {@link com.comp90018.storystep.domain.model.PauseReason} name, or null when collecting. */
    @Nullable
    @ColumnInfo(name = "pause_reason")
    public String pauseReason;

    @ColumnInfo(name = "start_wall_clock_millis")
    public long startWallClockMillis;

    /** Zero while the session is unfinished. */
    @ColumnInfo(name = "end_wall_clock_millis")
    public long endWallClockMillis;

    /** Collecting time only; paused and process-dead spans are never included. */
    @ColumnInfo(name = "active_duration_millis")
    public long activeDurationMillis;

    @ColumnInfo(name = "distance_meters")
    public double distanceMeters;

    /** The session's current collection span; one more than the number of resumes. */
    @ColumnInfo(name = "segment")
    public int segment;

    /** How many spans actually contributed route points. */
    @ColumnInfo(name = "segment_count")
    public int segmentCount;

    /** True once the session has been permanently ended. Drives the at-most-one-unfinished rule. */
    @ColumnInfo(name = "finished")
    public boolean finished;

    @ColumnInfo(name = "last_checkpoint_wall_clock_millis")
    public long lastCheckpointWallClockMillis;

    @Nullable
    @ColumnInfo(name = "destination_id")
    public String destinationId;

    @Nullable
    @ColumnInfo(name = "destination_name")
    public String destinationName;

    /** Stable lower-case category code; see {@link com.comp90018.storystep.data.AttractionCategoryCodec}. */
    @Nullable
    @ColumnInfo(name = "destination_category")
    public String destinationCategory;

    @Nullable
    @ColumnInfo(name = "destination_description")
    public String destinationDescription;

    @ColumnInfo(name = "destination_indoor")
    public boolean destinationIndoor;

    @ColumnInfo(name = "destination_latitude")
    public double destinationLatitude;

    @ColumnInfo(name = "destination_longitude")
    public double destinationLongitude;

    // ---- Recorded context -------------------------------------------------------------------
    //
    // Weather and movement are copied into the session rather than looked up when history is
    // read. The weather cache is keyed by area and refreshed on its own schedule, and the motion
    // estimate exists only while sensors are registered: neither could answer "what was it like
    // during that walk?" after the fact.

    /**
     * The area the recorded observation describes, or null when no weather was ever recorded.
     *
     * <p>This one nullable column is the discriminator for the whole weather group. The numeric
     * columns below stay primitive, so a row that predates version 5 reads as zeros and is only
     * ever interpreted through this field being null.
     */
    @Nullable
    @ColumnInfo(name = "weather_area_key")
    public String weatherAreaKey;

    /** {@link com.comp90018.storystep.domain.model.WeatherCondition} name. */
    @Nullable
    @ColumnInfo(name = "weather_condition")
    public String weatherCondition;

    @ColumnInfo(name = "weather_temperature_celsius")
    public double weatherTemperatureCelsius;

    @ColumnInfo(name = "weather_observed_at_millis")
    public long weatherObservedAtMillis;

    @ColumnInfo(name = "weather_fetched_at_millis")
    public long weatherFetchedAtMillis;

    /**
     * {@link com.comp90018.storystep.domain.model.MovementState} name, or null when written before
     * version 5.
     *
     * <p>{@code UNAVAILABLE} is a recorded fact, not a missing value: it means the device had no
     * usable motion sensor, which is different from a walk that was genuinely spent standing
     * still. History has to be able to say which one happened.
     */
    @Nullable
    @ColumnInfo(name = "movement_state")
    public String movementState;

    @ColumnInfo(name = "movement_stationary_millis")
    public long movementStationaryMillis;

    @ColumnInfo(name = "movement_moving_millis")
    public long movementMovingMillis;

    /**
     * True once the session has been interrupted by a process death at any point.
     *
     * <p>Stored rather than derived because {@code finish} clears {@code pause_reason}: without
     * this column a finished session keeps no evidence that it was ever interrupted, and the
     * history label the user needs would be unrecoverable. Sticky by design — it is set when the
     * interruption is observed and never cleared.
     */
    @ColumnInfo(name = "interrupted")
    public boolean interrupted;
}
