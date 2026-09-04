package com.comp90018.storystep.domain.arrival;

/**
 * Named thresholds for deciding that the user has arrived at the selected attraction.
 *
 * <p>Separate from {@link com.comp90018.storystep.domain.geo.LocationQualityPolicy} because the two
 * answer different questions. That one asks whether a fix is good enough to move a distance
 * readout; this one asks whether a sequence of fixes is good enough to make a claim the app then
 * writes into the user's history and cannot take back. The accuracy bar here is deliberately
 * stricter, and the rules are about a sustained sequence rather than a single sample.
 *
 * <p>Conservative starting values from the project plan, to be tuned against recorded approaches.
 * They trade missed triggers for never announcing an arrival that did not happen.
 */
public final class ArrivalPolicy {

    /**
     * The user is treated as inside when {@code distance + accuracy} is within this.
     *
     * <p>Adding the accuracy radius rather than comparing distance alone is what stops a fix that
     * merely <em>might</em> be at the attraction from counting: a 40 m reading with a 40 m error
     * circle says almost nothing about where the user is.
     */
    private final double maxDistancePlusAccuracyMeters;

    /** A fix less accurate than this cannot contribute to an arrival at all. */
    private final double maxAccuracyMeters;

    /** How many accepted fixes an arrival needs. One lucky fix is never enough. */
    private final int minAcceptedFixes;

    /** How long the accepted fixes must span, so passing by at walking pace does not qualify. */
    private final long minDwellMillis;

    /** A silence longer than this means the candidate describes an approach that is over. */
    private final long maxGapMillis;

    /** A fix older than this describes where the user was, not where they are. */
    private final long maxSampleAgeMillis;

    public ArrivalPolicy(double maxDistancePlusAccuracyMeters,
                         double maxAccuracyMeters,
                         int minAcceptedFixes,
                         long minDwellMillis,
                         long maxGapMillis,
                         long maxSampleAgeMillis) {
        if (maxDistancePlusAccuracyMeters <= 0.0) {
            throw new IllegalArgumentException("maxDistancePlusAccuracyMeters must be positive");
        }
        if (maxAccuracyMeters <= 0.0) {
            throw new IllegalArgumentException("maxAccuracyMeters must be positive");
        }
        if (minAcceptedFixes < 2) {
            throw new IllegalArgumentException(
                    "minAcceptedFixes must be at least 2; one fix cannot describe a stay");
        }
        if (minDwellMillis <= 0L) {
            throw new IllegalArgumentException("minDwellMillis must be positive");
        }
        if (maxGapMillis <= 0L) {
            throw new IllegalArgumentException("maxGapMillis must be positive");
        }
        if (maxSampleAgeMillis <= 0L) {
            throw new IllegalArgumentException("maxSampleAgeMillis must be positive");
        }
        this.maxDistancePlusAccuracyMeters = maxDistancePlusAccuracyMeters;
        this.maxAccuracyMeters = maxAccuracyMeters;
        this.minAcceptedFixes = minAcceptedFixes;
        this.minDwellMillis = minDwellMillis;
        this.maxGapMillis = maxGapMillis;
        this.maxSampleAgeMillis = maxSampleAgeMillis;
    }

    /**
     * The project's proposed starting values: 50 m for distance plus accuracy, 25 m accuracy,
     * 3 fixes spanning 10 s, resetting after a 15 s gap, ignoring fixes older than 15 s.
     */
    public static ArrivalPolicy defaultPolicy() {
        return new ArrivalPolicy(50.0, 25.0, 3, 10_000L, 15_000L, 15_000L);
    }

    public double getMaxDistancePlusAccuracyMeters() {
        return maxDistancePlusAccuracyMeters;
    }

    public double getMaxAccuracyMeters() {
        return maxAccuracyMeters;
    }

    public int getMinAcceptedFixes() {
        return minAcceptedFixes;
    }

    public long getMinDwellMillis() {
        return minDwellMillis;
    }

    public long getMaxGapMillis() {
        return maxGapMillis;
    }

    public long getMaxSampleAgeMillis() {
        return maxSampleAgeMillis;
    }
}
