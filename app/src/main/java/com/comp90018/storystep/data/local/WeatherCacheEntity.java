package com.comp90018.storystep.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room row for the last weather observation fetched for one area.
 *
 * <p>The area key is the primary key, so each grid cell keeps exactly one row and a refresh
 * replaces it rather than accumulating history. Weather is not a record the user owns — nobody
 * wants to browse yesterday's temperature — so unlike sessions and visits there is nothing here to
 * preserve, and no foreign key onto anything.
 *
 * <p>This table exists for one reason: a warm offline launch should still be able to say what the
 * weather was, labelled with its age, instead of showing nothing.
 */
@Entity(tableName = "weather_cache")
public class WeatherCacheEntity {

    /** The {@code WeatherAreaBucket} key. Never a precise coordinate. */
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "area_key")
    public String areaKey = "";

    /** {@code WeatherCondition} name; see {@link LocalWeatherMapper} for the unknown handling. */
    @ColumnInfo(name = "condition")
    public String condition;

    @ColumnInfo(name = "temperature_celsius")
    public double temperatureCelsius;

    /** When the provider observed it, in epoch milliseconds. Shown to the user. */
    @ColumnInfo(name = "observed_at_millis")
    public long observedAtMillis;

    /** When this app fetched it. Freshness is measured from here. */
    @ColumnInfo(name = "fetched_at_millis")
    public long fetchedAtMillis;
}
