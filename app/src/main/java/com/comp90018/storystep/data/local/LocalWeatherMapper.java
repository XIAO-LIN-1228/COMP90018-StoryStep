package com.comp90018.storystep.data.local;

import androidx.annotation.Nullable;

import com.comp90018.storystep.domain.model.WeatherCondition;
import com.comp90018.storystep.domain.model.WeatherSnapshot;

/**
 * Converts between {@link WeatherCacheEntity} rows and domain {@link WeatherSnapshot} objects.
 *
 * <p>A row read back is always {@link WeatherSnapshot.Provenance#CACHE}, whatever it was when it
 * was written. That is the point of the field: a screen must be able to tell an answer that came
 * off the network during this call from one that came off the disk.
 */
public final class LocalWeatherMapper {

    private LocalWeatherMapper() {
        // Utility class.
    }

    public static WeatherCacheEntity toEntity(WeatherSnapshot snapshot) {
        WeatherCacheEntity entity = new WeatherCacheEntity();
        entity.areaKey = snapshot.getAreaKey();
        entity.condition = snapshot.getCondition().name();
        entity.temperatureCelsius = snapshot.getTemperatureCelsius();
        entity.observedAtMillis = snapshot.getObservedAtMillis();
        entity.fetchedAtMillis = snapshot.getFetchedAtMillis();
        return entity;
    }

    /**
     * @return the snapshot, or {@code null} when the row cannot produce a valid one — a row
     *         written by an older version, or one whose numbers a schema change left unusable.
     *         A corrupt cache row degrades to "no cached weather", never to a crash on launch.
     */
    @Nullable
    public static WeatherSnapshot toDomain(@Nullable WeatherCacheEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            return new WeatherSnapshot(
                    entity.areaKey,
                    parseCondition(entity.condition),
                    entity.temperatureCelsius,
                    entity.observedAtMillis,
                    entity.fetchedAtMillis,
                    WeatherSnapshot.Provenance.CACHE);
        } catch (IllegalArgumentException unusable) {
            return null;
        }
    }

    /** An unrecognised stored name reads back as unknown, which is a category the app handles. */
    private static WeatherCondition parseCondition(@Nullable String stored) {
        if (stored == null) {
            return WeatherCondition.UNKNOWN;
        }
        try {
            return WeatherCondition.valueOf(stored);
        } catch (IllegalArgumentException unrecognised) {
            return WeatherCondition.UNKNOWN;
        }
    }
}
