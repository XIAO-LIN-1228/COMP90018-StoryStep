package com.comp90018.storystep.domain.forecast;

import java.util.Arrays;
import java.util.Objects;

/**
 * Hourly precipitation from every member of an ensemble forecast, for one weather area.
 *
 * <p>Each member is one complete, internally consistent run of the model. Keeping the members
 * apart, rather than a per-hour chance of rain, is what lets the chance of rain over a whole walk
 * be counted directly: a member that rains at 14:00 and at 15:00 is one wet scenario, not two
 * independent ones.
 *
 * <p>A value the provider left empty is held as {@link Double#NaN}. This class keeps the shape
 * consistent; whether enough data is present for advice is decided by {@link DepartureAdvisor}.
 */
public final class EnsemblePrecipitation {

    private final long[] endEpochSeconds;
    private final double[][] memberMm;
    private final boolean[] isDay;
    private final long fetchedAtMillis;

    /**
     * @param endEpochSeconds end of each hour, one hour apart (Open-Meteo's timestamps)
     * @param memberMm        millimetres per member per hour, {@code [member][hour]}
     * @param isDay           whether each hour's end falls in daylight
     */
    public EnsemblePrecipitation(long[] endEpochSeconds, double[][] memberMm, boolean[] isDay,
                                 long fetchedAtMillis) {
        Objects.requireNonNull(endEpochSeconds, "endEpochSeconds");
        Objects.requireNonNull(memberMm, "memberMm");
        Objects.requireNonNull(isDay, "isDay");
        HourlyBuckets.requireHourly(endEpochSeconds);
        if (isDay.length != endEpochSeconds.length) {
            throw new IllegalArgumentException("isDay length differs from hours");
        }
        double[][] copy = new double[memberMm.length][];
        for (int m = 0; m < memberMm.length; m++) {
            double[] series = Objects.requireNonNull(memberMm[m], "member");
            if (series.length != endEpochSeconds.length) {
                throw new IllegalArgumentException("member " + m + " length differs from hours");
            }
            copy[m] = series.clone();
        }
        this.endEpochSeconds = endEpochSeconds.clone();
        this.memberMm = copy;
        this.isDay = isDay.clone();
        this.fetchedAtMillis = fetchedAtMillis;
    }

    public int hourCount() {
        return endEpochSeconds.length;
    }

    public int memberCount() {
        return memberMm.length;
    }

    public long endEpochSecond(int hour) {
        return endEpochSeconds[hour];
    }

    /** Millimetres for one member in one hour; NaN when the provider gave none. */
    public double millimetres(int member, int hour) {
        return memberMm[member][hour];
    }

    public boolean isDay(int hour) {
        return isDay[hour];
    }

    /** End of the last hour covered: no trip may finish after this. */
    public long coverageEndMillis() {
        return HourlyBuckets.endMillis(endEpochSeconds[endEpochSeconds.length - 1]);
    }

    public long getFetchedAtMillis() {
        return fetchedAtMillis;
    }

    @Override
    public String toString() {
        return "EnsemblePrecipitation{hours=" + hourCount() + ", members=" + memberCount()
                + ", first=" + endEpochSeconds[0] + ", isDay=" + Arrays.toString(isDay) + '}';
    }
}
