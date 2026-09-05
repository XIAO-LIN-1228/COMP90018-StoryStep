package com.comp90018.storystep.domain.weather;

import com.comp90018.storystep.domain.model.WeatherSnapshot;

import java.util.Objects;

/**
 * A weather observation together with how much it can be trusted.
 *
 * <p>The pair travels together because neither half is safe alone. A snapshot without its
 * freshness invites a screen to present an hour-old temperature as current; a freshness without
 * its snapshot says nothing. Ranking asks {@link WeatherFreshness#isUsableAsContext()} and the UI
 * asks for a label, from the same object.
 */
public final class WeatherResult {

    private final WeatherSnapshot snapshot;
    private final WeatherFreshness freshness;

    public WeatherResult(WeatherSnapshot snapshot, WeatherFreshness freshness) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.freshness = Objects.requireNonNull(freshness, "freshness");
        if (freshness == WeatherFreshness.EXPIRED) {
            // An expired observation is not a result. Producing one would let a caller display
            // something the policy has already ruled unfit to show.
            throw new IllegalArgumentException("an expired observation is not a result");
        }
    }

    public WeatherSnapshot getSnapshot() {
        return snapshot;
    }

    public WeatherFreshness getFreshness() {
        return freshness;
    }

    /** True when this came off the network during the call that produced it. */
    public boolean isLive() {
        return snapshot.getProvenance() == WeatherSnapshot.Provenance.NETWORK;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WeatherResult)) {
            return false;
        }
        WeatherResult that = (WeatherResult) other;
        return freshness == that.freshness && snapshot.equals(that.snapshot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshot, freshness);
    }

    @Override
    public String toString() {
        return "WeatherResult{" + snapshot + ", " + freshness + '}';
    }
}
