# Rain forecast and departure-window advice evidence — 2026-10-10

Branch: `feature/departure-window-advice` (draft PR #39), rebased onto `main@ede3f7e`.
Design and decisions: [D-08](../decisions/D-08-departure-window-advice.md).

## Scope

- **Rain forecast page** (button on the Explore weather row): the next 24 hours of Open-Meteo's
  hourly chance of rain for the reference area, with a sky icon per hour and severe-weather pills.
- **Best time to leave page** (button on the Plan page when it has a stop): departures over the
  next 24 hours ranked by the chance of rain during the planned walk.
  - The chance averages the ICON, ECMWF and GFS ensembles. A member counts as wet when some hour
    the walk touches rains at 0.2 mm/h or more.
  - Daylight walks are preferred, and severe weather is flagged in two levels.
- **Unchanged:** no Room schema change, no new permission, no new library. Location is used only
  as the existing reference origin. Recommendations (REQ-21) are untouched.
- **Debug builds only:** a scenario switch that replaces the forecast with seven simulated days.
  Release builds have a pass-through.

## Automated results

Executed from the repository root on 2026-10-10, after the rebase:

```text
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
BUILD SUCCESSFUL
JVM tests: 1007, failures: 0, errors: 0
Android Lint: 0 errors, 118 warnings
```

- **New Lint warnings:** the feature adds two, Overdraw on the root background of
  `fragment_forecast.xml` and `fragment_advice.xml`, the same pattern as the existing pages.
- **Other Lint differences:** the rest of the difference from the pre-rebase count (113) is five
  newer-version notices for test libraries. Their bump is in draft PR #30, not here.
- **JVM coverage:**
  - recorded Melbourne and Singapore Open-Meteo responses run end to end;
  - end-of-hour alignment;
  - the 0.2 mm/h threshold;
  - per-model member counting and equal-weight averaging;
  - three-hourly rows judged as hourly rates, including a walk crossing a block edge;
  - daylight ranking, the 5-point no-difference rule and the 60% not-advisable flag;
  - dry-all-day on the rounded figure;
  - severe-weather thresholds and the start-instant reading;
  - polar day and night;
  - timezone formatting;
  - retry and cache behaviour;
  - the advice ViewModel's loading, failure, retry, expiry and resume paths.

## Emulator results

Device: Android emulator `Medium_Phone_API_37.0`, debug build. Live Open-Meteo, GPS fixed at
Melbourne (-37.7963, 144.9614). Screenshots taken with a scenario on show simulated weather.

| Check | Outcome |
|---|---|
| Rain forecast page on live data: Melbourne time note, hour ranges, sky icons | PASS |
| Best time to leave from a one-stop plan: area line, stay chips, dry-all-day card, "<1%" | PASS |
| Scenario `afternoon_showers`: 48–71% from 11 AM to 4 PM | PASS |
| Scenario `rain_all_day`: "Rain is likely whenever you go" | PASS |
| Scenario `dry_only_at_night`: best is the first dry evening hour | PASS |
| Scenario `models_disagree`: mid-range figures | PASS |
| Scenario `storm_afternoon`: caution thunderstorm, severe gusts 95 km/h, severe hail pills | PASS |
| Scenario `heat_day`: caution "Feels like 33 °C"; an unflagged departure chosen | PASS |
| Scenario `gale_all_day`: "Severe weather forecast for every daytime departure" | PASS |
| Stay 60 min: walk about 1 h 2 min, ranking recomputed | PASS |
| Rotation keeps the chosen stay and the advice | PASS |
| Landscape: the header scrolls away with the list | PASS after fix |
| Offline: the offline message and Try again; Try again after reconnecting loads | PASS |
| Rebased build: both pages, a severe scenario and landscape re-checked | PASS |

## Defects found and fixed during acceptance

- **Rain accumulated over the walk read every short walk as dry.** This rule came from the second
  review. A seven-minute walk collects under 0.2 mm in almost any rain, so `rain_all_day` showed
  "Dry all day".
  - Fix: replaced by the hourly-rate rule (D-08 §4.3).
- **The departure list had zero height in landscape.** The fixed header and footer filled the
  screen, and a large font scale on a small phone would do the same.
  - Fix: the header now sits in a scrolling `AppBarLayout`.

## Observations, not defects

- When the best departure is the first daylight hour, the list opens scrolled to it, so the
  evening rows ("Now" onwards) sit above and need a scroll up. The summary card names the best
  time, so nothing is hidden, but a first-time reader may not see "Now".

## Still to run

- A physical device, including the API 26 and API 31+ coverage the project lacks.
- TalkBack:
  - reading order;
  - the best-row description;
  - severe pills;
  - the "<1%" pronunciation.
- 200% font size on a small screen.
- A real rainy day.
- Any comparison with observed rain. The advice is uncalibrated model output, and the three
  ensembles disagree widely (one-hour walks on a recorded Singapore response: ICON 16%, ECMWF
  48%, GFS 52%).

These checks are `NOT_RUN`. JVM, build, Lint and emulator results do not establish their outcome
or the forecast's accuracy.
