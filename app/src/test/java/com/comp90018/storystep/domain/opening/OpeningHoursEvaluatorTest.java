package com.comp90018.storystep.domain.opening;

import static org.junit.Assert.assertEquals;

import java.time.DayOfWeek;
import org.junit.Test;

public class OpeningHoursEvaluatorTest {

    @Test public void alwaysOpenIsOpenAtEverySampledTime() {
        assertStatus(OpeningStatus.OPEN, "24/7", DayOfWeek.MONDAY, 0, 0);
        assertStatus(OpeningStatus.OPEN, " 24/7 ", DayOfWeek.SUNDAY, 23, 59);
    }

    @Test public void weekdayRangeUsesInclusiveOpeningAndExclusiveClosing() {
        String rule = "Mo-Fr 09:00-17:00";
        assertStatus(OpeningStatus.CLOSED, rule, DayOfWeek.MONDAY, 8, 59);
        assertStatus(OpeningStatus.OPEN, rule, DayOfWeek.MONDAY, 9, 0);
        assertStatus(OpeningStatus.OPEN, rule, DayOfWeek.FRIDAY, 16, 59);
        assertStatus(OpeningStatus.CLOSED, rule, DayOfWeek.FRIDAY, 17, 0);
        assertStatus(OpeningStatus.CLOSED, rule, DayOfWeek.SATURDAY, 12, 0);
    }

    @Test public void commaDaysMultipleRulesAndSplitHoursAreCombined() {
        String rule = "Mo-Fr 09:00-12:00,13:00-17:00; Sa,Su 10:00-16:00";
        assertStatus(OpeningStatus.CLOSED, rule, DayOfWeek.WEDNESDAY, 12, 30);
        assertStatus(OpeningStatus.OPEN, rule, DayOfWeek.WEDNESDAY, 13, 0);
        assertStatus(OpeningStatus.OPEN, rule, DayOfWeek.SUNDAY, 15, 59);
    }

    @Test public void wrappingDayAndOvernightRangesCarryIntoTheNextDay() {
        assertStatus(OpeningStatus.OPEN, "Fr-Mo 10:00-12:00", DayOfWeek.SUNDAY, 11, 0);
        assertStatus(OpeningStatus.OPEN, "Fr 18:00-02:00", DayOfWeek.FRIDAY, 23, 0);
        assertStatus(OpeningStatus.OPEN, "Fr 18:00-02:00", DayOfWeek.SATURDAY, 1, 59);
        assertStatus(OpeningStatus.CLOSED, "Fr 18:00-02:00", DayOfWeek.SATURDAY, 2, 0);
    }

    @Test public void endOfDayIsAcceptedOnlyAsAnEndTime() {
        assertStatus(OpeningStatus.OPEN, "Mo 00:00-24:00", DayOfWeek.MONDAY, 23, 59);
        assertStatus(OpeningStatus.UNKNOWN, "Mo 24:00-24:00", DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "Mo 09:60-17:00", DayOfWeek.MONDAY, 12, 0);
    }

    @Test public void unsupportedOrPartlyParsedRulesNeverBecomeClosed() {
        assertStatus(OpeningStatus.UNKNOWN, null, DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "", DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "Mo-Fr 09:00-17:00; PH off",
                DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "sunrise-sunset", DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "Mo 09:00-17:00 open \"ring the bell\"",
                DayOfWeek.MONDAY, 12, 0);
        assertStatus(OpeningStatus.UNKNOWN, "Mo 09:00-17:00 || appointment",
                DayOfWeek.MONDAY, 12, 0);
    }

    @Test public void invalidMomentIsUnknown() {
        assertEquals(OpeningStatus.UNKNOWN,
                OpeningHoursEvaluator.evaluate("24/7", null, 0));
        assertEquals(OpeningStatus.UNKNOWN,
                OpeningHoursEvaluator.evaluate("24/7", DayOfWeek.MONDAY, -1));
        assertEquals(OpeningStatus.UNKNOWN,
                OpeningHoursEvaluator.evaluate("24/7", DayOfWeek.MONDAY, 1440));
    }

    private static void assertStatus(OpeningStatus expected, String rule, DayOfWeek day,
                                     int hour, int minute) {
        assertEquals(expected, OpeningHoursEvaluator.evaluate(rule, day, hour * 60 + minute));
    }
}
