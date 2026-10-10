package com.comp90018.storystep.ui;

import android.content.res.Resources;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Formats stored history for display.
 *
 * <p>Here rather than in {@code domain} because every method needs {@link Resources} or the
 * platform's date formatting, and here rather than in a Fragment because the list, the detail
 * screen and the accessibility descriptions must all say the same thing about the same session.
 */
public final class HistoryText {

    private HistoryText() {
        // Utility class.
    }

    /** A calendar date, with today and yesterday named rather than dated. */
    public static String formatDate(Resources resources, long wallClockMillis) {
        return formatDate(resources, wallClockMillis, System.currentTimeMillis());
    }

    static String formatDate(Resources resources, long wallClockMillis, long nowMillis) {
        // Compare local calendar days, including across midnight and daylight-saving changes.
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = Instant.ofEpochMilli(wallClockMillis).atZone(zone).toLocalDate();
        LocalDate today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        long days = ChronoUnit.DAYS.between(date, today);
        if (days == 0) {
            return resources.getString(R.string.history_date_today);
        }
        if (days == 1) {
            return resources.getString(R.string.history_date_yesterday);
        }
        if (days == -1) {
            return resources.getString(R.string.history_date_tomorrow);
        }
        if (days > 1 && days < 7) {
            return resources.getQuantityString(R.plurals.history_days_ago, (int) days, days);
        }
        if (days < -1 && days > -7) {
            return resources.getQuantityString(R.plurals.history_days_ahead, (int) -days, -days);
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.ENGLISH)
                .format(new Date(wallClockMillis));
    }

    /** The English UI keeps its month names even when the phone uses another language. */
    public static String formatMonthYear(long wallClockMillis) {
        return new SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
                .format(new Date(wallClockMillis));
    }

    /**
     * A date and a time of day, for a single recorded moment such as an arrival.
     *
     * <p>Use the UI's English language and the device's time zone. DateFormat is context-free;
     * passing a null Context to DateUtils used to crash journey details on API 36.
     */
    public static String formatDateTime(Resources resources, long wallClockMillis) {
        DateFormat format = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH);
        return format.format(new Date(wallClockMillis));
    }

    /** Time of day only. Context-free for the same reason as {@link #formatDateTime}. */
    public static String formatTime(long wallClockMillis) {
        return DateFormat.getTimeInstance(DateFormat.SHORT, Locale.ENGLISH)
                .format(new Date(wallClockMillis));
    }

    /**
     * Collecting time, as a duration.
     *
     * <p>A walk that recorded less than a minute is named rather than rounded to "0 min", which
     * reads as a bug rather than as a very short walk.
     */
    public static String formatDuration(Resources resources, long durationMillis) {
        long safeMillis = Math.max(0L, durationMillis);
        long totalMinutes = TimeUnit.MILLISECONDS.toMinutes(safeMillis);
        if (totalMinutes < 1L) {
            return resources.getString(R.string.history_duration_under_a_minute);
        }
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours == 0L) {
            return resources.getString(R.string.history_duration_minutes, (int) minutes);
        }
        return resources.getString(R.string.history_duration_hours, (int) hours, (int) minutes);
    }

    public static String formatVisitCount(Resources resources, int visitCount) {
        return resources.getQuantityString(R.plurals.history_visit_count, visitCount, visitCount);
    }

    /**
     * The movement recorded for a session.
     *
     * <p>{@code UNAVAILABLE} is reported as "not recorded" rather than as zero movement. They are
     * different facts: one says this phone could not measure, the other says the user stood still.
     */
    public static String formatMovement(Resources resources, MovementSnapshot movement) {
        if (movement == null || movement.getState() == MovementState.UNAVAILABLE) {
            return resources.getString(R.string.history_movement_unavailable);
        }
        return resources.getString(R.string.history_movement_format,
                formatDuration(resources, movement.getMovingMillis()),
                formatDuration(resources, movement.getStationaryMillis()));
    }

    /** The accessibility description for a route sketch of {@code segmentCount} spans. */
    public static String describeRoute(Resources resources, int segmentCount) {
        return resources.getQuantityString(
                R.plurals.history_route_segments, segmentCount, segmentCount);
    }
}
