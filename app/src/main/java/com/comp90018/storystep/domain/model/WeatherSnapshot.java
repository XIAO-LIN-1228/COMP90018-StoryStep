package com.comp90018.storystep.domain.model;

import java.util.Objects;

/**
 * One weather observation, as this app records it.
 *
 * <p>Immutable and self-describing: it carries not only what the weather was, but when it was
 * observed, when this app fetched it and which area it describes. A screen cannot present a
 * snapshot honestly without those three, and a stored snapshot cannot be judged stale without
 * them either.
 *
 * <p>The temperature unit is fixed at Celsius here rather than carried as a field. Providers
 * report in whatever unit they are asked for; converting once at the mapper boundary means no
 * downstream code has to ask what unit it is holding.
 */
public final class WeatherSnapshot {

    /** Where this snapshot came from. Distinguishes a live answer from a re-read stored one. */
    public enum Provenance {

        /** Fetched from the weather service during this call. */
        NETWORK,

        /** Read back from the local cache. May still be fresh; freshness is a separate question. */
        CACHE
    }

    private final String areaKey;
    private final WeatherCondition condition;
    private final double temperatureCelsius;
    private final long observedAtMillis;
    private final long fetchedAtMillis;
    private final Provenance provenance;

    public WeatherSnapshot(String areaKey,
                           WeatherCondition condition,
                           double temperatureCelsius,
                           long observedAtMillis,
                           long fetchedAtMillis,
                           Provenance provenance) {
        this.areaKey = Objects.requireNonNull(areaKey, "areaKey");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        // A non-finite temperature would survive every later comparison and render as "NaN°C".
        // Rejecting it here keeps a malformed provider payload from reaching a screen at all.
        if (Double.isNaN(temperatureCelsius) || Double.isInfinite(temperatureCelsius)) {
            throw new IllegalArgumentException(
                    "temperatureCelsius must be finite: " + temperatureCelsius);
        }
        if (observedAtMillis <= 0L) {
            throw new IllegalArgumentException(
                    "observedAtMillis must be a real instant: " + observedAtMillis);
        }
        if (fetchedAtMillis <= 0L) {
            throw new IllegalArgumentException(
                    "fetchedAtMillis must be a real instant: " + fetchedAtMillis);
        }
        this.temperatureCelsius = temperatureCelsius;
        this.observedAtMillis = observedAtMillis;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    /** The {@code WeatherAreaBucket} key this observation describes, never a precise position. */
    public String getAreaKey() {
        return areaKey;
    }

    public WeatherCondition getCondition() {
        return condition;
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    /** When the provider says the weather was observed, in epoch milliseconds. */
    public long getObservedAtMillis() {
        return observedAtMillis;
    }

    /** When this app received it. Freshness is measured from here, not from the observation. */
    public long getFetchedAtMillis() {
        return fetchedAtMillis;
    }

    public Provenance getProvenance() {
        return provenance;
    }

    /** The same observation, relabelled as having been read from the cache. */
    public WeatherSnapshot asCached() {
        if (provenance == Provenance.CACHE) {
            return this;
        }
        return new WeatherSnapshot(areaKey, condition, temperatureCelsius, observedAtMillis,
                fetchedAtMillis, Provenance.CACHE);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WeatherSnapshot)) {
            return false;
        }
        WeatherSnapshot that = (WeatherSnapshot) other;
        return Double.compare(temperatureCelsius, that.temperatureCelsius) == 0
                && observedAtMillis == that.observedAtMillis
                && fetchedAtMillis == that.fetchedAtMillis
                && areaKey.equals(that.areaKey)
                && condition == that.condition
                && provenance == that.provenance;
    }

    @Override
    public int hashCode() {
        return Objects.hash(areaKey, condition, temperatureCelsius, observedAtMillis,
                fetchedAtMillis, provenance);
    }

    @Override
    public String toString() {
        return "WeatherSnapshot{" + areaKey + ", " + condition + ", "
                + temperatureCelsius + "C, " + provenance + '}';
    }
}
