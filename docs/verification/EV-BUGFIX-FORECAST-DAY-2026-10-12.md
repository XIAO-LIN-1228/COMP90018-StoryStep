# Hourly rain forecast day labels

- Date: 2026-10-12
- Branch: `fix/forecast-day-label`, based on `main` at `382924e`
- Design: [D-08 §3](../decisions/D-08-departure-window-advice.md)

## Problem

The hourly "Rain forecast" page lists the next 24 hours, labelled by interval only. Rows after
local midnight, such as "12:00 AM–1:00 AM", carried no day and read as earlier than the current
time. It is the same fault as the departure labels fixed in
[EV-BUGFIX-DEPARTURE-DAY-2026-10-12](EV-BUGFIX-DEPARTURE-DAY-2026-10-12.md).

## Cause

The hours themselves were correct. `ForecastViewModel` keeps the hours from
`HourlyForecast.upcoming(now, 24)`: the current hour, then the following ones. Only the labels
were at fault: `RainRowAdapter` showed `ForecastText.interval()` with no day, and the UI state did
not record the instant the hours were counted from.

## Change

- `ForecastUiState.getReferenceMillis()` records the instant the hours were counted from: the
  same `clock.wallClockMillis()` passed to `upcoming`.
- `ForecastText.dayInterval()` labels an hour that starts on a later local day of the forecast
  location as "Tomorrow 12:00 AM–1:00 AM", or with a weekday beyond that. It shares the day logic
  with `dayTime()` through `ForecastText.dayOffset()`. Today's rows are unchanged.
- The interval keeps no-break spaces and a word joiner after the dash, so a label too wide for the
  row wraps after "Tomorrow". The row's time cell (`item_rain_row`, shared with departure advice)
  now allows two lines. With one line, the 12-hour label "Tomorrow 2:00 AM–3:00 AM" was cut to
  "Tomorrow 2:00 AM–3:00" on the emulator.
- The row sets no content description of its own, so TalkBack reads the visible label.

## Verification

- `./gradlew testDebugUnitTest assembleDebug lintDebug`: 1013 JVM tests, 0 failures, errors or
  skips. The debug APK built. Lint reported 0 errors and 118 warnings, the same as `main`, and none
  in the changed files.
- New tests:
  - `ForecastTextTest.anHourAfterLocalMidnightIsOnTheNextDay`;
  - `ForecastTextTest.anIntervalHasNoLineBreakOfItsOwn`;
  - `ForecastViewModelTest.contentRemembersTheInstantItCountedFrom`.
- Emulator (API 37, `Australia/Melbourne`, system 12-hour time, live data, 15:03–15:05):
  - rows read "3:00 PM–4:00 PM" … "11:00 PM–12:00 AM", then "Tomorrow 12:00 AM–1:00 AM" …
    "Tomorrow 2:00 PM–3:00 PM";
  - most "Tomorrow" labels wrap to two lines after the day, none cut off; "Tomorrow 1:00 AM–2:00
    AM" fits on one line.
- NOT_RUN: physical phone, 24-hour system time on this build, TalkBack and 200% font.
