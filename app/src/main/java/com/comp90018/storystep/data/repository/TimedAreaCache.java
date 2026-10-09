package com.comp90018.storystep.data.repository;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * A small in-memory cache of one value per weather area, each good for a fixed time.
 *
 * <p>Deliberately not Room: forecasts are only worth keeping for minutes, and losing them with the
 * process costs one request. That keeps the schema unchanged (decision D-08).
 *
 * @param <T> the cached value
 */
final class TimedAreaCache<T> {

    private static final class Entry<T> {
        final T value;
        final long storedAtMillis;

        Entry(T value, long storedAtMillis) {
            this.value = value;
            this.storedAtMillis = storedAtMillis;
        }
    }

    private final long lifetimeMillis;
    private final Map<String, Entry<T>> entries = new HashMap<>();

    TimedAreaCache(long lifetimeMillis) {
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
        this.lifetimeMillis = lifetimeMillis;
    }

    /**
     * The value stored for the area, or null when there is none or it is older than the
     * lifetime. A value stamped after {@code nowMillis} — the wall clock moved back — is treated as
     * expired rather than trusted for an unknown time.
     */
    @Nullable
    synchronized T get(String areaKey, long nowMillis) {
        Entry<T> entry = entries.get(areaKey);
        if (entry == null) {
            return null;
        }
        long age = nowMillis - entry.storedAtMillis;
        if (age < 0 || age > lifetimeMillis) {
            entries.remove(areaKey);
            return null;
        }
        return entry.value;
    }

    synchronized void put(String areaKey, T value, long nowMillis) {
        entries.put(areaKey, new Entry<>(value, nowMillis));
    }
}
