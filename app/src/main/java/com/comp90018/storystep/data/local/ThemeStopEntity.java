package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.*;

@Entity(tableName = "theme_stops", primaryKeys = {"session_id", "stop_id"},
        foreignKeys = @ForeignKey(entity = ThemeRunEntity.class, parentColumns = "session_id",
                childColumns = "session_id", onDelete = ForeignKey.CASCADE))
public class ThemeStopEntity {
    @NonNull @ColumnInfo(name = "session_id") public String sessionId = "";
    @NonNull @ColumnInfo(name = "stop_id") public String stopId = "";
    @ColumnInfo(name = "skipped") public boolean skipped;
    @Nullable @ColumnInfo(name = "answer") public Integer answer;
}
