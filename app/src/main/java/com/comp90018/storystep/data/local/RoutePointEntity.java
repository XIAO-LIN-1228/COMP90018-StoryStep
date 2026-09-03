package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room row for one stored point of a session's route.
 *
 * <p>The unique index on {@code (session_id, sequence)} is what makes appending idempotent: a
 * retry after a failed or half-applied write re-offers the same sequence numbers and is ignored
 * rather than duplicating the trace.
 *
 * <p>The foreign key cascades so deleting a session takes its own points with it and nothing else.
 * Saved attractions are not owned by a session and are unaffected.
 */
@Entity(
        tableName = "route_points",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"session_id", "sequence"}, unique = true),
                @Index("session_id")
        })
public class RoutePointEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    public long rowId;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    /** Position in the session's trace, starting at zero and never reused. */
    @ColumnInfo(name = "sequence")
    public int sequence;

    /** The collection span this point belongs to; points from different spans are never joined. */
    @ColumnInfo(name = "segment")
    public int segment;

    @ColumnInfo(name = "latitude")
    public double latitude;

    @ColumnInfo(name = "longitude")
    public double longitude;

    @ColumnInfo(name = "accuracy_meters")
    public float accuracyMeters;

    @ColumnInfo(name = "wall_clock_millis")
    public long wallClockMillis;
}
