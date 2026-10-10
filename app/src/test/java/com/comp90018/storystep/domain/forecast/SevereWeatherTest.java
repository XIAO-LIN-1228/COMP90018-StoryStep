package com.comp90018.storystep.domain.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Severe-weather thresholds (D-08 §4.6) and how they change departure advice. */
public class SevereWeatherTest {

    private static final long HOUR = 3_600_000L;
    /** 23:00 UTC: the first row's end. */
    private static final long FIRST_END_SECOND = 1_791_586_800L;
    private static final long FIRST_END = FIRST_END_SECOND * 1000L;
    private static final long NOW = FIRST_END + 30 * 60_000L;
    private static final long NEXT_HOUR = FIRST_END + HOUR;

    private static HourlyForecast.Hour hour(Integer code, Double gust, Double mean, Double feels) {
        return new HourlyForecast.Hour(FIRST_END_SECOND, 0, SkyCondition.fromWmo(code), true,
                code, gust, mean, feels);
    }

    private static SevereWeather.Flag flag(Integer code, Double gust, Double mean, Double feels) {
        return SevereWeather.assess(hour(code, gust, mean, feels));
    }

    private static void assertFlag(SevereWeather.Level level, SevereWeather.Hazard hazard,
                                   SevereWeather.Flag flag) {
        assertEquals(level, flag.getLevel());
        assertEquals(hazard, flag.getHazard());
    }

    // --- Thresholds ------------------------------------------------------------------------------

    @Test
    public void calmMildWeatherIsNotFlagged() {
        assertEquals(SevereWeather.Level.NONE, flag(1, 30.0, 15.0, 18.0).getLevel());
        assertEquals(SevereWeather.Level.NONE, flag(null, null, null, null).getLevel());
    }

    @Test
    public void gustsFollowGaleDamagingAndDestructiveLines() {
        assertEquals(SevereWeather.Level.NONE, flag(0, 61.9, null, null).getLevel());
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.WIND, flag(0, 62.0, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.WIND, flag(0, 90.0, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.DESTRUCTIVE_WIND,
                flag(0, 125.0, null, null));
    }

    @Test
    public void aDamagingMeanWindIsSevereWithoutAGust() {
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.WIND, flag(0, null, 63.0, null));
    }

    @Test
    public void aThunderstormIsCautionUnlessItsGustsOrHailMakeItSevere() {
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.THUNDERSTORM,
                flag(95, 40.0, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.THUNDERSTORM,
                flag(95, 92.0, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.HAIL, flag(96, null, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.HAIL, flag(99, null, null, null));
    }

    @Test
    public void heavySnowIsSevereAndOtherSnowIsCaution() {
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.SNOW, flag(71, null, null, null));
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.SNOW, flag(85, null, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.SNOW, flag(75, null, null, null));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.SNOW, flag(86, null, null, null));
    }

