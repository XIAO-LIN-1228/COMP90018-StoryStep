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
        private final int wetMembers;
        private final int members;
        private final boolean daylight;

        Candidate(long startMillis, boolean now, int wetMembers, int members, boolean daylight) {
            this.startMillis = startMillis;
            this.now = now;
            this.wetMembers = wetMembers;
            this.members = members;
            this.daylight = daylight;
        }

        public long getStartMillis() {
            return startMillis;
        }

        /** Whether this is "leave now" rather than a later full hour. */
        public boolean isNow() {
            return now;
        }

        /** Ensemble members with rain in some hour of the walk; what ranking uses. */
        public int getWetMembers() {
            return wetMembers;
        }

        public int getMembers() {
            return members;
        }

        /** Whether every hour of the walk is in daylight. */
        public boolean isDaylight() {
            return daylight;
        }

        /** Chance of rain at some point during the walk, rounded to a whole percent. */
        public int getPercent() {
            return (int) Math.round(100.0 * wetMembers / members);
        }

        public RainBand getBand() {
            return RainBand.forPercent(getPercent());
        }
    }

    private final List<Candidate> candidates;
    private final int bestIndex;
    private final boolean noMeaningfulDifference;
    private final boolean allNotAdvisable;
    private final boolean noDaylightCandidate;

    DepartureAdvice(List<Candidate> candidates, int bestIndex, boolean noMeaningfulDifference,
                    boolean allNotAdvisable, boolean noDaylightCandidate) {
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.bestIndex = bestIndex;
        this.noMeaningfulDifference = noMeaningfulDifference;
        this.allNotAdvisable = allNotAdvisable;
        this.noDaylightCandidate = noDaylightCandidate;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public int getBestIndex() {
        return bestIndex;
    }

    public Candidate getBest() {
        return candidates.get(bestIndex);
    }

    /** The choices differ by fewer than two members, so the earliest is shown as best. */
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
