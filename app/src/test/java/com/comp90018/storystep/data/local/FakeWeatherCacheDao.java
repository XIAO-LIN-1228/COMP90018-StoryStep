package com.comp90018.storystep.data.local;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory {@link WeatherCacheDao} honouring the real table's primary key on {@code area_key}:
 * a second observation for the same area replaces the first rather than accumulating.
 *
 * <p>That replacement is what the repository's "one row per area" assumption rests on, so a fake
 * that appended would let a cache-growth bug pass the JVM tests and only show up on a device.
 */
public final class FakeWeatherCacheDao implements WeatherCacheDao {

    private final Map<String, WeatherCacheEntity> rows = new LinkedHashMap<>();

    /** Set to make reads throw, standing in for an unreadable cache. */
    public RuntimeException failReads;

    /** Set to make writes throw, standing in for a full or locked database. */
    public RuntimeException failWrites;

    /** How many times a row was written, so a test can prove a fresh read did not refetch. */
    public int upsertCount;

    @Override
    public WeatherCacheEntity findByArea(String areaKey) {
        if (failReads != null) {
            throw failReads;
        }
        return copy(rows.get(areaKey));
    }

    @Override
    public void upsert(WeatherCacheEntity weather) {
        if (failWrites != null) {
            throw failWrites;
        }
        upsertCount++;
        rows.put(weather.areaKey, copy(weather));
    }

    @Override
    public int deleteFetchedBefore(long cutoffMillis) {
        if (failWrites != null) {
            throw failWrites;
        }
        int removed = 0;
        Iterator<WeatherCacheEntity> iterator = rows.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().fetchedAtMillis < cutoffMillis) {
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }

    @Override
    public int deleteAll() {
        if (failWrites != null) {
            throw failWrites;
        }
        int removed = rows.size();
        rows.clear();
        return removed;
    }

    @Override
    public int count() {
        return rows.size();
    }

    /** Seeds a row directly, bypassing the write-failure switch. */
    public void put(WeatherCacheEntity entity) {
        rows.put(entity.areaKey, copy(entity));
    }

    /** Copied on the way in and out, so a caller cannot mutate a stored row by holding it. */
    private static WeatherCacheEntity copy(WeatherCacheEntity source) {
        if (source == null) {
            return null;
        }
        WeatherCacheEntity clone = new WeatherCacheEntity();
        clone.areaKey = source.areaKey;
        clone.condition = source.condition;
        clone.temperatureCelsius = source.temperatureCelsius;
        clone.observedAtMillis = source.observedAtMillis;
        clone.fetchedAtMillis = source.fetchedAtMillis;
        return clone;
    }
}
