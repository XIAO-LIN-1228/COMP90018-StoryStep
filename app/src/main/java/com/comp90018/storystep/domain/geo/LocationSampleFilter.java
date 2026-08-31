package com.comp90018.storystep.domain.geo;

import com.comp90018.storystep.domain.model.LocationSample;

/**
 * Decides whether a location sample is good enough to drive user-visible state.
 *
 * <p>Stateful but plain Java: it remembers only the last accepted sample, so a whole synthetic
 * track can be replayed through it in a JVM test with a fake clock. Every rejection returns a typed
 * reason rather than a boolean, because the reasons mean different things to the user — a stale fix
 * is a "waiting" state, an implausible jump is not.
 *
 * <p>Not thread-safe; the coordinator that owns the location subscription owns one instance.
 */
public final class LocationSampleFilter {

    /** Why a sample was accepted or discarded. */
    public enum Decision {

        ACCEPTED,

        /** Accuracy radius exceeds the policy limit. */
        POOR_ACCURACY,

        /** Older than the policy's freshness window. */
        STALE,

        /** Not newer than the last accepted sample; a provider can deliver these out of order. */
        OUT_OF_ORDER,

        /** Movement is within receiver noise, so position is unchanged. */
        BELOW_MIN_DISPLACEMENT,

        /** The implied speed between samples is impossible for a walking user. */
        IMPLAUSIBLE_SPEED;

        public boolean isAccepted() {
            return this == ACCEPTED;
        }
    }

    private final LocationQualityPolicy policy;

    private LocationSample lastAccepted;

    public LocationSampleFilter(LocationQualityPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        this.policy = policy;
    }

    /**
     * Evaluates {@code candidate} and, when accepted, remembers it as the new reference.
     *
     * @param candidate           the sample just reported
     * @param nowMonotonicMillis  current monotonic time, from the same boot as the sample
     */
    public Decision offer(LocationSample candidate, long nowMonotonicMillis) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (candidate.getAccuracyMeters() > policy.getMaxAccuracyMeters()) {
            return Decision.POOR_ACCURACY;
        }
        if (candidate.ageMillis(nowMonotonicMillis) > policy.getMaxAgeMillis()) {
            return Decision.STALE;
        }

        if (lastAccepted == null) {
            // The first usable fix has nothing to compare against; displacement and speed rules
            // need two points, so applying them here would reject every session's opening sample.
            lastAccepted = candidate;
            return Decision.ACCEPTED;
        }

        long elapsedMillis = candidate.getMonotonicMillis() - lastAccepted.getMonotonicMillis();
        if (elapsedMillis <= 0L) {
            return Decision.OUT_OF_ORDER;
        }

        double displacement = GeoCalculator.distanceMeters(
                lastAccepted.getPosition(), candidate.getPosition());
        if (displacement > 0.0
                && displacement / (elapsedMillis / 1000.0) > policy.getMaxSpeedMetersPerSecond()) {
            return Decision.IMPLAUSIBLE_SPEED;
        }
        if (displacement < policy.getMinDisplacementMeters()) {
            // Deliberately does not update lastAccepted: otherwise a long series of sub-threshold
            // steps would creep the reference point and defeat the noise floor entirely.
            return Decision.BELOW_MIN_DISPLACEMENT;
        }

        lastAccepted = candidate;
        return Decision.ACCEPTED;
    }

    /** The most recent accepted sample, or null when none has been accepted yet. */
    public LocationSample getLastAccepted() {
        return lastAccepted;
    }

    /** Clears the reference point, for example when a paused session resumes a new segment. */
    public void reset() {
        lastAccepted = null;
    }
}