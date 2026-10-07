package com.comp90018.storystep.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.comp90018.storystep.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.TimeZone;

/** Regression for English UI mixed with Chinese platform dates; no stored user data is changed. */
@RunWith(AndroidJUnit4.class)
public class HistoryTextLocaleTest {
    private Locale originalLocale;
    private Locale originalDisplayLocale;
    private Locale originalFormatLocale;
    private TimeZone originalTimeZone;
    private Resources resources;

    @Before public void useChineseLocaleAndMelbourneTime() {
        originalLocale = Locale.getDefault();
        originalDisplayLocale = Locale.getDefault(Locale.Category.DISPLAY);
        originalFormatLocale = Locale.getDefault(Locale.Category.FORMAT);
        originalTimeZone = TimeZone.getDefault();
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        TimeZone.setDefault(TimeZone.getTimeZone("Australia/Melbourne"));
        Context context = ApplicationProvider.getApplicationContext();
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.setLocale(Locale.SIMPLIFIED_CHINESE);
        resources = context.createConfigurationContext(configuration).getResources();
    }

    @After public void restoreLocaleAndTimeZone() {
        Locale.setDefault(originalLocale);
        Locale.setDefault(Locale.Category.DISPLAY, originalDisplayLocale);
        Locale.setDefault(Locale.Category.FORMAT, originalFormatLocale);
        TimeZone.setDefault(originalTimeZone);
    }

    @Test public void profileAndRecordedDatesStayEnglish() {
        long recorded = localTime("2026-09-18T15:30:00");
        assertEquals("Exploring since Sep 2026 · stored on this device",
                resources.getString(R.string.profile_since, HistoryText.formatMonthYear(recorded)));
        assertEnglishAfternoon(HistoryText.formatDateTime(resources, recorded));
        assertTrue(HistoryText.formatDateTime(resources, recorded).contains("Sep 18, 2026"));
        assertEnglishAfternoon(HistoryText.formatTime(recorded));
        assertEquals("Sep 18, 2026",
                HistoryText.formatDate(resources, recorded, localTime("2026-10-07T12:00:00")));
    }

    @Test public void relativeDatesStayEnglishIncludingWeekBoundary() {
        long now = localTime("2026-10-07T12:00:00");
        assertEquals("Today", HistoryText.formatDate(resources, now, now));
        assertEquals("Yesterday", HistoryText.formatDate(resources,
                localTime("2026-10-06T15:00:00"), now));
        assertEquals("3 days ago", HistoryText.formatDate(resources,
                localTime("2026-10-04T12:00:00"), now));
        assertEquals("6 days ago", HistoryText.formatDate(resources,
                localTime("2026-10-01T12:00:00"), now));
        assertEquals("Sep 30, 2026", HistoryText.formatDate(resources,
                localTime("2026-09-30T12:00:00"), now));
        assertEquals("Tomorrow", HistoryText.formatDate(resources,
                localTime("2026-10-08T12:00:00"), now));
        assertEquals("In 3 days", HistoryText.formatDate(resources,
                localTime("2026-10-10T12:00:00"), now));
    }

    @Test public void relativeDatesUseCalendarDaysAcrossMidnightAndDaylightSaving() {
        assertEquals("Yesterday", HistoryText.formatDate(resources,
                localTime("2026-10-06T23:59:00"), localTime("2026-10-07T00:01:00")));
        // Melbourne's clock jumps forwards on October 4: these calendar days are 23 hours apart.
        assertEquals("Yesterday", HistoryText.formatDate(resources,
                localTime("2026-10-03T12:00:00"), localTime("2026-10-04T12:00:00")));
        // The April clock change makes this single calendar date longer than 24 hours.
        assertEquals("Today", HistoryText.formatDate(resources,
                localTime("2026-04-05T00:01:00"), localTime("2026-04-05T23:59:00")));
    }

    @Test public void englishDatesStillFollowDeviceTimeZone() {
        long recorded = Instant.parse("2026-09-30T15:30:00Z").toEpochMilli();
        assertEquals("Oct 2026", HistoryText.formatMonthYear(recorded));
        assertTrue(HistoryText.formatDateTime(resources, recorded).contains("Oct 1, 2026"));
        assertTrue(HistoryText.formatTime(recorded).contains("1:30"));
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        assertEquals("Sep 2026", HistoryText.formatMonthYear(recorded));
        assertTrue(HistoryText.formatDateTime(resources, recorded).contains("Sep 30, 2026"));
        assertTrue(HistoryText.formatTime(recorded).contains("8:30"));
    }

    private static long localTime(String dateTime) {
        return LocalDateTime.parse(dateTime).atZone(ZoneId.of("Australia/Melbourne"))
                .toInstant().toEpochMilli();
    }

    private static void assertEnglishAfternoon(String text) {
        assertTrue(text, text.contains("3:30"));
        assertTrue(text, text.contains("PM"));
    }
}
