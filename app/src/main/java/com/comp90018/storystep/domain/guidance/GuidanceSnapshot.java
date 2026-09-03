package com.comp90018.storystep.domain.guidance;

/**
 * Where the destination lies, and — when a compass is available — which way to turn.
 *
 * <p>The two are separate on purpose. The bearing needs only a position, so it survives a missing
 * or unreliable magnetometer; the relative turn needs a heading and does not. A screen that treated
 * them as one value would hide useful direction information from every user without a compass.
 *
 * <p>This describes a straight line to the destination. It is not a walking route and must never be
 * presented as one: the line may cross a river, a rail corridor or a building.
 */
public final class GuidanceSnapshot {

    private static final GuidanceSnapshot UNAVAILABLE = new GuidanceSnapshot(null, null, null);

    private final Double targetBearingDegrees;
    private final Double relativeBearingDegrees;
    private final CardinalDirection cardinal;

    private GuidanceSnapshot(Double targetBearingDegrees,
                             Double relativeBearingDegrees,
                             CardinalDirection cardinal) {
        this.targetBearingDegrees = targetBearingDegrees;
        this.relativeBearingDegrees = relativeBearingDegrees;
        this.cardinal = cardinal;
    }

    /** No position, or no destination: nothing truthful can be said about direction. */
    public static GuidanceSnapshot unavailable() {
        return UNAVAILABLE;
    }

    /** A known bearing to the destination, without a device heading to relate it to. */
    public static GuidanceSnapshot bearingOnly(double targetBearingDegrees) {
        return new GuidanceSnapshot(targetBearingDegrees, null,
                CardinalDirection.fromBearing(targetBearingDegrees));
    }

    /** A known bearing and a usable heading: the arrow can be pointed. */
    public static GuidanceSnapshot withHeading(double targetBearingDegrees,
                                               double relativeBearingDegrees) {
        return new GuidanceSnapshot(targetBearingDegrees, relativeBearingDegrees,
                CardinalDirection.fromBearing(targetBearingDegrees));
    }

    public boolean hasBearing() {
        return targetBearingDegrees != null;
    }

    /** Degrees clockwise from true north to the destination. Check {@link #hasBearing()} first. */
    public double getTargetBearingDegrees() {
        return targetBearingDegrees == null ? 0.0 : targetBearingDegrees;
    }

    /** True only when an arrow may be drawn. */
    public boolean hasRelativeBearing() {
        return relativeBearingDegrees != null;
    }

    /**
     * Degrees to turn, within {@code (-180, 180]}; positive is clockwise. Check
     * {@link #hasRelativeBearing()} first — zero here means "straight ahead", not "unknown".
     */
    public double getRelativeBearingDegrees() {
        return relativeBearingDegrees == null ? 0.0 : relativeBearingDegrees;
    }

    /** The eight-point label for the destination's bearing, or null when there is no bearing. */
    public CardinalDirection getCardinal() {
        return cardinal;
    }
}
