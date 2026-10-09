package com.comp90018.storystep.domain.forecast;

import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares departure times for a planned walk by the chance of rain during the whole walk.
 *
 * <p>Within one model the chance is the share of its members with at least {@value #WET_MM} mm
 * in some part of the walk. Counting members, rather than combining per-hour chances, needs no
 * assumption about whether rain in one hour predicts rain in the next: each member already is one
 * consistent scenario. The models are then averaged with equal weight, because a single model's
 * ensemble is typically too confident and models disagree; averaging them is the usual remedy
 * and keeps the best-staffed model from outvoting the others. See decision D-08.
 *
 * <p>A model with a three-hour native step is judged per block: a member is wet in a block when
 * the block's total reaches the threshold, and a walk touching any part of the block counts the
 * whole block.
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
     * An hour or block counts as wet from this amount. Values arrive in 0.1 mm steps, so this is
     * "more than a trace"; it is also the Bureau of Meteorology's amount for any rain.
     */
    public static final double WET_MM = 0.2;

    /** A model with fewer members than this is left out. */
    public static final int MIN_MEMBERS = 10;

    /** Daylight choices closer than this are reported as no real difference. */
    static final double MEANINGFUL_GAP = 0.05;

    /** Absorbs binary rounding, so a reported 0.2 is never read as just under it. */
    private static final double EPSILON = 1e-9;

    public static boolean isTooLong(long tripMillis) {
        return tripMillis > MAX_TRIP_MILLIS;
    }

    /**
     * @param nowMillis  the current instant
     * @param tripMillis the whole walk including stays; positive and at most
     *                   {@link #MAX_TRIP_MILLIS}
     * @throws ForecastUnavailableException with {@code UNUSABLE} when no model has enough members
     *                                      and complete values for every departure, or too few
     *                                      hours remain for any departure
     */
    public DepartureAdvice advise(long nowMillis, long tripMillis,
                                  EnsemblePrecipitation ensemble) {
        Objects.requireNonNull(ensemble, "ensemble");
        if (tripMillis <= 0 || isTooLong(tripMillis)) {
            throw new IllegalArgumentException("trip duration out of range: " + tripMillis);
        }

        List<Long> starts = departures(nowMillis, tripMillis, ensemble.coverageEndMillis());
        if (starts.isEmpty() || !inside(starts.get(0), ensemble)) {
            throw unusable();
        }

        List<EnsemblePrecipitation.Model> used = new ArrayList<>();
        List<String> names = new ArrayList<>();
        long needFrom = starts.get(0);
        long needTo = starts.get(starts.size() - 1) + tripMillis;
        for (EnsemblePrecipitation.Model model : ensemble.getModels()) {
            if (model.memberCount() >= MIN_MEMBERS && complete(model, ensemble, needFrom, needTo)) {
                used.add(model);
                names.add(model.getName());
            }
        }
        if (used.isEmpty()) {
            throw unusable();
        }

        List<DepartureAdvice.Candidate> candidates = new ArrayList<>(starts.size());
        for (long start : starts) {
            long end = start + tripMillis;
            double sum = 0;
            for (EnsemblePrecipitation.Model model : used) {
                sum += wetShare(model, ensemble, start, end);
            }
            candidates.add(new DepartureAdvice.Candidate(start, start == nowMillis,
                    sum / used.size(), daylight(ensemble, start, end)));
        }
        return choose(candidates, names);
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

    /** Whether the data begins no later than this instant. */
    private static boolean inside(long startMillis, EnsemblePrecipitation ensemble) {
        return HourlyBuckets.startMillis(ensemble.endEpochSecond(0)) <= startMillis;
    }

    /**
     * Whether the model has a value in every hour any departure needs. For a three-hour model
     * that is every hour of every block a walk touches, since the block total is read from them.
     */
    private static boolean complete(EnsemblePrecipitation.Model model,
                                    EnsemblePrecipitation ensemble, long from, long to) {
        int block = model.getBlockHours();
        for (int h = 0; h < ensemble.hourCount(); h++) {
            long b = HourlyBuckets.block(ensemble.endEpochSecond(h), block);
            if (HourlyBuckets.blockStartMillis(b, block) >= to
                    || HourlyBuckets.blockEndMillis(b, block) <= from) {
                continue;
            }
            for (int m = 0; m < model.memberCount(); m++) {
                if (Double.isNaN(model.millimetres(m, h))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Share of the model's members wet in some hour or block the walk touches. */
    private static double wetShare(EnsemblePrecipitation.Model model,
                                   EnsemblePrecipitation ensemble, long start, long end) {
        int blockHours = model.getBlockHours();
        int wet = 0;
        for (int m = 0; m < model.memberCount(); m++) {
            if (memberWet(model, m, blockHours, ensemble, start, end)) {
                wet++;
            }
        }
        return (double) wet / model.memberCount();
    }

    private static boolean memberWet(EnsemblePrecipitation.Model model, int member,
                                     int blockHours, EnsemblePrecipitation ensemble,
                                     long start, long end) {
        int h = 0;
        while (h < ensemble.hourCount()) {
            long block = HourlyBuckets.block(ensemble.endEpochSecond(h), blockHours);
            boolean touched = HourlyBuckets.blockStartMillis(block, blockHours) < end
                    && HourlyBuckets.blockEndMillis(block, blockHours) > start;
            // The rows of one block are adjacent; read them together.
            double sum = 0;
            int rows = 0;
            while (h < ensemble.hourCount()
                    && HourlyBuckets.block(ensemble.endEpochSecond(h), blockHours) == block) {
                sum += model.millimetres(member, h);
                rows++;
                h++;
            }
            // A block's total is spread evenly over its hours, so the mean of the rows present
            // times the block length recovers it even where the data starts or ends mid-block.
            if (touched && sum / rows * blockHours >= WET_MM - EPSILON) {
                return true;
            }
        }
        return false;
    }

    /** Whether every hour the walk touches is in daylight. */
    private static boolean daylight(EnsemblePrecipitation ensemble, long start, long end) {
        for (int h = 0; h < ensemble.hourCount(); h++) {
            if (HourlyBuckets.overlaps(ensemble.endEpochSecond(h), start, end)
                    && !ensemble.isDay(h)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Best is the driest daylight walk, the earliest on a tie; after-dark walks are considered
     * only when no walk stays in daylight.
     */
    private static DepartureAdvice choose(List<DepartureAdvice.Candidate> candidates,
                                          List<String> models) {
        boolean anyDaylight = false;
        for (DepartureAdvice.Candidate candidate : candidates) {
            anyDaylight |= candidate.isDaylight();
        }

        int best = -1;
        double lowest = Double.MAX_VALUE;
        double highest = -1;
        int pool = 0;
        boolean allNotAdvisable = true;
        for (int i = 0; i < candidates.size(); i++) {
            DepartureAdvice.Candidate candidate = candidates.get(i);
            if (anyDaylight && !candidate.isDaylight()) {
                continue;
            }
            pool++;
            if (candidate.getProbability() < lowest - EPSILON) {
                lowest = candidate.getProbability();
                best = i;
            }
            highest = Math.max(highest, candidate.getProbability());
            allNotAdvisable &= candidate.getPercent() >= RainBand.NOT_ADVISABLE_PERCENT;
        }

        boolean noMeaningfulDifference = pool >= 2 && highest - lowest < MEANINGFUL_GAP - EPSILON;
        if (noMeaningfulDifference) {
            best = firstInPool(candidates, anyDaylight);
        }
        return new DepartureAdvice(candidates, models, best, noMeaningfulDifference,
                allNotAdvisable, !anyDaylight);
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
