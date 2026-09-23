package com.comp90018.storystep.ui.detail;

import java.util.Locale;

/**
 * Tidies OpenStreetMap's opening-hours syntax enough to read, and no further.
 *
 * <p>"Mo-Fr 09:00-17:00; Sa,Su 10:00-16:00" becomes three readable lines. The syntax has
 * conditionals, holidays, seasons and comments this does not touch; anything it does not
 * recognise is shown as it was tagged, because a half-understood rule shown as a confident
 * sentence would be worse than the original.
 *
 * <p>Deliberately no "open now": deciding that needs the whole grammar, and being told a place
 * is open when it is shut is the kind of wrong answer a walker would act on.
 */
public final class OpeningHoursText {

    private static final String[][] DAYS = {
            {"Mo", "Mon"}, {"Tu", "Tue"}, {"We", "Wed"}, {"Th", "Thu"},
            {"Fr", "Fri"}, {"Sa", "Sat"}, {"Su", "Sun"}, {"PH", "Public holidays"}};

    private OpeningHoursText() {
    }

    /** One rule per line, day abbreviations spelled out, ranges with an en dash. */
    public static String format(String openingHours) {
        if (openingHours == null) {
            return null;
        }
        String value = openingHours.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.equalsIgnoreCase("24/7")) {
            return "Open all hours";
        }
        StringBuilder out = new StringBuilder();
        for (String rule : value.split(";")) {
            String line = rule.trim();
            if (line.isEmpty()) {
                continue;
            }
            for (String[] day : DAYS) {
                // Word boundaries: "Sa" must not match inside "Sat" once it has been replaced.
                line = line.replaceAll("\\b" + day[0] + "\\b", day[1]);
            }
            line = line.replace(",", ", ").replaceAll("\\s+", " ").trim();
            // An en dash between times and days reads as a range rather than a minus sign.
            line = line.replaceAll("(?<=[0-9A-Za-z])-(?=[0-9A-Za-z])", "–");
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(line);
        }
        return out.length() == 0 ? null : out.toString();
    }

    /** "Step-free access", "Partly step-free", "No step-free access", or null when unrecorded. */
    public static Integer accessLabel(String wheelchair) {
        if (wheelchair == null) {
            return null;
        }
        switch (wheelchair.trim().toLowerCase(Locale.ROOT)) {
            case "yes":
                return com.comp90018.storystep.R.string.detail_access_yes;
            case "limited":
                return com.comp90018.storystep.R.string.detail_access_limited;
            case "no":
                return com.comp90018.storystep.R.string.detail_access_no;
            default:
                return null;
        }
    }

    /** "Free to enter", "Entry fee", or the charge as tagged; null when unrecorded. */
    public static String feeLabel(String fee, String free, String charged) {
        if (fee == null) {
            return null;
        }
        String value = fee.trim();
        if (value.equalsIgnoreCase("no")) {
            return free;
        }
        if (value.equalsIgnoreCase("yes")) {
            return charged;
        }
        return value;
    }
}