    @Test
    public void feelsLikeFollowsHeatAndColdStressLines() {
        assertEquals(SevereWeather.Level.NONE, flag(0, null, null, 31.9).getLevel());
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.HEAT, flag(0, null, null, 32.0));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.HEAT, flag(0, null, null, 39.0));
        assertEquals(SevereWeather.Level.NONE, flag(0, null, null, 0.1).getLevel());
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.COLD, flag(0, null, null, 0.0));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.COLD, flag(0, null, null, -13.0));
    }

    @Test
    public void theWorstLevelWinsAndThenTheHazardOrder() {
        // Severe heat beats caution thunder; destructive wind is named before hail.
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.HEAT, flag(95, 40.0, null, 40.0));
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.DESTRUCTIVE_WIND,
                flag(96, 130.0, null, null));
    }

    // --- A walk takes its worst hour ---------------------------------------------------------------

    /** 34 hourly rows from FIRST_END with mild weather, changed per test. */
    private static List<HourlyForecast.Hour> calmHours() {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        for (int h = 0; h < 34; h++) {
            rows.add(new HourlyForecast.Hour(FIRST_END_SECOND + h * 3_600L, 0, SkyCondition.CLEAR,
                    true, 0, 20.0, 10.0, 20.0));
        }
        return rows;
    }

    private static HourlyForecast.Hour gusty(long endMillis, double gust) {
        return new HourlyForecast.Hour(endMillis / 1000L, 0, SkyCondition.CLEAR, true, 0, gust,
                20.0, 20.0);
    }

    private static int row(long endMillis) {
        return (int) ((endMillis - FIRST_END) / HOUR);
    }

    @Test
    public void aWalkTakesTheWorstHourItTouches() {
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(NEXT_HOUR + 2 * HOUR), gusty(NEXT_HOUR + 2 * HOUR, 95));
        HourlyForecast forecast = new HourlyForecast(rows, 0L);

        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.WIND,
                SevereWeather.assessWalk(forecast, NEXT_HOUR, NEXT_HOUR + 2 * HOUR));
        assertEquals(SevereWeather.Level.NONE,
                SevereWeather.assessWalk(forecast, NEXT_HOUR, NEXT_HOUR + HOUR).getLevel());
    }

    @Test
    public void aWalkBeyondTheForecastIsUnchecked() {
        HourlyForecast forecast = new HourlyForecast(calmHours().subList(0, 3), 0L);

        assertNull(SevereWeather.assessWalk(forecast, NEXT_HOUR, NEXT_HOUR + 5 * HOUR));
    }

    // --- Advice ---------------------------------------------------------------------------------

    /** A dry, all-daylight ensemble; departures differ only by severe weather. */
    private static EnsemblePrecipitation dryEnsemble() {
        long[] ends = new long[34];
        for (int h = 0; h < ends.length; h++) {
            ends[h] = FIRST_END_SECOND + h * 3_600L;
        }
        ForecastPlace place = new ForecastPlace("UTC", 0, SunTimes.of(
                new long[] {ends[0] - 3_600L}, new long[] {ends[ends.length - 1]}));
        return new EnsemblePrecipitation(ends, Collections.singletonList(
                new EnsemblePrecipitation.Model("ICON", 1, new double[40][34])), place, NOW);
    }

    /** Like {@link #dryEnsemble()} but wet in every hour except the one ending at {@code dryEnd}. */
    private static EnsemblePrecipitation wetExcept(long dryEnd) {
        double[][] mm = new double[40][34];
        for (int m = 0; m < 20; m++) {
            Arrays.fill(mm[m], 1.0);
            mm[m][row(dryEnd)] = 0.0;
        }
        long[] ends = new long[34];
        for (int h = 0; h < ends.length; h++) {
            ends[h] = FIRST_END_SECOND + h * 3_600L;
        }
        ForecastPlace place = new ForecastPlace("UTC", 0, SunTimes.of(
                new long[] {ends[0] - 3_600L}, new long[] {ends[ends.length - 1]}));
        return new EnsemblePrecipitation(ends, Collections.singletonList(
                new EnsemblePrecipitation.Model("ICON", 1, mm)), place, NOW);
    }

    private final DepartureAdvisor advisor = new DepartureAdvisor();

    @Test
    public void theDriestDepartureIsSkippedWhenItIsSevere() {
        // 02:00 is the only dry hour, but a damaging gust blows through it.
        long dryEnd = NEXT_HOUR + 4 * HOUR;
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(dryEnd), gusty(dryEnd, 100));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, wetExcept(dryEnd),
                new HourlyForecast(rows, 0L));

        assertTrue(advice.hasBest());
        assertFalse(advice.getBest().getStartMillis() == dryEnd - HOUR);
        assertTrue(advice.isSevereChecked());
    }

    @Test
    public void aCautionDepartureCanStillBeBest() {
        long dryEnd = NEXT_HOUR + 4 * HOUR;
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(dryEnd), gusty(dryEnd, 70));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, wetExcept(dryEnd),
                new HourlyForecast(rows, 0L));

        assertEquals(dryEnd - HOUR, advice.getBest().getStartMillis());
        assertEquals(SevereWeather.Level.CAUTION, advice.getBest().getSevere().getLevel());
    }

    @Test
    public void severeEveryDaylightHourNamesNoBest() {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        for (HourlyForecast.Hour calm : calmHours()) {
            rows.add(gusty(calm.getEndMillis(), 100));
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, dryEnsemble(),
                new HourlyForecast(rows, 0L));

        assertTrue(advice.isSevereAllDaytime());
        assertFalse(advice.hasBest());
    }

    @Test
    public void withoutAnHourlyForecastSevereWeatherIsUnchecked() {
        DepartureAdvice advice = advisor.advise(NOW, HOUR, dryEnsemble(), null);

        assertFalse(advice.isSevereChecked());
        assertTrue(advice.hasBest());
        assertNull(advice.getBest().getSevere());
    }

    @Test
    public void atEqualRainAFlaggedDepartureGivesWayToAClearOne() {
        // All dry; the rows stamped 22:00 to 02:00 are hot enough for caution. Feels-like is the
        // value at its timestamp, so leaving at 02:00 still sets off in the heat; 03:00 is clear.
        List<HourlyForecast.Hour> rows = calmHours();
        for (int h = 0; h < 5; h++) {
            long end = FIRST_END + h * HOUR;
            rows.set(h, new HourlyForecast.Hour(end / 1000L, 0, SkyCondition.CLEAR, true, 0,
                    20.0, 10.0, 34.0));
        }

        DepartureAdvice advice = advisor.advise(NOW, HOUR, dryEnsemble(),
                new HourlyForecast(rows, 0L));

        assertEquals(SevereWeather.Level.NONE, advice.getBest().getSevere().getLevel());
        assertEquals(FIRST_END + 5 * HOUR, advice.getBest().getStartMillis());
    }

    @Test
    public void clearlyLessRainStillBeatsAClearSky() {
        // The only dry departure carries a caution flag; every other hour is wet.
        long dryEnd = NEXT_HOUR + 4 * HOUR;
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(dryEnd), new HourlyForecast.Hour(dryEnd / 1000L, 0, SkyCondition.CLEAR,
                true, 0, 20.0, 10.0, 34.0));

        DepartureAdvice advice = advisor.advise(NOW, HOUR, wetExcept(dryEnd),
                new HourlyForecast(rows, 0L));

        assertEquals(dryEnd - HOUR, advice.getBest().getStartMillis());
    }

    // --- Review fixes (2026-10-10) ------------------------------------------------------------

    @Test
    public void aHeavyThunderstormIsSevere() {
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.THUNDERSTORM,
                flag(97, null, null, null));
    }

    @Test
    public void aDestructiveMeanWindIsDestructiveWithoutAGust() {
        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.DESTRUCTIVE_WIND,
                flag(0, null, 89.0, null));
    }

    @Test
    public void aStrongMeanWindIsCaution() {
        assertEquals(SevereWeather.Level.NONE, flag(0, null, 49.9, null).getLevel());
        assertFlag(SevereWeather.Level.CAUTION, SevereWeather.Hazard.WIND, flag(0, null, 50.0, null));
    }

    @Test
    public void theRowStampedAtTheStartInstantCounts() {
        // Feels-like is the value at its timestamp: a hot 23:00 reading flags a walk leaving at
        // 23:00 even though the hour it describes, 22:00-23:00, is before the walk.
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(NEXT_HOUR), new HourlyForecast.Hour(NEXT_HOUR / 1000L, 0, SkyCondition.CLEAR,
                true, 0, 20.0, 10.0, 40.0));

        assertFlag(SevereWeather.Level.SEVERE, SevereWeather.Hazard.HEAT,
                SevereWeather.assessWalk(new HourlyForecast(rows, 0L), NEXT_HOUR,
                        NEXT_HOUR + HOUR));
    }

    @Test
    public void severeWeatherIsUncheckedWhenAWalkIsNotCovered() {
        DepartureAdvice advice = advisor.advise(NOW, HOUR, dryEnsemble(),
                new HourlyForecast(calmHours().subList(0, 6), 0L));

        assertFalse(advice.isSevereChecked());
    }

    @Test
    public void dryAllDayNeedsEveryWalkUnflaggedAndUnderFivePercentAsShown() {
        List<HourlyForecast.Hour> rows = calmHours();
        rows.set(row(NEXT_HOUR + 5 * HOUR), gusty(NEXT_HOUR + 5 * HOUR, 70));

        assertTrue(advisor.advise(NOW, HOUR, dryEnsemble(), new HourlyForecast(calmHours(), 0L))
                .isDryThroughout());
        assertFalse(advisor.advise(NOW, HOUR, dryEnsemble(), new HourlyForecast(rows, 0L))
                .isDryThroughout());
    }
}
