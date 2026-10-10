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
    public static final SunTimes UNKNOWN = new SunTimes(new long[0], new long[0]);

    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final long[] sunriseMillis;
    private final long[] sunsetMillis;

    private SunTimes(long[] sunriseMillis, long[] sunsetMillis) {
        this.sunriseMillis = sunriseMillis;
        this.sunsetMillis = sunsetMillis;
    }

    /**
     * @param sunriseEpochSeconds one sunrise per day, in order
     * @param sunsetEpochSeconds  the same day's sunset, each after its sunrise
     */
    public static SunTimes of(long[] sunriseEpochSeconds, long[] sunsetEpochSeconds) {
        Objects.requireNonNull(sunriseEpochSeconds, "sunrise");
        Objects.requireNonNull(sunsetEpochSeconds, "sunset");
        if (sunriseEpochSeconds.length != sunsetEpochSeconds.length) {
            throw new IllegalArgumentException("sunrise and sunset counts differ");
        }
        long[] rise = new long[sunriseEpochSeconds.length];
        long[] set = new long[sunsetEpochSeconds.length];
        for (int i = 0; i < rise.length; i++) {
            if (sunsetEpochSeconds[i] <= sunriseEpochSeconds[i]
                    || (i > 0 && sunriseEpochSeconds[i] <= sunsetEpochSeconds[i - 1])) {
                throw new IllegalArgumentException("sun times out of order at day " + i);
            }
            rise[i] = sunriseEpochSeconds[i] * 1000L;
            set[i] = sunsetEpochSeconds[i] * 1000L;
        }
        return new SunTimes(rise, set);
    }

    /** Whether any day is known. */
    public boolean isKnown() {
        return sunriseMillis.length > 0;
    }

    /**
     * Whether the instant is between a sunrise and that day's sunset. Null when it is more than a
     * day outside the covered days, where a night cannot be told from a missing day.
     */
    public Boolean isDaylight(long millis) {
        if (!isKnown() || millis < sunriseMillis[0] - DAY_MILLIS
                || millis >= sunsetMillis[sunsetMillis.length - 1] + DAY_MILLIS) {
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
