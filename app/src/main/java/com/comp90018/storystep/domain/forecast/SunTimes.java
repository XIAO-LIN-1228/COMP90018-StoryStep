package com.comp90018.storystep.domain.forecast;

import java.util.Arrays;
import java.util.Objects;

/**
 * Sunrise and sunset for each day a forecast covers, at the forecast's location.
 *
 * <p>Day and night are decided from these instants rather than from an hourly day/night flag,
 * so an hour or a walk is judged by when the sun actually rises and sets (decision D-08). An
 * instant outside every covered day is unknown.
 */
public final class SunTimes {

    /** No sunrise or sunset known: every question about daylight is unknown. */
    public static final SunTimes UNKNOWN = new SunTimes(new long[0], new long[0], false, 0, 0);

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final long[] sunriseMillis;
    private final long[] sunsetMillis;
    private final boolean known;
    /** The span the provider's days cover, so a polar night with no daylight is still known. */
    private final long coveredFrom;
    private final long coveredTo;

    private SunTimes(long[] sunriseMillis, long[] sunsetMillis, boolean known, long coveredFrom,
                     long coveredTo) {
        this.sunriseMillis = sunriseMillis;
        this.sunsetMillis = sunsetMillis;
        this.known = known;
        this.coveredFrom = coveredFrom;
        this.coveredTo = coveredTo;
    }

    /**
     * @param sunriseEpochSeconds one sunrise per day, in order
     * @param sunsetEpochSeconds  the same day's sunset, not before its sunrise
     *
     * <p>Polar days and nights are accepted: a day whose sunrise equals its sunset (polar night)
     * has no daylight, and days that touch (polar day: sunset at midnight, next sunrise at the
     * same midnight) are merged into one stretch of daylight.
     */
    public static SunTimes of(long[] sunriseEpochSeconds, long[] sunsetEpochSeconds) {
        Objects.requireNonNull(sunriseEpochSeconds, "sunrise");
        Objects.requireNonNull(sunsetEpochSeconds, "sunset");
        if (sunriseEpochSeconds.length != sunsetEpochSeconds.length) {
            throw new IllegalArgumentException("sunrise and sunset counts differ");
        }
        java.util.List<long[]> spans = new java.util.ArrayList<>();
        for (int i = 0; i < sunriseEpochSeconds.length; i++) {
            long rise = sunriseEpochSeconds[i] * 1000L;
            long set = sunsetEpochSeconds[i] * 1000L;
            if (set < rise) {
                throw new IllegalArgumentException("sunset before sunrise at day " + i);
            }
            if (set == rise) {
                continue;   // polar night: no daylight that day
            }
            if (!spans.isEmpty()) {
                long[] last = spans.get(spans.size() - 1);
                if (rise < last[0]) {
                    throw new IllegalArgumentException("sun times out of order at day " + i);
                }
                if (rise <= last[1]) {
                    last[1] = Math.max(last[1], set);   // touching days: polar day
                    continue;
                }
            }
            spans.add(new long[] {rise, set});
        }
        long[] rise = new long[spans.size()];
        long[] set = new long[spans.size()];
        for (int i = 0; i < rise.length; i++) {
            rise[i] = spans.get(i)[0];
            set[i] = spans.get(i)[1];
        }
        boolean any = sunriseEpochSeconds.length > 0;
        long from = any ? sunriseEpochSeconds[0] * 1000L : 0;
        long to = any ? sunsetEpochSeconds[sunsetEpochSeconds.length - 1] * 1000L : 0;
        return new SunTimes(rise, set, any, from, to);
    }

    /** Whether any day is known, including a polar night with no daylight at all. */
    public boolean isKnown() {
        return known;
    }

    /**
     * Whether the instant is between a sunrise and that day's sunset. Null when it is more than a
     * day outside the covered days, where a night cannot be told from a missing day.
     */
    public Boolean isDaylight(long millis) {
        if (!known || millis < coveredFrom - DAY_MILLIS || millis >= coveredTo + DAY_MILLIS) {
            return null;
        }
        for (int i = 0; i < sunriseMillis.length; i++) {
            if (millis >= sunriseMillis[i] && millis < sunsetMillis[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the whole span [start, end) lies between one sunrise and the same day's sunset.
     * False when any part is after dark or the span is not covered.
     */
    public boolean isDaylightThroughout(long startMillis, long endMillis) {
        for (int i = 0; i < sunriseMillis.length; i++) {
            if (startMillis >= sunriseMillis[i] && endMillis <= sunsetMillis[i]) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "SunTimes{rise=" + Arrays.toString(sunriseMillis)
                + ", set=" + Arrays.toString(sunsetMillis) + '}';
    }
}
