package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.domain.forecast.DepartureAdvice;
import com.comp90018.storystep.domain.forecast.DepartureAdvisor;
import com.comp90018.storystep.domain.forecast.EnsemblePrecipitation;

import org.junit.Test;

import java.util.Arrays;

/**
 * Departure advice on the recorded answers: the whole path from provider JSON to ranked
 * departures, with nothing hand-built.
 */
public class RecordedDepartureAdviceTest {

    private static final long HOUR = 3_600_000L;

    private final DepartureAdvisor advisor = new DepartureAdvisor();

    private static EnsemblePrecipitation recorded(String place) {
        return RemoteForecastMapper.toEnsemble(ForecastFixtures.ensemble(place),
                ForecastFixtures.RECORDED_AT);
    }

    @Test
    public void aDryDayInMelbourneGivesFullAdviceWithAllThreeModels() {
        DepartureAdvice advice = advisor.advise(ForecastFixtures.RECORDED_AT, 2 * HOUR,
                recorded("melbourne"));

        assertEquals(Arrays.asList("ICON", "ECMWF", "GFS"), advice.getModels());
        assertEquals(25, advice.getCandidates().size());
        assertTrue(advice.getBest().isDaylight());
        assertTrue(advice.getBest().getPercent() <= 5);
    }

    /**
     * The point of counting members over the walk, on real data: for every departure, the chance
     * of rain during a three-hour walk is at least the chance in its wettest single hour or block,
     * and for some departures it is clearly more. A rule that took the wettest hour would
     * understate those walks.
     */
    @Test
    public void aWalkIsAtLeastAsWetAsItsWettestHourAndSometimesWetter() {
        EnsemblePrecipitation ensemble = recorded("singapore");
        long trip = 3 * HOUR;

        DepartureAdvice advice = advisor.advise(ForecastFixtures.RECORDED_AT, trip, ensemble);

        double largestGap = 0;
        for (DepartureAdvice.Candidate candidate : advice.getCandidates()) {
            double wettestHour = 0;
            long start = candidate.getStartMillis();
            // Each single hour of the walk, as a one-hour "walk" of its own.
            for (long hourStart = start; hourStart < start + trip; hourStart += HOUR) {
                long hourEnd = Math.min(hourStart + HOUR, start + trip);
                // "Leave now" for exactly that hour: the advisor's own rule for that window.
                DepartureAdvice.Candidate single = advisor
                        .advise(hourStart, hourEnd - hourStart, ensemble).getCandidates().get(0);
                wettestHour = Math.max(wettestHour, single.getProbability());
            }
            assertTrue(candidate.getProbability() + 1e-9 >= wettestHour);
            largestGap = Math.max(largestGap, candidate.getProbability() - wettestHour);
        }
        assertTrue("largest gap " + largestGap, largestGap >= 0.10);
    }
}
