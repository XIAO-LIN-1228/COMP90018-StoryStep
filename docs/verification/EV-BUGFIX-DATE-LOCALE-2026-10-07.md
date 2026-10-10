# English date labels on non-English devices

Date: 2026-10-07. Branch: `bugfix`. Requirements: REQ-16.1 / AT-16A date presentation.

## Report and fix

Physical-device feedback reported Chinese month names in the English profile subtitle
and Chinese relative dates in recent journeys. Profile used the default locale;
history used platform relative dates, resource-localized dates and default-locale times.

Profile month/year, history absolute dates, arrival times and recorded-weather times
now explicitly use English formatting. Recent-day labels come from the app's English
resources instead of platform DateUtils. Calendar-day comparisons retain the device's
time zone and handle midnight and daylight-saving changes. Stored timestamps are unchanged.

## Executed verification

- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --console=plain`:
  PASS; 854 JVM tests, zero failures/errors/skips; debug and test APKs built;
  Lint zero errors and 113 warnings (same warning count as the previous bugfix).
- `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.storystep.ui.HistoryTextLocaleTest --console=plain`:
  PASS; all four tests on Pixel_8, API 36.
- Instrumentation sets the test process locale and resource configuration to simplified
  Chinese. It checks the profile subtitle, relative and absolute dates, time labels,
  the one-week boundary, local midnight, Melbourne daylight-saving transitions and
  a time-zone change to Los Angeles. Locale and time zone are restored after each test.
- `git diff --check`: PASS.

## Remaining manual verification

Install the updated build on the reporting phone with its system language left in Chinese.
Check Profile, Your journeys and a journey detail: month names and relative dates should
be English, for example `Exploring since Sep 2026` and `3 days ago`.
The updated build has not yet been retested on a physical phone or other Android API levels.
