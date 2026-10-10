package com.comp90018.storystep.domain.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every departure considered for one walk, in time order with "Now" first, and which is best.
 */
public final class DepartureAdvice {

    /** One possible departure and the chance of rain during the walk that follows it. */
    public static final class Candidate {

        private final long startMillis;
        private final boolean now;
        private final double probability;
        private final boolean daylight;
        private final SevereWeather.Flag severe;

        Candidate(long startMillis, boolean now, double probability, boolean daylight,
                  SevereWeather.Flag severe) {
            this.startMillis = startMillis;
            this.now = now;
            this.probability = probability;
            this.daylight = daylight;
            this.severe = severe;
        }

        /**
         * The worst severe-weather flag during the walk; null when it could not be checked (no
         * hourly forecast, or one that does not cover the walk).
         */
        public SevereWeather.Flag getSevere() {
            return severe;
        }

        boolean isSevere() {
            return severe != null && severe.getLevel() == SevereWeather.Level.SEVERE;
        }

        public long getStartMillis() {
            return startMillis;
        }

        /** Whether this is "leave now" rather than a later full hour. */
        public boolean isNow() {
            return now;
        }

        /**
         * Chance of rain at some point during the walk, 0 to 1: the average over the models used
         * of each model's share of wet members. What ranking uses.
         */
        public double getProbability() {
            return probability;
        }

        /** Whether every hour of the walk is in daylight. */
        public boolean isDaylight() {
            return daylight;
        }

        /** {@link #getProbability()} rounded to a whole percent. */
        public int getPercent() {
            return (int) Math.round(100.0 * probability);
        }

        public RainBand getBand() {
            return RainBand.forPercent(getPercent());
        }
    }

    private final List<Candidate> candidates;
    private final List<String> models;
    private final int bestIndex;
    private final boolean noMeaningfulDifference;
    private final boolean allNotAdvisable;
    private final boolean noDaylightCandidate;
    private final boolean dryThroughout;
    private final boolean severeAllDaytime;
    private final boolean severeChecked;

    DepartureAdvice(List<Candidate> candidates, List<String> models, int bestIndex,
                    boolean noMeaningfulDifference, boolean allNotAdvisable,
                    boolean noDaylightCandidate, boolean dryThroughout,
                    boolean severeAllDaytime, boolean severeChecked) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
        this.bestIndex = bestIndex;
        this.noMeaningfulDifference = noMeaningfulDifference;
        this.allNotAdvisable = allNotAdvisable;
        this.noDaylightCandidate = noDaylightCandidate;
        this.dryThroughout = dryThroughout;
        this.severeAllDaytime = severeAllDaytime;
        this.severeChecked = severeChecked;
    }

    /**
     * Every daylight departure meets a severe-weather threshold: no best is named and the page
     * suggests another day. {@link #getBestIndex()} is then -1.
     */
    public boolean isSevereAllDaytime() {
        return severeAllDaytime;
    }

    /** Whether severe weather was checked at all; false when the hourly forecast was missing. */
    public boolean isSevereChecked() {
        return severeChecked;
    }

    /** Whether a best departure is named. */
    public boolean hasBest() {
        return bestIndex >= 0;
    }

    /**
     * Every departure, by day or night, is under {@link RainBand#DRY_PERCENT}: whenever the walker
     * leaves, rain is not expected, and naming one time as best would overstate a difference.
     */
    public boolean isDryThroughout() {
        return dryThroughout;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    /** The models whose members were counted, for the page's source note. */
    public List<String> getModels() {
        return models;
    }

    public int getBestIndex() {
        return bestIndex;
    }

    public Candidate getBest() {
        return candidates.get(bestIndex);
    }

    /** The choices differ by less than five percentage points, so the earliest is shown as best. */
    public boolean isNoMeaningfulDifference() {
        return noMeaningfulDifference;
    }

    /** Every departure the best was chosen from is at or above the not-advisable threshold. */
    public boolean isAllNotAdvisable() {
        return allNotAdvisable;
    }

    /** No walk stays in daylight, so the best was chosen among after-dark departures. */
    public boolean isNoDaylightCandidate() {
        return noDaylightCandidate;
    }
}
