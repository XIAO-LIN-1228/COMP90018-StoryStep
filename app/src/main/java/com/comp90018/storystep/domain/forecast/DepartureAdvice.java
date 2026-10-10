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

        Candidate(long startMillis, boolean now, double probability, boolean daylight) {
            this.startMillis = startMillis;
            this.now = now;
            this.probability = probability;
            this.daylight = daylight;
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

    DepartureAdvice(List<Candidate> candidates, List<String> models, int bestIndex,
                    boolean noMeaningfulDifference, boolean allNotAdvisable,
                    boolean noDaylightCandidate, boolean dryThroughout) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.models = Collections.unmodifiableList(new ArrayList<>(models));
        this.bestIndex = bestIndex;
        this.noMeaningfulDifference = noMeaningfulDifference;
        this.allNotAdvisable = allNotAdvisable;
        this.noDaylightCandidate = noDaylightCandidate;
        this.dryThroughout = dryThroughout;
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
