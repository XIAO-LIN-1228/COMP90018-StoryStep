package com.comp90018.storystep.ui.forecast;

import static org.junit.Assert.assertEquals;

import com.comp90018.storystep.domain.forecast.ForecastPlace;
import com.comp90018.storystep.domain.forecast.SunTimes;

import org.junit.Test;

/** Times on the forecast pages are the forecast location's, not the phone's. */
public class ForecastTextTest {

    /** 2026-10-10 00:00 UTC = 11:00 in Melbourne (AEDT, UTC+11). */
    private static final long MIDNIGHT_UTC = 1_791_590_400_000L;

    /** The JDK puts a narrow no-break space before AM/PM; compare it as a plain space. */
    private static String time(long millis, ForecastPlace place) {
        return ForecastText.time(millis, place).replace('\u202f', ' ');
    }

    private static ForecastPlace place(String id, int offsetSeconds) {
        return new ForecastPlace(id, offsetSeconds, SunTimes.UNKNOWN);
    }

    @Test
    public void timesAreShownInTheLocationsZone() {
        assertEquals("11:00 AM", time(MIDNIGHT_UTC,
                place("Australia/Melbourne", 39_600)));
        assertEquals("8:00 AM", time(MIDNIGHT_UTC, place("Asia/Singapore", 28_800)));
    }

    @Test
    public void anUnknownZoneFallsBackToTheReportedOffset() {
        assertEquals("5:30 AM", time(MIDNIGHT_UTC, place("Nowhere/Invented", 19_800)));
        assertEquals("12:00 AM", time(MIDNIGHT_UTC, ForecastPlace.unknown()));
    }

    @Test
    public void aNamedZoneFollowsDaylightSavingAcrossTheDay() {
        // Melbourne leaves daylight saving at 03:00 AEDT on 2027-04-04 (16:00 UTC the day before).
        // The reported offset (+11) would be wrong after it; the named zone is not.
        long beforeChange = 1_806_764_400_000L;   // 2027-04-03 15:00 UTC = 02:00 AEDT
        long afterChange = 1_806_771_600_000L;    // 2027-04-03 17:00 UTC = 03:00 AEST
        ForecastPlace melbourne = place("Australia/Melbourne", 39_600);
        assertEquals("2:00 AM", time(beforeChange, melbourne));
        assertEquals("3:00 AM", time(afterChange, melbourne));
    }

    @Test
    public void offsetsAreWrittenTheShortWay() {
        assertEquals("UTC+11", ForecastText.utcOffset(39_600_000));
        assertEquals("UTC+5:30", ForecastText.utcOffset(19_800_000));
        assertEquals("UTC-3", ForecastText.utcOffset(-10_800_000));
        assertEquals("UTC", ForecastText.utcOffset(0));
    }
}
