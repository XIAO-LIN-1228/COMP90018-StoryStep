# Teammate A test plan

Date: 2026-09-20. Scope and sequence: [execution plan](teammate-a-execution-plan.md).
Baseline: `a4b6256` on local `feature/context-recommendations`. No remote mutations are authorized.

## Test matrix

| ID | Level | Scenario | Expected result |
|---|---|---|---|
| A-T01 | JVM | Equal-distance indoor/outdoor pair, controlled rain then neutral weather | Scores 0.76/0.51 under rain, both 0.635 with neutral weather for D-07 fixture; deterministic ties. |
| A-T02 | JVM | Null inputs, unknown venue/time, radius and invalid distance boundaries | Context defaults to 0.5; no NaN; invalid/out-of-range candidates excluded; empty input safe. |
| A-T03 | JVM | Stale, expired, future, wrong-area weather; local hour boundaries | No false wet-weather reason or expired-weather boost; time preference follows 08:00/18:00 boundaries. |
| A-T04 | JVM | Saved/visited transitions, failed reads and callbacks completed out of order | Current known context drives scoring; missing context neutral; old reads cannot revert newer state. |
| A-T05 | JVM | Mode switching, search/venue/unvisited filters, more than 30 candidates | Nearby sorts by distance/ID; all candidates filtered/ranked before limit; clearing filter recovers empty results. |
| A-T06 | JVM | Location movement, context timer, weather changes and selected target | Meaningful reranking only; destination stable; no attraction request per context update or location sample. |
| A-T07 | JVM | Cache/stale results and weather expiry during permission recovery | Useful retained content and truthful provenance; expired weather does not influence reasons. |
| A-T08 | JVM | Profile history/saved updates, errors, deletion and late callbacks | Real counts; failures show unknown; stale callbacks cannot replace newer results. |
| A-T09 | Static/build | Memory routes/resources, story/weather labels, capability reader | No obsolete fake editor route; attribution and unavailable states present; no new sensor subscription. |
| A-T10 | Android | Switch mode, set search/filters, navigate away/back, recreate Activity | Visible query/chips/mode agree with ViewModel; selected target and filters remain stable. |
| A-T11 | Android/manual | Profile values after save/delete/relaunch and permission/provider changes | Actual values/capabilities displayed; location and sensor recovery remain usable. |
| A-T12 | Android/manual | Online -> offline, expiry, controlled rain, story and finished-journey photo flow | Cache labelled; reasons match context; no false save; legitimate photo flow works. |
| A-T13 | Device/manual | Landscape, 200% fonts, TalkBack, API 26 and another supported version | Controls reachable and labelled; no clipping/crash or loss of selected state. |
| A-T14 | Physical | GPS, compass, motion, arrival and battery walkthrough | Record observed accuracy, hardware/OS and battery measurements; not substituted by emulator results. |
| A-T15 | Automated gate | Full JVM suite, debug/test APK builds, Lint and whitespace validation | Zero failing JVM tests; both APKs build; zero Lint errors, warnings inventoried; clean whitespace. |

Existing suites: `RecommendationEngineTest`, `RecommendationIntegrationTest`,
`NearbyAttractionsViewModelTest`, `WeatherViewModelTest`, `ProfileViewModelTest`,
`RecommendationPresentationTest`, `StoryViewModelTest`, plus the full project's regression suite.
Add tests for uncovered behavior found in review, not assertions that merely mirror
implementation details. Use controlled clocks/data and deferred callbacks.

## Commands

Environment-specific launcher/SDK paths (not committed Gradle configuration):

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-23'
$env:ANDROID_HOME = 'C:/Users/LENOVO/AppData/Local/Android/Sdk'
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-configuration-cache --console=plain
.\gradlew.bat assembleDebug assembleDebugAndroidTest lint --no-configuration-cache --console=plain
git diff --check
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
```

On an available disposable emulator/device, execute all instrumentation tests:

```powershell
.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache --console=plain
```

Record the XML totals, Gradle exit codes, Lint severity counts and device model/API.
If no device is available, mark A-T10 through A-T14 NOT_RUN (test APK compilation
only is not a pass). Do not provision or wipe a user's device to fabricate evidence.

## Acceptance decision

All automated gates must pass after repairs. Any unresolved functional defect is
reported separately from environmental/device gaps. REQ-21 can have verified
deterministic policy behavior while REQ-22 and cleanup remain only partially
verified until Android/manual cases run. No claim of bug-free code or complete
release acceptance follows from a passing test suite.
