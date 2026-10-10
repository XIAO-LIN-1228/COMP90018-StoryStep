package com.comp90018.storystep.domain.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Shape rules for the two forecast series and the rain bands. */
public class ForecastModelTest {

    private static final long FIRST_END_SECOND = 1_791_583_200L;
    private static final long HOUR = 3_600_000L;

    private static HourlyForecast hourly(int hours) {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        for (int h = 0; h < hours; h++) {
            rows.add(new HourlyForecast.Hour(FIRST_END_SECOND + h * 3_600L, h, SkyCondition.CLEAR));
        }
        return new HourlyForecast(rows, 0L);
    }

    @Test
    public void bandsFollowTheNwsBoundaries() {
        assertEquals(RainBand.LOW, RainBand.forPercent(0));
        assertEquals(RainBand.LOW, RainBand.forPercent(29));
        assertEquals(RainBand.POSSIBLE, RainBand.forPercent(30));
        assertEquals(RainBand.POSSIBLE, RainBand.forPercent(59));
        assertEquals(RainBand.LIKELY, RainBand.forPercent(60));
        assertEquals(RainBand.LIKELY, RainBand.forPercent(79));
        assertEquals(RainBand.EXPECTED, RainBand.forPercent(80));
        assertEquals(RainBand.EXPECTED, RainBand.forPercent(100));
    }

    @Test(expected = IllegalArgumentException.class)
    public void aPercentOverOneHundredIsRejected() {
        RainBand.forPercent(101);
    }

    @Test
    public void aRowCoversTheHourBeforeItsTimestamp() {
        HourlyForecast.Hour first = hourly(1).getHours().get(0);

        assertEquals(FIRST_END_SECOND * 1000L - HOUR, first.getStartMillis());
        assertEquals(FIRST_END_SECOND * 1000L, first.getEndMillis());
    }

    @Test
    public void upcomingKeepsTheHourInProgressAndDropsThoseOver() {
        HourlyForecast forecast = hourly(26);
        // 22:28: the 21:00-22:00 row is over, 22:00-23:00 is in progress.
        long now = FIRST_END_SECOND * 1000L + 28 * 60_000L;

        List<HourlyForecast.Hour> upcoming = forecast.upcoming(now, 24);

        assertEquals(24, upcoming.size());
        assertEquals(FIRST_END_SECOND + 3_600L, upcoming.get(0).getEndEpochSecond());
    }

    @Test
    public void anHourEndingExactlyNowIsOver() {
        long now = FIRST_END_SECOND * 1000L;

        assertEquals(FIRST_END_SECOND + 3_600L,
                hourly(3).upcoming(now, 24).get(0).getEndEpochSecond());
    }

    @Test
    public void missingFieldsStayMissing() {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        rows.add(new HourlyForecast.Hour(FIRST_END_SECOND, null, null));

        HourlyForecast.Hour hour = new HourlyForecast(rows, 0L).getHours().get(0);

        assertNull(hour.getPrecipitationProbability());
        assertNull(hour.getSky());
    }

