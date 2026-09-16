package com.comp90018.storystep.ui;

import android.content.res.Resources;
import android.text.format.DateUtils;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.model.MovementSnapshot;
import com.comp90018.storystep.domain.model.MovementState;

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
        return DateUtils.getRelativeTimeSpanString(
                wallClockMillis,
                System.currentTimeMillis(),
                DateUtils.DAY_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE).toString();
    }

    /**
     * A date and a time of day, for a single recorded moment such as an arrival.
     *
     * <p>Formatted with {@link java.text.DateFormat} rather than {@code DateUtils}: the latter
     * needs a {@link android.content.Context}, and passing {@code null} - which this used to do -
     * throws on API 36 the moment a journey detail renders its date.
     */
    public static String formatDateTime(Resources resources, long wallClockMillis) {
        java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
                resources.getConfiguration().getLocales().get(0));
        return format.format(new java.util.Date(wallClockMillis));
    }

    /** Time of day only. Context-free for the same reason as {@link #formatDateTime}. */
    public static String formatTime(long wallClockMillis) {
        return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(new java.util.Date(wallClockMillis));
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
