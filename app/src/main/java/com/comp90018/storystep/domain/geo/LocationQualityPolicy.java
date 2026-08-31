package com.comp90018.storystep.domain.geo;

/**
 * Named thresholds for accepting a location sample.
 *
 * <p>These exist so the numbers live in one reviewable place instead of being duplicated across
 * screens. They are conservative starting values to be tuned with recorded evidence, not release
 * guarantees: they deliberately trade missed updates for never claiming a position the device did
 * not actually observe well enough.
 */
public final class LocationQualityPolicy {

    /** Samples less accurate than this are unusable for distance and arrival work. */
    private final double maxAccuracyMeters;

    /** A sample older than this cannot represent where the user is now. */
    private final long maxAgeMillis;

    /**
     * Movement below this is treated as receiver noise rather than travel, so a stationary device
     * does not inflate an accumulated route.
     */
    private final double minDisplacementMeters;

    /**
     * Implied speed above this means the two samples cannot both be right; the newer one is
     * rejected rather than allowed to teleport the user.
     */
    private final double maxSpeedMetersPerSecond;

    public LocationQualityPolicy(double maxAccuracyMeters,
                                 long maxAgeMillis,
                                 double minDisplacementMeters,
                                 double maxSpeedMetersPerSecond) {
        if (maxAccuracyMeters <= 0.0) {
            throw new IllegalArgumentException("maxAccuracyMeters must be positive");
        }
        if (maxAgeMillis <= 0L) {
            throw new IllegalArgumentException("maxAgeMillis must be positive");
        }
        if (minDisplacementMeters < 0.0) {
            throw new IllegalArgumentException("minDisplacementMeters must not be negative");
        }
        if (maxSpeedMetersPerSecond <= 0.0) {
            throw new IllegalArgumentException("maxSpeedMetersPerSecond must be positive");
        }
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.maxAgeMillis = maxAgeMillis;
        this.minDisplacementMeters = minDisplacementMeters;
        this.maxSpeedMetersPerSecond = maxSpeedMetersPerSecond;
    }

    /**
     * The project's proposed starting values: 30 m accuracy, 15 s freshness, 3 m minimum
     * displacement and 8 m/s maximum implied speed.
     */
    public static LocationQualityPolicy defaultPolicy() {
        return new LocationQualityPolicy(30.0, 15_000L, 3.0, 8.0);
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public long getMaxAgeMillis() {
        return maxAgeMillis;
    }

    public double getMinDisplacementMeters() {
        return minDisplacementMeters;
    }

    public double getMaxSpeedMetersPerSecond() {
        return maxSpeedMetersPerSecond;
    }
}