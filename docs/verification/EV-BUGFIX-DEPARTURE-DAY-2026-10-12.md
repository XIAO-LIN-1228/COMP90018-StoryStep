# Departure advice day labels

- Date: 2026-10-12
- Branch: `fix/departure-day-label`, based on `main` at `afdd76d`
- Design: [D-08 §4.5](../decisions/D-08-departure-window-advice.md)

## Problem

On a physical phone, "Best time to leave" suggested a departure that looked earlier than the
current time, as soon as the page opened.

## Cause

The departures themselves were correct. `DepartureAdvisor` offers "Now" and then each full hour
up to 24 hours ahead, so every departure is at or after the current instant. The live Open-Meteo
ensemble response was checked on 2026-10-12: zone `Australia/Melbourne`, first row at the
current hour.

The labels were the fault: rows and the summary card showed only the time of day. The list
crosses midnight, and the ranking prefers daylight, so the best departure is often tomorrow
morning. At 13:48 on the emulator the best departure was tomorrow at 07:00, because rain was
forecast for the afternoon. The card read "Best: leave at 07:00" and the rows after midnight
read "02:00", "03:00" and so on, which look like times already past.

The time format itself was checked as a possible cause. `java.text.DateFormat` follows the
system 12/24-hour setting: 12-hour shows "5:00 PM" and 24-hour shows "17:00". Neither drops
AM/PM from a 12-hour time.

## Change

- `DepartureAdvice.getComputedAtMillis()` records the instant the departures were counted from.
- `ForecastText.dayTime()` labels a time on a later local day of the forecast location as
  "Tomorrow 07:00", or with a weekday beyond that. `ForecastText.dayOffset()` computes the day
  difference in the location's zone.
- The row labels and their accessibility description use it. The summary card reads "Best: leave
  tomorrow at 07:00". Today's times and "Now" are unchanged.

## Verification

- `./gradlew testDebugUnitTest assembleDebug lintDebug`: 1010 JVM tests, 0 failures, errors or
  skips. The debug APK built. Lint reported 0 errors and 118 warnings, the same as `main`.
- New tests:
  - `ForecastTextTest.aDepartureAfterLocalMidnightIsOnTheNextDay`;
  - `ForecastTextTest.theDayIsTheLocationsNotUtcs`;
  - `DepartureAdvisorTest.adviceRemembersTheInstantItCountedFrom`.
- Emulator (API 37, `Australia/Melbourne`, system 24-hour time, live data, 13:48–13:51):
  - the card read "Best: leave tomorrow at 07:00 · about 1% chance of rain";
  - rows read "Now", "14:00" … "23:00", then "Tomorrow 00:00" … "Tomorrow 13:00";
  - the best row was tinted.
- NOT_RUN: the physical phone on which the problem was reported.
