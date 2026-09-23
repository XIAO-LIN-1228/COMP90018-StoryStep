package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;

/**
 * One row of the history list: a finished session's totals plus how many places it visited.
 *
 * <p>A projection rather than {@link SessionEntity} because the list needs a visit count, which
 * lives in another table, and does not need the route or the destination snapshot. Counting in SQL
 * keeps the list one query no matter how much history the user has accumulated; reading whole
 * sessions and then counting each one's visits would be a query per row.
 */
public class SessionHistoryRow {

    @ColumnInfo(name = "id")
    public String id;

    @ColumnInfo(name = "startWallClockMillis")
    public long startWallClockMillis;

    @ColumnInfo(name = "endWallClockMillis")
    public long endWallClockMillis;

    @ColumnInfo(name = "activeDurationMillis")
    public long activeDurationMillis;

    @ColumnInfo(name = "distanceMeters")
    public double distanceMeters;

    @ColumnInfo(name = "segmentCount")
    public int segmentCount;

    @ColumnInfo(name = "interrupted")
    public boolean interrupted;

    @Nullable
    @ColumnInfo(name = "destinationName")
    public String destinationName;

    @ColumnInfo(name = "visitCount")
    public int visitCount;

    @Nullable
    @ColumnInfo(name = "title")
    public String title;

    @Nullable
    @ColumnInfo(name = "placeName")
    public String placeName;

    @Nullable
    @ColumnInfo(name = "photoFiles")
    public String photoFiles;

    @Nullable
    @ColumnInfo(name = "steps")
    public Long steps;

    @Nullable
    @ColumnInfo(name = "ascentMeters")
    public Double ascentMeters;
}
