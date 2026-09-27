package com.comp90018.storystep.domain.opening;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates a deliberately small, fully parsed subset of OpenStreetMap opening-hours rules.
 *
 * <p>Unsupported or partly understood input is {@link OpeningStatus#UNKNOWN}; it is never treated
 * as closed. This avoids making a confident claim when a rule contains holidays, dates,
 * conditionals, comments or other parts of the wider opening_hours grammar.
 */
public final class OpeningHoursEvaluator {

    private static final String DAY = "(?:Mo|Tu|We|Th|Fr|Sa|Su)";
    private static final Pattern RULE = Pattern.compile(
            "^(" + DAY + "(?:-" + DAY + ")?(?:\\s*,\\s*" + DAY
                    + "(?:-" + DAY + ")?)*)\\s+(.+)$");
    private static final Pattern TIME_RANGE = Pattern.compile(
            "^(\\d{2}):(\\d{2})\\s*-\\s*(\\d{2}):(\\d{2})$");

    private OpeningHoursEvaluator() {
    }

    /** Evaluates the complete rule for the supplied local day and minute. */
    public static OpeningStatus evaluate(String openingHours, DayOfWeek day, int minuteOfDay) {
        if (openingHours == null || day == null || minuteOfDay < 0 || minuteOfDay >= 24 * 60) {
            return OpeningStatus.UNKNOWN;
        }
        String value = openingHours.trim();
        if (value.isEmpty()) {
            return OpeningStatus.UNKNOWN;
        }
        if (value.equalsIgnoreCase("24/7")) {
            return OpeningStatus.OPEN;
        }

        List<WeeklyRange> ranges = new ArrayList<>();
        String[] rules = value.split(";", -1);
        for (String rawRule : rules) {
            Matcher rule = RULE.matcher(rawRule.trim());
            if (!rule.matches()) {
                return OpeningStatus.UNKNOWN;
            }
            EnumSet<DayOfWeek> days = parseDays(rule.group(1));
            if (days == null) {
                return OpeningStatus.UNKNOWN;
            }
            String[] rawRanges = rule.group(2).split(",", -1);
            if (rawRanges.length == 0) {
                return OpeningStatus.UNKNOWN;
            }
            for (String rawRange : rawRanges) {
                int[] times = parseTimeRange(rawRange.trim());
                if (times == null) {
                    return OpeningStatus.UNKNOWN;
                }
                ranges.add(new WeeklyRange(days, times[0], times[1]));
            }
        }

        DayOfWeek previousDay = day.minus(1);
        for (WeeklyRange range : ranges) {
            if (range.isOpen(day, previousDay, minuteOfDay)) {
                return OpeningStatus.OPEN;
            }
        }
        return OpeningStatus.CLOSED;
    }

    private static EnumSet<DayOfWeek> parseDays(String value) {
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (String part : value.split("\\s*,\\s*", -1)) {
            String[] endpoints = part.split("-", -1);
            if (endpoints.length < 1 || endpoints.length > 2) {
                return null;
            }
            DayOfWeek start = parseDay(endpoints[0]);
            DayOfWeek end = endpoints.length == 1 ? start : parseDay(endpoints[1]);
            if (start == null || end == null) {
                return null;
            }
            DayOfWeek cursor = start;
            while (true) {
                result.add(cursor);
                if (cursor == end) {
                    break;
                }
                cursor = cursor.plus(1);
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static DayOfWeek parseDay(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "mo": return DayOfWeek.MONDAY;
            case "tu": return DayOfWeek.TUESDAY;
            case "we": return DayOfWeek.WEDNESDAY;
            case "th": return DayOfWeek.THURSDAY;
            case "fr": return DayOfWeek.FRIDAY;
            case "sa": return DayOfWeek.SATURDAY;
            case "su": return DayOfWeek.SUNDAY;
            default: return null;
        }
    }

    private static int[] parseTimeRange(String value) {
        Matcher matcher = TIME_RANGE.matcher(value);
        if (!matcher.matches()) {
            return null;
        }
        int start = parseTime(matcher.group(1), matcher.group(2), false);
        int end = parseTime(matcher.group(3), matcher.group(4), true);
        if (start < 0 || end < 0 || start == end) {
            return null;
        }
        return new int[] {start, end};
    }

    private static int parseTime(String hourText, String minuteText, boolean allowEndOfDay) {
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(minuteText);
        if (minute > 59 || hour > 24 || (hour == 24 && (!allowEndOfDay || minute != 0))) {
            return -1;
        }
        return hour * 60 + minute;
    }

    private static final class WeeklyRange {
        private final EnumSet<DayOfWeek> days;
        private final int start;
        private final int end;

        private WeeklyRange(EnumSet<DayOfWeek> days, int start, int end) {
            this.days = EnumSet.copyOf(days);
            this.start = start;
            this.end = end;
        }

        private boolean isOpen(DayOfWeek day, DayOfWeek previousDay, int minute) {
            if (end > start) {
                return days.contains(day) && minute >= start && minute < end;
            }
            return (days.contains(day) && minute >= start)
                    || (days.contains(previousDay) && minute < end);
        }
    }
}
