package com.comp90018.storystep.domain.forecast;

import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares departure times for a planned walk by the chance of rain during the whole walk.
 *
 * <p>For each departure the chance is the share of ensemble members with at least
 * {@value #WET_MM} mm in any hour the walk touches. Counting members, rather than combining
 * per-hour chances, needs no assumption about whether rain in one hour predicts rain in the next:
 * each member already is one consistent scenario. See decision D-08.
 *
 * <p>Departures are "now" and then each full hour up to a day ahead. Hours are UTC hours, which
 * are also local full hours wherever the offset is a whole number of hours.
 *
 * <p>The figures are uncalibrated model output, compared with each other; they are not a promise
 * that a walk stays dry.
 */
public final class DepartureAdvisor {

    /** Walks longer than this get no advice; the caller says so before asking. */
    public static final long MAX_TRIP_MILLIS = 8L * HourlyBuckets.HOUR_MILLIS;

    /** How far ahead departures are offered. */
    public static final long HORIZON_MILLIS = 24L * HourlyBuckets.HOUR_MILLIS;

    /**
     * An hour counts as wet from this amount. Values arrive in 0.1 mm steps, so this is "more
     * than a trace"; it is also the Bureau of Meteorology's amount for any rain.
     */
    public static final double WET_MM = 0.2;

    /** Fewer members than this cannot support a meaningful share. */
    public static final int MIN_MEMBERS = 10;

    /** Daylight choices closer than this many members are reported as no real difference. */
    static final int MEANINGFUL_MEMBER_GAP = 2;

    /** Absorbs binary rounding, so a reported 0.2 is never read as just under it. */
    private static final double WET_EPSILON = 1e-9;

    public static boolean isTooLong(long tripMillis) {
        return tripMillis > MAX_TRIP_MILLIS;
    }

    /**
     * @param nowMillis  the current instant
     * @param tripMillis the whole walk including stays; positive and at most
     *                   {@link #MAX_TRIP_MILLIS}
     * @throws ForecastUnavailableException with {@code UNUSABLE} when the ensemble has too few
     *                                      members, a missing value in an hour some departure
     *                                      needs, or too few hours for any departure
     */
    public DepartureAdvice advise(long nowMillis, long tripMillis,
                                  EnsemblePrecipitation ensemble) {
        Objects.requireNonNull(ensemble, "ensemble");
        if (tripMillis <= 0 || isTooLong(tripMillis)) {
            throw new IllegalArgumentException("trip duration out of range: " + tripMillis);
        }
        if (ensemble.memberCount() < MIN_MEMBERS) {
            throw unusable();
        }

        List<Long> starts = departures(nowMillis, tripMillis, ensemble.coverageEndMillis());
        if (starts.isEmpty()) {
            throw unusable();
        }

        List<DepartureAdvice.Candidate> candidates = new ArrayList<>(starts.size());
        for (int i = 0; i < starts.size(); i++) {
            long start = starts.get(i);
            candidates.add(evaluate(start, start + tripMillis, start == nowMillis, ensemble));
        }
        return choose(candidates);
    }

    /** "Now", then each full hour after it within the horizon whose walk ends inside the data. */
    private static List<Long> departures(long nowMillis, long tripMillis, long coverageEndMillis) {
        List<Long> starts = new ArrayList<>();
        if (nowMillis + tripMillis <= coverageEndMillis) {
            starts.add(nowMillis);
        }
        long hour = HourlyBuckets.HOUR_MILLIS;
        long next = Math.floorDiv(nowMillis, hour) * hour + hour;
        for (long start = next; start <= nowMillis + HORIZON_MILLIS; start += hour) {
            if (start + tripMillis <= coverageEndMillis) {
                starts.add(start);
            }
        }
        return starts;
    }

    private static DepartureAdvice.Candidate evaluate(long start, long end, boolean now,
                                                      EnsemblePrecipitation ensemble) {
        boolean daylight = true;
        boolean any = false;
        boolean[] wet = new boolean[ensemble.memberCount()];
        for (int h = 0; h < ensemble.hourCount(); h++) {
            if (!HourlyBuckets.overlaps(ensemble.endEpochSecond(h), start, end)) {
                continue;
            }
            any = true;
            daylight &= ensemble.isDay(h);
            for (int m = 0; m < wet.length; m++) {
                double mm = ensemble.millimetres(m, h);
                if (Double.isNaN(mm)) {
                    throw unusable();
                }
                if (mm >= WET_MM - WET_EPSILON) {
                    wet[m] = true;
                }
            }
        }
        if (!any) {
            // The walk starts before the first hour the data describes.
            throw unusable();
        }
        int wetMembers = 0;
        for (boolean memberWet : wet) {
            if (memberWet) {
                wetMembers++;
            }
        }
        return new DepartureAdvice.Candidate(start, now, wetMembers, wet.length, daylight);
    }

    /**
     * Best is the driest daylight walk, the earliest on a tie; after-dark walks are considered
     * only when no walk stays in daylight.
     */
    private static DepartureAdvice choose(List<DepartureAdvice.Candidate> candidates) {
        boolean anyDaylight = false;
        for (DepartureAdvice.Candidate candidate : candidates) {
            anyDaylight |= candidate.isDaylight();
        }

        int best = -1;
        int fewest = Integer.MAX_VALUE;
        int most = Integer.MIN_VALUE;
        int pool = 0;
        boolean allNotAdvisable = true;
        for (int i = 0; i < candidates.size(); i++) {
            DepartureAdvice.Candidate candidate = candidates.get(i);
            if (anyDaylight && !candidate.isDaylight()) {
                continue;
            }
            pool++;
            if (candidate.getWetMembers() < fewest) {
                fewest = candidate.getWetMembers();
                best = i;
            }
            most = Math.max(most, candidate.getWetMembers());
            allNotAdvisable &= candidate.getPercent() >= RainBand.NOT_ADVISABLE_PERCENT;
        }

        boolean noMeaningfulDifference = pool >= 2 && most - fewest < MEANINGFUL_MEMBER_GAP;
        if (noMeaningfulDifference) {
            best = firstInPool(candidates, anyDaylight);
        }
        return new DepartureAdvice(candidates, best, noMeaningfulDifference, allNotAdvisable,
                !anyDaylight);
    }

    private static int firstInPool(List<DepartureAdvice.Candidate> candidates,
                                   boolean daylightOnly) {
        for (int i = 0; i < candidates.size(); i++) {
            if (!daylightOnly || candidates.get(i).isDaylight()) {
                return i;
            }
        }
        throw new IllegalStateException("empty pool");
    }

    private static ForecastUnavailableException unusable() {
        return new ForecastUnavailableException(ForecastUnavailableException.Reason.UNUSABLE);
    }
}
