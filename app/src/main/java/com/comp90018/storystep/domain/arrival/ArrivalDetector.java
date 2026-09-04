package com.comp90018.storystep.domain.arrival;

import com.comp90018.storystep.domain.geo.GeoCalculator;
import com.comp90018.storystep.domain.model.Attraction;
import com.comp90018.storystep.domain.model.LocationSample;

/**
 * Decides when a run of location fixes amounts to arriving at the selected attraction.
 *
 * <p>Stateful but plain Java, like {@link com.comp90018.storystep.domain.geo.LocationSampleFilter}:
 * it remembers only the current candidate, so a whole synthetic approach can be replayed through it
 * in a JVM test with a fake clock. Not thread-safe; the coordinator that owns the location
 * subscription owns one instance.
 *
 * <p>It is fed the <em>raw</em> fix, before the route's sample filter sees it. That filter drops
 * movement under its minimum displacement as receiver noise, which is exactly what standing at the
 * destination looks like — a detector placed behind it would stop counting at the moment the user
 * actually arrives. The stricter accuracy bar here is what keeps that safe.
 *
 * <p>Reporting an arrival is not the same as recording one. This class says "the approach looks
 * real"; whether a visit exists is decided by the unique session/attraction row in storage, which
 * stays authoritative however often this fires.
 */
public final class ArrivalDetector {

    /** What one offered sample meant for the current candidate. */
    public enum Decision {

        /** No attraction is selected, so there is nothing to arrive at. */
        NO_TARGET,

        /** The fix's error circle is too wide to place the user near anything. */
        POOR_ACCURACY,

        /** The fix describes where the user was, not where they are. */
        STALE,

        /** Not newer than the last accepted fix; providers do deliver these. */
        OUT_OF_ORDER,

        /** Credible, but not close enough to the target. */
        OUTSIDE,

        /** Inside and counted, but the stay is not yet long or dense enough. */
        WAITING,

        /** The stay qualifies. Reported once per target until the candidate is reset. */
        ARRIVED;

        public boolean isArrival() {
            return this == ARRIVED;
        }
    }

    private final ArrivalPolicy policy;

    private Attraction target;

    /** Monotonic time of the first and most recent accepted fix of the current candidate. */
    private long firstAcceptedMonotonicMillis;
    private long lastAcceptedMonotonicMillis;
    private int acceptedCount;

    /** Highest monotonic timestamp seen at all, so a repeated callback cannot be counted twice. */
    private long lastOfferedMonotonicMillis;
    private boolean hasOffered;

    /** True once this target's arrival has been reported, so it is announced once, not per fix. */
    private boolean reported;

    public ArrivalDetector(ArrivalPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
    }

    /**
     * Sets the attraction the user is heading towards, clearing any candidate.
     *
     * <p>A partially satisfied stay near the previous target says nothing about the new one, and
     * carrying it over would let switching destinations manufacture an instant arrival.
     *
     * @param target the chosen attraction, or null when none is selected
     */
    public void setTarget(Attraction target) {
        this.target = target;
        reset();
    }

    /** The attraction currently being watched, or null when none is selected. */
    public Attraction getTarget() {
        return target;
    }

    /**
     * Offers one fix and reports what it meant.
     *
     * @param sample             the fix just reported, before route-quality filtering
     * @param nowMonotonicMillis current monotonic time, from the same boot as the sample
     */
    public Decision offer(LocationSample sample, long nowMonotonicMillis) {
        if (sample == null) {
            throw new IllegalArgumentException("sample must not be null");
        }
        if (target == null) {
            return Decision.NO_TARGET;
        }

        long sampleMonotonicMillis = sample.getMonotonicMillis();
        if (hasOffered && sampleMonotonicMillis <= lastOfferedMonotonicMillis) {
            // A duplicate or late delivery adds no information. It must not count towards the fix
            // total, and it equally must not throw away a stay the user has genuinely accumulated.
            return Decision.OUT_OF_ORDER;
        }
        hasOffered = true;
        lastOfferedMonotonicMillis = sampleMonotonicMillis;

        if (sample.ageMillis(nowMonotonicMillis) > policy.getMaxSampleAgeMillis()) {
            clearCandidate();
            return Decision.STALE;
        }
        if (sample.getAccuracyMeters() > policy.getMaxAccuracyMeters()) {
            clearCandidate();
            return Decision.POOR_ACCURACY;
        }

        double distanceMeters =
                GeoCalculator.distanceMeters(sample.getPosition(), target.getLocation());
        if (distanceMeters + sample.getAccuracyMeters() > policy.getMaxDistancePlusAccuracyMeters()) {
            clearCandidate();
            return Decision.OUTSIDE;
        }

        if (acceptedCount > 0
                && sampleMonotonicMillis - lastAcceptedMonotonicMillis > policy.getMaxGapMillis()) {
            // The user was inside, went untracked for longer than the app can account for, and is
            // inside again. That is a new approach, not a continuous stay.
            clearCandidate();
        }

        if (acceptedCount == 0) {
            firstAcceptedMonotonicMillis = sampleMonotonicMillis;
        }
        lastAcceptedMonotonicMillis = sampleMonotonicMillis;
        acceptedCount++;

        if (reported) {
            // Still inside after the arrival was announced. Keep the candidate alive so leaving and
            // returning is distinguishable, but do not announce it again.
            return Decision.WAITING;
        }
        if (acceptedCount >= policy.getMinAcceptedFixes()
                && lastAcceptedMonotonicMillis - firstAcceptedMonotonicMillis
                        >= policy.getMinDwellMillis()) {
            reported = true;
            return Decision.ARRIVED;
        }
        return Decision.WAITING;
    }

    /**
     * Clears the candidate and lets the current target be reported again.
     *
     * <p>Called when collection restarts on a new segment: readings from before a pause describe a
     * different walk, and the app was not watching in between.
     */
    public void reset() {
        clearCandidate();
        hasOffered = false;
        lastOfferedMonotonicMillis = 0L;
        reported = false;
    }

    private void clearCandidate() {
        acceptedCount = 0;
        firstAcceptedMonotonicMillis = 0L;
        lastAcceptedMonotonicMillis = 0L;
    }
}
