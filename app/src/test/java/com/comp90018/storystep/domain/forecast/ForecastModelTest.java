package com.comp90018.storystep.domain.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.comp90018.storystep.domain.model.WeatherCondition;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Shape rules for the two forecast series and the rain bands. */
public class ForecastModelTest {

    private static final long FIRST_END_SECOND = 1_791_583_200L;
    private static final long HOUR = 3_600_000L;

    private static HourlyForecast hourly(int hours) {
        List<HourlyForecast.Hour> rows = new ArrayList<>();
        for (int h = 0; h < hours; h++) {
            rows.add(new HourlyForecast.Hour(FIRST_END_SECOND + h * 3_600L, h, WeatherCondition.CLEAR));
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
        assertNull(hour.getCondition());
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
                new double[][] {{0.0}}, new boolean[] {true, true}, 0L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anEmptySeriesIsRejected() {
        new EnsemblePrecipitation(new long[0], new double[0][], new boolean[0], 0L);
    }

    @Test
    public void theEnsembleIsCopiedNotShared() {
        double[][] mm = {{0.0}};
        EnsemblePrecipitation ensemble = new EnsemblePrecipitation(
                new long[] {FIRST_END_SECOND}, mm, new boolean[] {true}, 0L);

        mm[0][0] = 5.0;

        assertEquals(0.0, ensemble.millimetres(0, 0), 0.0);
        assertEquals(FIRST_END_SECOND * 1000L, ensemble.coverageEndMillis());
    }
}
