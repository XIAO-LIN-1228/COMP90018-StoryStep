package com.comp90018.storystep.domain.forecast;

import java.util.Objects;

/**
 * Where a forecast is for, as far as telling time goes: the location's timezone and its sunrise
 * and sunset. Times on the forecast pages are shown in this zone, because the walk happens there
 * even when the phone is set to another one (decision D-08).
 */
public final class ForecastPlace {

    private final String timezoneId;
    private final int utcOffsetSeconds;
    private final SunTimes sun;

    /**
     * @param timezoneId       IANA identifier from the provider, e.g. {@code Australia/Melbourne};
     *                         may be empty when not given
     * @param utcOffsetSeconds the provider's offset at request time, used when the identifier is
     *                         unknown to the device
     */
    public ForecastPlace(String timezoneId, int utcOffsetSeconds, SunTimes sun) {
        this.timezoneId = timezoneId == null ? "" : timezoneId;
        this.utcOffsetSeconds = utcOffsetSeconds;
        this.sun = Objects.requireNonNull(sun, "sun");
    }

    /** A place with no timezone or sun times known: UTC, daylight unknown. */
    public static ForecastPlace unknown() {
        return new ForecastPlace("", 0, SunTimes.UNKNOWN);
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public int getUtcOffsetSeconds() {
        return utcOffsetSeconds;
    }

    public SunTimes getSun() {
        return sun;
    }
}
