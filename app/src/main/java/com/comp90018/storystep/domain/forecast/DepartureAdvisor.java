package com.comp90018.storystep.domain.forecast;

import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares departure times for a planned walk by the chance of rain during the whole walk.
 *
 * <p>Within one model the chance is the share of its members in which at least {@value #WET_MM} mm
 * falls during the walk. Counting members, rather than combining per-hour chances, needs no
 * assumption about whether rain in one hour predicts rain in the next: each member already is one
 * consistent scenario. The models are then averaged with equal weight, because a single model's
 * ensemble is typically too confident and models disagree; averaging them is the usual remedy
 * and keeps the best-staffed model from outvoting the others. See decision D-08.
 *
 * <p>Rain during the walk is each hour's (ICON) or three-hour block's (ECMWF, GFS) amount times
 * the share of that hour or block the walk covers, summed over the walk. So every model answers
 * the same question whatever its time step, a walk crossing a block edge is not wetter by
 * construction, and the minutes of the current hour already gone do not count. The rain is
 * assumed to fall evenly within an hour or block, which is how Open-Meteo spreads a three-hourly
 * total over its hours (review 2026-10-10).
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
        return advise(nowMillis, tripMillis, ensemble, null);
    }

    /**
     * As {@link #advise(long, long, EnsemblePrecipitation)}, also flagging severe weather from the
     * hourly forecast (D-08 §4.6): severe departures are never best, and if every daylight
     * departure is severe no best is named. A null forecast leaves severe weather unchecked.
     */
    public DepartureAdvice advise(long nowMillis, long tripMillis,
                                  EnsemblePrecipitation ensemble, HourlyForecast hourly) {
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
                    sum / used.size(), daylight(ensemble, start, end),
                    hourly == null ? null : SevereWeather.assessWalk(hourly, start, end)));
        }
        boolean checked = hourly != null;
        for (DepartureAdvice.Candidate candidate : candidates) {
            checked &= candidate.getSevere() != null;
        }
        return choose(candidates, names, checked);
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
        double during = 0;
        int h = 0;
        while (h < ensemble.hourCount()) {
            long block = HourlyBuckets.block(ensemble.endEpochSecond(h), blockHours);
            long blockStart = HourlyBuckets.blockStartMillis(block, blockHours);
            long blockEnd = HourlyBuckets.blockEndMillis(block, blockHours);
            // The rows of one block are adjacent; read them together.
            double sum = 0;
            int rows = 0;
            while (h < ensemble.hourCount()
                    && HourlyBuckets.block(ensemble.endEpochSecond(h), blockHours) == block) {
                sum += model.millimetres(member, h);
                rows++;
                h++;
            }
            long overlap = Math.min(end, blockEnd) - Math.max(start, blockStart);
            if (overlap > 0) {
                // A block's total is spread evenly over its hours, so the mean of the rows present
                // times the block length recovers it even where the data starts mid-block.
                double total = sum / rows * blockHours;
                during += total * overlap / (double) (blockEnd - blockStart);
            }
        }
        return during >= WET_MM - EPSILON;
    }

    /** Whether the whole walk is between one sunrise and that day's sunset. */
    private static boolean daylight(EnsemblePrecipitation ensemble, long start, long end) {
        return ensemble.getPlace().getSun().isDaylightThroughout(start, end);
    }

    /**
     * Best is the driest daylight walk without severe weather, the earliest on a tie; after-dark
     * walks are considered only when no walk stays in daylight. Severe walks are never best, and
     * when every daylight walk is severe no best is named.
     */
    private static DepartureAdvice choose(List<DepartureAdvice.Candidate> candidates,
                                          List<String> models, boolean severeChecked) {
        boolean anyDaylight = false;
        boolean daylightNotSevere = false;
        boolean anyNotSevere = false;
        for (DepartureAdvice.Candidate candidate : candidates) {
            anyDaylight |= candidate.isDaylight();
            daylightNotSevere |= candidate.isDaylight() && !candidate.isSevere();
            anyNotSevere |= !candidate.isSevere();
        }
        // On the shown percent, so "5%" never sits beside "under 5%"; and never while any walk is
        // flagged, since "leave whenever you like" would then contradict a pill.
        boolean dry = true;
        for (DepartureAdvice.Candidate candidate : candidates) {
            dry &= candidate.getPercent() < RainBand.DRY_PERCENT && isClean(candidate);
        }
        boolean severeAllDaytime = anyDaylight ? !daylightNotSevere : !anyNotSevere;
        if (severeAllDaytime) {
            return new DepartureAdvice(candidates, models, -1, false, false, !anyDaylight,
                    false, true, severeChecked);
        }

        int best = -1;
        double lowest = Double.MAX_VALUE;
        double highest = -1;
        int pool = 0;
        boolean allNotAdvisable = true;
        for (int i = 0; i < candidates.size(); i++) {
            DepartureAdvice.Candidate candidate = candidates.get(i);
            if ((anyDaylight && !candidate.isDaylight()) || candidate.isSevere()) {
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
        // Among departures within the meaningful gap of the driest, one with no caution flag
        // goes before one with a flag: equal rain, fewer worries.
        int clean = firstCleanNear(candidates, anyDaylight, lowest);
        if (noMeaningfulDifference) {
            best = clean >= 0 ? clean : firstInPool(candidates, anyDaylight);
        } else if (clean >= 0 && !isClean(candidates.get(best))) {
            best = clean;
        }
        return new DepartureAdvice(candidates, models, best, noMeaningfulDifference,
                allNotAdvisable, !anyDaylight, dry, false, severeChecked);
    }

    /** No caution or severe flag; an unchecked walk counts as clean, having nothing against it. */
    private static boolean isClean(DepartureAdvice.Candidate candidate) {
        return candidate.getSevere() == null
                || candidate.getSevere().getLevel() == SevereWeather.Level.NONE;
    }

    /** The earliest clean candidate in the pool within the meaningful gap of the driest, or -1. */
    private static int firstCleanNear(List<DepartureAdvice.Candidate> candidates,
                                      boolean daylightOnly, double lowest) {
        for (int i = 0; i < candidates.size(); i++) {
            DepartureAdvice.Candidate candidate = candidates.get(i);
            if ((daylightOnly && !candidate.isDaylight()) || candidate.isSevere()) {
                continue;
            }
            if (isClean(candidate)
                    && candidate.getProbability() < lowest + MEANINGFUL_GAP - EPSILON) {
                return i;
            }
        }
        return -1;
    }

    private static int firstInPool(List<DepartureAdvice.Candidate> candidates,
                                   boolean daylightOnly) {
        for (int i = 0; i < candidates.size(); i++) {
            DepartureAdvice.Candidate candidate = candidates.get(i);
            if ((!daylightOnly || candidate.isDaylight()) && !candidate.isSevere()) {
                return i;
            }
        }
        throw new IllegalStateException("empty pool");
    }

    private static ForecastUnavailableException unusable() {
        return new ForecastUnavailableException(ForecastUnavailableException.Reason.UNUSABLE);
    }
}
