package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.*;

@Entity(tableName = "theme_runs", foreignKeys = @ForeignKey(entity = SessionEntity.class,
        parentColumns = "id", childColumns = "session_id", onDelete = ForeignKey.CASCADE))
public class ThemeRunEntity {
    @PrimaryKey @NonNull @ColumnInfo(name = "session_id") public String sessionId = "";
    @NonNull @ColumnInfo(name = "definition_json") public String definitionJson = "";
}
