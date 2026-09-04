package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Room row for one recorded arrival.
 *
 * <p>The unique {@code (session_id, attraction_id)} index is the exactly-once guarantee REQ-13
 * relies on: re-entering the same target within one session cannot create a second visit, whatever
 * the arrival detector does upstream.
 *
 * <p>The attraction name and story are copied in rather than referenced, for the same reason the
 * session snapshots its destination — history must stay readable after the cache moves on.
 *
 * <p>Written by the arrival path through {@code RoomSessionStore}. The table shipped with the rest
 * of the schema before anything wrote to it, so that the migration adding it was shipped and tested
 * once rather than split across two releases.
 */
@Entity(
        tableName = "visits",
        foreignKeys = @ForeignKey(
                entity = SessionEntity.class,
                parentColumns = "id",
                childColumns = "session_id",
                onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"session_id", "attraction_id"}, unique = true),
                @Index("session_id")
        })
public class VisitEntity {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "row_id")
    public long rowId;

    @NonNull
    @ColumnInfo(name = "session_id")
    public String sessionId = "";

    @NonNull
    @ColumnInfo(name = "attraction_id")
    public String attractionId = "";

    @ColumnInfo(name = "arrival_wall_clock_millis")
    public long arrivalWallClockMillis;

    @Nullable
    @ColumnInfo(name = "attraction_name")
    public String attractionName;

    @Nullable
    @ColumnInfo(name = "story")
    public String story;

    /** True until the user has actually read the revealed story. */
    @ColumnInfo(name = "unread")
    public boolean unread;
}
