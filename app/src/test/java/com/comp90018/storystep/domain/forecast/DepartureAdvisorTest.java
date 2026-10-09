package com.comp90018.storystep.domain.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.repository.ForecastUnavailableException;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Departure advice from ensemble members, on hand-built ensembles with a fixed clock.
 *
 * <p>The fixture mirrors a real Open-Meteo answer requested at 22:28 UTC: 34 hourly rows whose
 * first timestamp is 22:00, the end of the 21:00-22:00 hour.
 */
public class DepartureAdvisorTest {

    private static final long HOUR = 3_600_000L;
    private static final long MINUTE = 60_000L;

    /** 2026-10-09 22:00 UTC: the first row's timestamp. */
    private static final long FIRST_END_SECOND = 1_791_583_200L;
    private static final long FIRST_END = FIRST_END_SECOND * 1000L;

    /** 22:28 UTC, mid-hour. */
    private static final long NOW = FIRST_END + 28 * MINUTE;

    /** 23:00, the first full-hour departure after NOW. */
    private static final long NEXT_HOUR = FIRST_END + HOUR;

    private final DepartureAdvisor advisor = new DepartureAdvisor();

    /** A dry, all-daylight ensemble that individual tests make wet or dark. */
    private static final class Fixture {
        final long[] ends;
        final double[][] mm;
        final boolean[] day;

        Fixture(int members, int hours) {
            ends = new long[hours];
            for (int h = 0; h < hours; h++) {
                ends[h] = FIRST_END_SECOND + h * 3_600L;
            }
            mm = new double[members][hours];
            day = new boolean[hours];
            Arrays.fill(day, true);
        }

        /** The row describing the hour that ends at {@code endMillis}. */
        int row(long endMillis) {
            return (int) ((endMillis - FIRST_END) / HOUR);
        }

        Fixture wet(int member, long hourEndMillis, double millimetres) {
            mm[member][row(hourEndMillis)] = millimetres;
            return this;
        }

        Fixture night(long fromEndMillis, long toEndMillis) {
            for (int h = row(fromEndMillis); h <= row(toEndMillis); h++) {
                day[h] = false;
            }
            return this;
        }

        EnsemblePrecipitation build() {
            return new EnsemblePrecipitation(ends,
                    Collections.singletonList(new EnsemblePrecipitation.Model("icon", 1, mm)),
                    day, NOW);
        }
    }

    private static Fixture fixture() {
        return new Fixture(40, 34);
    }

    private static DepartureAdvice.Candidate startingAt(DepartureAdvice advice, long start) {
        for (DepartureAdvice.Candidate candidate : advice.getCandidates()) {
            if (candidate.getStartMillis() == start) {
                return candidate;
            }
        }
        throw new AssertionError("no departure at " + start);
    }

    /** Wet members of a single 40-member model, read back from the probability. */
    private static int members(DepartureAdvice.Candidate candidate) {
        return (int) Math.round(candidate.getProbability() * 40);
    }

    private static void assertUnusable(Runnable call) {
        try {
            call.run();
            fail("expected UNUSABLE");
        } catch (ForecastUnavailableException expected) {
            assertEquals(ForecastUnavailableException.Reason.UNUSABLE, expected.getReason());
        }
    }

    @Test
    public void departuresAreNowThenEveryFullHourForADay() {
        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture().build());
        List<DepartureAdvice.Candidate> candidates = advice.getCandidates();