    @Test
    public void hoursMustBeConsecutive() {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        rows.add(new HourlyForecast.Hour(FIRST_END_SECOND, 0, null));
        rows.add(new HourlyForecast.Hour(FIRST_END_SECOND + 7_200L, 0, null));
        try {
            new HourlyForecast(rows, 0L);
            fail("expected rejection");
        } catch (IllegalArgumentException expected) {
            // A gap would misplace every later hour.
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void aProbabilityOutOfRangeIsRejected() {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        rows.add(new HourlyForecast.Hour(FIRST_END_SECOND, 120, null));
        new HourlyForecast(rows, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aMemberOfTheWrongLengthIsRejected() {
        new EnsemblePrecipitation(new long[] {FIRST_END_SECOND, FIRST_END_SECOND + 3_600L},
                Collections.singletonList(new EnsemblePrecipitation.Model("m", 1,
                        new double[][] {{0.0}})),
                ForecastPlace.unknown(), 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptySeriesIsRejected() {
        new EnsemblePrecipitation(new long[0],
                Collections.singletonList(new EnsemblePrecipitation.Model("m", 1, new double[0][])),
                ForecastPlace.unknown(), 0L);
    }

    @Test
    public void theEnsembleIsCopiedNotShared() {
        double[][] mm = {{0.0}};
        EnsemblePrecipitation ensemble = new EnsemblePrecipitation(
                new long[] {FIRST_END_SECOND},
                Collections.singletonList(new EnsemblePrecipitation.Model("m", 1, mm)),
                ForecastPlace.unknown(), 0L);

        mm[0][0] = 5.0;

        assertEquals(0.0, ensemble.getModels().get(0).millimetres(0, 0), 0.0);
        assertEquals(FIRST_END_SECOND * 1000L, ensemble.coverageEndMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void onlyHourlyAndThreeHourModelsAreSupported() {
        new EnsemblePrecipitation.Model("m", 6, new double[0][]);
    }

    @Test
    public void threeHourBlocksEndOnMultiplesOfThreeUtcHours() {
        // FIRST_END_SECOND is 22:00 UTC; 22:00 and 00:00 share the 21:00-00:00 block.
        long at22 = FIRST_END_SECOND;
        assertEquals(HourlyBuckets.block(at22, 3), HourlyBuckets.block(at22 + 2 * 3_600L, 3));
        assertEquals(HourlyBuckets.block(at22, 3) + 1, HourlyBuckets.block(at22 + 3 * 3_600L, 3));
        long block = HourlyBuckets.block(at22, 3);
        assertEquals((FIRST_END_SECOND - 3_600L) * 1000L, HourlyBuckets.blockStartMillis(block, 3));
        assertEquals((FIRST_END_SECOND + 2 * 3_600L) * 1000L, HourlyBuckets.blockEndMillis(block, 3));
    }

    // --- Sun times ---------------------------------------------------------------------------

    /** Melbourne, 2026-10-10: sunrise 06:42:55, sunset 19:32:26 local (UTC+11). */
    private static final long RISE = 1_791_574_975L;
    private static final long SET = 1_791_621_146L;
    private static final SunTimes MELBOURNE = SunTimes.of(
            new long[] {RISE, RISE + 86_313L}, new long[] {SET, SET + 86_456L});

    @Test
    public void daylightRunsFromSunriseToSunset() {
        assertEquals(Boolean.FALSE, MELBOURNE.isDaylight(RISE * 1000L - 1));
        assertEquals(Boolean.TRUE, MELBOURNE.isDaylight(RISE * 1000L));
        assertEquals(Boolean.TRUE, MELBOURNE.isDaylight(SET * 1000L - 1));
        assertEquals(Boolean.FALSE, MELBOURNE.isDaylight(SET * 1000L));
    }

    @Test
    public void farOutsideTheCoveredDaysIsUnknown() {
        assertNull(MELBOURNE.isDaylight(RISE * 1000L - 2 * 86_400_000L));
        assertNull(SunTimes.UNKNOWN.isDaylight(RISE * 1000L));
    }

    @Test
    public void aSpanMustFitInsideOneDay() {
        long rise = RISE * 1000L;
        long set = SET * 1000L;
        assertTrue(MELBOURNE.isDaylightThroughout(rise, set));
        assertFalse(MELBOURNE.isDaylightThroughout(rise - 1, set));
        // Across the night between two days.
        assertFalse(MELBOURNE.isDaylightThroughout(set - 1, rise + 86_400_000L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sunsetBeforeSunriseIsRejected() {
        SunTimes.of(new long[] {SET}, new long[] {RISE});
    }

    // --- Sky -----------------------------------------------------------------------------------

    @Test
    public void wmoCodesMapToSevenSkies() {
        assertEquals(SkyCondition.CLEAR, SkyCondition.fromWmo(0));
        assertEquals(SkyCondition.PARTLY_CLOUDY, SkyCondition.fromWmo(1));
        assertEquals(SkyCondition.PARTLY_CLOUDY, SkyCondition.fromWmo(2));
        assertEquals(SkyCondition.OVERCAST, SkyCondition.fromWmo(3));
        assertEquals(SkyCondition.FOG, SkyCondition.fromWmo(45));
        assertEquals(SkyCondition.FOG, SkyCondition.fromWmo(48));
        assertEquals(SkyCondition.RAIN, SkyCondition.fromWmo(51));
        assertEquals(SkyCondition.RAIN, SkyCondition.fromWmo(67));
        assertEquals(SkyCondition.RAIN, SkyCondition.fromWmo(81));
        assertEquals(SkyCondition.SNOW, SkyCondition.fromWmo(73));
        assertEquals(SkyCondition.SNOW, SkyCondition.fromWmo(86));
        assertEquals(SkyCondition.THUNDERSTORM, SkyCondition.fromWmo(95));
        assertEquals(SkyCondition.THUNDERSTORM, SkyCondition.fromWmo(99));
        assertEquals(SkyCondition.UNKNOWN, SkyCondition.fromWmo(null));
        assertEquals(SkyCondition.UNKNOWN, SkyCondition.fromWmo(4));
    }

    @Test
    public void hourAtFindsTheHourContainingAnInstant() {
        HourlyForecast forecast = hourly(3);
        long firstStart = FIRST_END_SECOND * 1000L - HOUR;

        assertEquals(FIRST_END_SECOND, forecast.hourAt(firstStart).getEndEpochSecond());
        assertEquals(FIRST_END_SECOND + 3_600L,
                forecast.hourAt(FIRST_END_SECOND * 1000L).getEndEpochSecond());
        assertNull(forecast.hourAt(firstStart - 1));
        assertNull(forecast.hourAt((FIRST_END_SECOND + 2 * 3_600L) * 1000L));
    }
}