        assertEquals(25, candidates.size());
        assertTrue(candidates.get(0).isNow());
        assertEquals(NOW, candidates.get(0).getStartMillis());
        assertEquals(NEXT_HOUR, candidates.get(1).getStartMillis());
        assertFalse(candidates.get(1).isNow());
        assertEquals(NOW + 24 * HOUR - 28 * MINUTE, candidates.get(24).getStartMillis());
    }

    @Test
    public void thirtyFourHoursKeepEveryDepartureForAnEightHourWalk() {
        DepartureAdvice advice = advisor.advise(NOW, DepartureAdvisor.MAX_TRIP_MILLIS,
                fixture().build());

        assertEquals(25, advice.getCandidates().size());
    }

    @Test
    public void aValueDescribesTheHourEndingAtItsTimestamp() {
        // Rain stamped 00:00 fell between 23:00 and 00:00.
        EnsemblePrecipitation ensemble = fixture().wet(0, NEXT_HOUR + HOUR, 1.0).build();

        DepartureAdvice advice = advisor.advise(NOW, HOUR, ensemble);

        assertEquals(1, members(startingAt(advice, NEXT_HOUR)));
        assertEquals(0, members(startingAt(advice, NEXT_HOUR + HOUR)));
    }

    @Test
    public void aFullHourWalkOfOneHourTouchesOnlyItsOwnHour() {
        // Rain in the hours either side of 23:00-00:00 does not count for a 23:00 departure.
        EnsemblePrecipitation ensemble = fixture()
                .wet(0, NEXT_HOUR, 1.0)
                .wet(1, NEXT_HOUR + 2 * HOUR, 1.0)
                .build();

        assertEquals(0, members(startingAt(advisor.advise(NOW, HOUR, ensemble), NEXT_HOUR)));
    }

    @Test
    public void leavingNowMidHourCountsTheHourInProgressFully() {
        // 22:28 for ten minutes lies inside 22:00-23:00, the row stamped 23:00.
        EnsemblePrecipitation ensemble = fixture().wet(0, NEXT_HOUR, 0.5).build();

        DepartureAdvice advice = advisor.advise(NOW, 10 * MINUTE, ensemble);

        assertEquals(1, members(advice.getCandidates().get(0)));
    }

    @Test
    public void aTenthOfAMillimetreIsDryAndTwoTenthsAreWet() {
        EnsemblePrecipitation ensemble = fixture()
                .wet(0, NEXT_HOUR + HOUR, 0.1)
                .wet(1, NEXT_HOUR + HOUR, 0.2)
                .build();

        assertEquals(1, members(startingAt(advisor.advise(NOW, HOUR, ensemble), NEXT_HOUR)));
    }

    @Test
    public void aMemberWetInSeveralHoursCountsOnce() {
        EnsemblePrecipitation ensemble = fixture()
                .wet(0, NEXT_HOUR + HOUR, 1.0)
                .wet(0, NEXT_HOUR + 2 * HOUR, 1.0)
                .build();

        assertEquals(1, members(startingAt(advisor.advise(NOW, 2 * HOUR, ensemble), NEXT_HOUR)));
    }

    /**
     * The point of counting members: rain spread across a walk's hours makes the walk wetter than
     * any single hour. Here no hour exceeds 30% of members, but 90% of members rain at some point
     * during a three-hour walk. A rule based on the wettest single hour would report 30%.
     */
    @Test
    public void aWalkCanBeWetterThanAnyOfItsHours() {
        Fixture fixture = new Fixture(10, 34);
        for (int m = 0; m < 9; m++) {
            fixture.wet(m, NEXT_HOUR + (1 + m / 3) * HOUR, 1.0);
        }
        EnsemblePrecipitation ensemble = fixture.build();

        DepartureAdvice.Candidate walk = startingAt(
                advisor.advise(NOW, 3 * HOUR, ensemble), NEXT_HOUR);

        assertEquals(90, walk.getPercent());
        for (int hour = 1; hour <= 3; hour++) {
            int wetThatHour = 0;
            for (int m = 0; m < 10; m++) {
                if (fixture.mm[m][fixture.row(NEXT_HOUR + hour * HOUR)] >= 0.2) {
                    wetThatHour++;
                }
            }
            assertEquals(3, wetThatHour);
        }
    }

    @Test
    public void percentIsTheRoundedShareOfMembers() {
        Fixture fixture = fixture();
        for (int m = 0; m < 13; m++) {
            fixture.wet(m, NEXT_HOUR + HOUR, 1.0);
        }

        DepartureAdvice.Candidate walk = startingAt(
                advisor.advise(NOW, HOUR, fixture.build()), NEXT_HOUR);

        assertEquals(13, members(walk));
        assertEquals(33, walk.getPercent());
        assertEquals(RainBand.POSSIBLE, walk.getBand());
    }

    @Test
    public void theDriestDaylightWalkIsBest() {
        Fixture fixture = fixture();
        // Every departure is wet in 10 members except 03:00.
        for (long end = FIRST_END + HOUR; end <= FIRST_END + 33 * HOUR; end += HOUR) {
            if (end != NEXT_HOUR + 5 * HOUR) {
                for (int m = 0; m < 10; m++) {
                    fixture.wet(m, end, 1.0);
                }
            }
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture.build());

        assertEquals(NEXT_HOUR + 4 * HOUR, advice.getBest().getStartMillis());
        assertFalse(advice.isNoMeaningfulDifference());
    }

    @Test
    public void tiesGoToTheEarlierDeparture() {
        Fixture fixture = fixture();
        for (long end = FIRST_END + HOUR; end <= FIRST_END + 33 * HOUR; end += HOUR) {
            for (int m = 0; m < 10; m++) {
                fixture.wet(m, end, 1.0);
            }
        }
        // Two dry departures, 02:00 and 05:00.
        for (int m = 0; m < 10; m++) {
            fixture.mm[m][fixture.row(NEXT_HOUR + 4 * HOUR)] = 0.0;
            fixture.mm[m][fixture.row(NEXT_HOUR + 7 * HOUR)] = 0.0;
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture.build());

        assertEquals(NEXT_HOUR + 3 * HOUR, advice.getBest().getStartMillis());
    }

    @Test
    public void afterDarkWalksAreListedButNotChosen() {
        // Dark from 23:00 to 06:00 and dry there; half the members rain in every daylight hour.
        Fixture fixture = fixture().night(NEXT_HOUR + HOUR, NEXT_HOUR + 7 * HOUR);
        for (long end = FIRST_END; end <= FIRST_END + 33 * HOUR; end += HOUR) {
            if (fixture.day[fixture.row(end)]) {
                for (int m = 0; m < 20; m++) {
                    fixture.wet(m, end, 1.0);
                }
            }
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture.build());

        DepartureAdvice.Candidate midnight = startingAt(advice, NEXT_HOUR + HOUR);
        assertFalse(midnight.isDaylight());
        assertEquals(0, members(midnight));
        assertTrue(advice.getBest().isDaylight());
        assertFalse(advice.isNoDaylightCandidate());
    }

    @Test
    public void aWalkThatRunsIntoDarkIsNotDaylight() {
        EnsemblePrecipitation ensemble = fixture()
                .night(NEXT_HOUR + 3 * HOUR, NEXT_HOUR + 3 * HOUR).build();

        DepartureAdvice advice = advisor.advise(NOW, 2 * HOUR, ensemble);

        assertTrue(startingAt(advice, NEXT_HOUR).isDaylight());
        assertFalse(startingAt(advice, NEXT_HOUR + HOUR).isDaylight());
    }

    @Test
    public void withNoDaylightWalkTheDriestAfterDarkIsChosen() {
        Fixture fixture = fixture().night(FIRST_END, FIRST_END + 33 * HOUR);
        for (int m = 0; m < 10; m++) {
            fixture.wet(m, NEXT_HOUR, 1.0);
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture.build());

        assertTrue(advice.isNoDaylightCandidate());
        assertEquals(NEXT_HOUR, advice.getBest().getStartMillis());
    }

    @Test
    public void choicesWithinOneMemberAreNoMeaningfulDifference() {
        // Leaving now: 1 wet member; every later hour: dry. A gap of one member is noise.
        EnsemblePrecipitation ensemble = fixture().wet(0, NEXT_HOUR, 1.0).build();

        DepartureAdvice advice = advisor.advise(NOW, 10 * MINUTE, ensemble);

        assertTrue(advice.isNoMeaningfulDifference());
        assertEquals(0, advice.getBestIndex());
    }

    @Test
    public void aGapOfTwoMembersIsADifference() {
        EnsemblePrecipitation ensemble = fixture()
                .wet(0, NEXT_HOUR, 1.0).wet(1, NEXT_HOUR, 1.0).build();

        DepartureAdvice advice = advisor.advise(NOW, 10 * MINUTE, ensemble);

        assertFalse(advice.isNoMeaningfulDifference());
        assertEquals(NEXT_HOUR, advice.getBest().getStartMillis());
    }

    @Test
    public void rainLikelyEverywhereIsFlaggedButTheBestIsStillChosen() {
        Fixture fixture = fixture();
        for (long end = FIRST_END; end <= FIRST_END + 33 * HOUR; end += HOUR) {
            int wetMembers = end == NEXT_HOUR + 3 * HOUR ? 24 : 32;   // 60% once, 80% elsewhere
            for (int m = 0; m < wetMembers; m++) {
                fixture.wet(m, end, 1.0);
            }
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, fixture.build());

        assertTrue(advice.isAllNotAdvisable());
        assertEquals(NEXT_HOUR + 2 * HOUR, advice.getBest().getStartMillis());
        assertEquals(RainBand.LIKELY, advice.getBest().getBand());
    }

    @Test
    public void oneDepartureUnderTheThresholdClearsTheFlag() {
        Fixture fixture = fixture();
        for (long end = FIRST_END; end <= FIRST_END + 33 * HOUR; end += HOUR) {
            int wetMembers = end == NEXT_HOUR + 3 * HOUR ? 23 : 32;   // 57.5% rounds to 58%
            for (int m = 0; m < wetMembers; m++) {
                fixture.wet(m, end, 1.0);
            }
        }

        assertFalse(advisor.advise(NOW, HOUR, fixture.build()).isAllNotAdvisable());
    }

    @Test
    public void shortDataDropsDeparturesWhoseWalkWouldOutrunIt() {
        // 10 rows end at 07:00; a two-hour walk can leave no later than 05:00.
        EnsemblePrecipitation ensemble = new Fixture(40, 10).build();

        List<DepartureAdvice.Candidate> candidates =
                advisor.advise(NOW, 2 * HOUR, ensemble).getCandidates();

        assertEquals(NEXT_HOUR + 6 * HOUR,
                candidates.get(candidates.size() - 1).getStartMillis());
    }

    @Test
    public void noDepartureFittingIsUnusable() {
        EnsemblePrecipitation ensemble = new Fixture(40, 2).build();

        assertUnusable(() -> advisor.advise(NOW, 3 * HOUR, ensemble));
    }

    @Test
    public void tooFewMembersIsUnusable() {
        EnsemblePrecipitation ensemble = new Fixture(DepartureAdvisor.MIN_MEMBERS - 1, 34).build();

        assertUnusable(() -> advisor.advise(NOW, HOUR, ensemble));
    }

    @Test
    public void aMissingValueInAnHourSomeWalkNeedsIsUnusable() {
        EnsemblePrecipitation ensemble = fixture().wet(5, NEXT_HOUR + 4 * HOUR, Double.NaN).build();

        assertUnusable(() -> advisor.advise(NOW, HOUR, ensemble));
    }

    @Test
    public void aMissingValueInAnHourAlreadyOverIsIgnored() {
        // The first row is 21:00-22:00, before NOW; no walk touches it.
        EnsemblePrecipitation ensemble = fixture().wet(5, FIRST_END, Double.NaN).build();

        assertEquals(25, advisor.advise(NOW, HOUR, ensemble).getCandidates().size());
    }

    @Test
    public void eightHoursIsTheLongestWalkAdvised() {
        assertFalse(DepartureAdvisor.isTooLong(8 * HOUR));
        assertTrue(DepartureAdvisor.isTooLong(8 * HOUR + 1));
        try {
            advisor.advise(NOW, 8 * HOUR + 1, fixture().build());
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            // The caller is expected to check isTooLong first.
        }
    }

    // --- Several models ---------------------------------------------------------------------

    private static EnsemblePrecipitation.Model model(String name, int blockHours, double[][] mm) {
        return new EnsemblePrecipitation.Model(name, blockHours, mm);
    }

    private static EnsemblePrecipitation withModels(EnsemblePrecipitation.Model... models) {
        Fixture shape = fixture();
        return new EnsemblePrecipitation(shape.ends, Arrays.asList(models), shape.day, NOW);
    }

    /** 01:00, 02:00 and 03:00 rows: the three-hour block 00:00-03:00 UTC. */
    private static final long BLOCK_START = NEXT_HOUR + HOUR;

    @Test
    public void aThreeHourModelIsJudgedByItsBlockTotal() {
        // A tenth of a millimetre in each hour is dry hour by hour, but the block holds 0.3 mm.
        Fixture three = new Fixture(10, 34);
        for (int i = 1; i <= 3; i++) {
            three.wet(0, BLOCK_START + i * HOUR, 0.1);
        }
        EnsemblePrecipitation ensemble = withModels(model("ecmwf", 3, three.mm));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, ensemble);

        // Leaving at 02:00 for an hour touches only 02:00-03:00, but that is inside the block.
        assertEquals(10, startingAt(advice, BLOCK_START + 2 * HOUR).getPercent());
        assertEquals(10, startingAt(advice, BLOCK_START).getPercent());
        assertEquals(0, startingAt(advice, BLOCK_START + 3 * HOUR).getPercent());
        // The same rows read hourly would be dry throughout.
        EnsemblePrecipitation hourly = withModels(model("icon", 1, three.mm));
        assertEquals(0, startingAt(advisor.advise(NOW, HOUR, hourly), BLOCK_START + 2 * HOUR)
                .getPercent());
    }

    @Test
    public void aBlockCutByTheStartOfTheDataIsReadFromTheHoursPresent() {
        // Data starting at 23:00 holds two of the three hours of the 21:00-00:00 block.
        long[] ends = new long[33];
        for (int h = 0; h < ends.length; h++) {
            ends[h] = FIRST_END_SECOND + (h + 1) * 3_600L;
        }
        double[][] mm = new double[10][33];
        mm[0][0] = 0.1;
        mm[0][1] = 0.1;
        boolean[] day = new boolean[33];
        Arrays.fill(day, true);
        EnsemblePrecipitation ensemble = new EnsemblePrecipitation(ends,
                Collections.singletonList(model("gefs", 3, mm)), day, NOW);

        DepartureAdvice advice = advisor.advise(NOW, 10 * MINUTE, ensemble);

        assertEquals(10, advice.getCandidates().get(0).getPercent());
    }

    @Test
    public void modelsCountEquallyWhateverTheirSize() {
        Fixture icon = new Fixture(40, 34);
        for (int m = 0; m < 40; m++) {
            icon.wet(m, NEXT_HOUR + HOUR, 1.0);
        }
        Fixture ecmwf = new Fixture(10, 34);
        EnsemblePrecipitation ensemble = withModels(
                model("icon", 1, icon.mm), model("ecmwf", 3, ecmwf.mm));

        DepartureAdvice.Candidate walk = startingAt(
                advisor.advise(NOW, HOUR, ensemble), NEXT_HOUR);

        // Pooling the 50 members would give 80%; equal weight gives the midpoint.
        assertEquals(50, walk.getPercent());
    }

    @Test
    public void aModelWithAMissingValueIsLeftOutAndTheRestStillAdvise() {
        Fixture good = new Fixture(40, 34);
        for (int m = 0; m < 10; m++) {
            good.wet(m, NEXT_HOUR + HOUR, 1.0);
        }
        Fixture broken = new Fixture(10, 34).wet(3, NEXT_HOUR + 5 * HOUR, Double.NaN);
        EnsemblePrecipitation ensemble = withModels(
                model("icon", 1, good.mm), model("gefs", 3, broken.mm));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, ensemble);

        assertEquals(Collections.singletonList("icon"), advice.getModels());
        assertEquals(25, startingAt(advice, NEXT_HOUR).getPercent());
    }

    @Test
    public void aModelWithTooFewMembersIsLeftOut() {
        Fixture small = new Fixture(DepartureAdvisor.MIN_MEMBERS - 1, 34);
        for (int m = 0; m < small.mm.length; m++) {
            small.wet(m, NEXT_HOUR + HOUR, 1.0);
        }
        EnsemblePrecipitation ensemble = withModels(
                model("icon", 1, new Fixture(40, 34).mm), model("tiny", 1, small.mm));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, ensemble);

        assertEquals(Collections.singletonList("icon"), advice.getModels());
        assertEquals(0, startingAt(advice, NEXT_HOUR).getPercent());
    }
}
