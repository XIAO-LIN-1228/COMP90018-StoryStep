# Teammate A requirement review and test evidence

Date: 2026-09-20. Branch: local `feature/context-recommendations`. Starting commit: `a4b6256`.
Changes in this review are local working-tree changes on that baseline. No push,
remote branch, PR, merge or main modification was performed in this review.

Plans: [execution](../teammate-a-execution-plan.md), [tests](../teammate-a-test-plan.md).
Earlier implementation evidence: [initial delivery](EV-REQ21-REQ22-2026-09-20-01.md).

## Decision

The teammate A implementation covers the agreed core behavior. Review found and
reproduced one failure-recovery defect, then repaired it with regressions. It is
not justified to call the application bug-free or fully accepted: instrumented
UI tests and physical-device/manual acceptance have not run on this machine.

The final automated gate passed: 721 JVM tests, both APK builds and Lint with
zero errors. The 63 Lint warnings and unexecuted device checks remain explicit.

## Requirement review

| Scope | Implementation and inspected paths | Evidence and limit |
|---|---|---|
| REQ-21 policy | `domain/recommendation/RecommendationEngine.java`: all six agreed contextual inputs, D-07 weights, neutral missing data, validity/radius checks, score-distance-ID ties. | Controlled JVM scoring, boundaries, preference effects and deterministic ordering. This establishes policy correctness for fixtures, not field recommendation quality. |
| REQ-22 presentation | Nearby Fragment, ViewModel, adapter, row state and XML expose Recommended/Nearby and reasons from actual score components. Filtering precedes ranking/limiting. | JVM mode/filter/limit tests and source review. On-device layout, touch, navigation, rotation and TalkBack remain NOT_RUN. |
| REQ-22 updates | Location rerank threshold 10 m; attraction requery threshold 250 m; weather/time timer 30 s; saved observer and visited reads/arrival events. Selection stays in coordinator. | Controlled rain/expiry/time/save/visit tests; request-count and destination assertions; old-read and post-clear guards. |
| Missing/stale context | Wrong-area/stale/future/old weather is neutral; known preference sets are distinct from missing data. Attraction freshness survives local updates. | Weather fixtures, deferred callbacks, stale-attraction retention and permission recovery tests. Live offline device flow remains NOT_RUN. |
| Profile | Repository-backed completed journey, distinct visited-place and saved-place counts. Permission/provider and accelerometer/magnetometer availability are queried without sensor registration. | JVM counts, duplicate visits, deletion, read failures and late callbacks. Android capability presentation tests are compiled only. |
| Prototype cleanup | Memory class/layout/navigation removed; existing journey-detail photograph editing retained. | XML/navigation/source review and APK compilation; photo-picker walkthrough NOT_RUN. |
| Provenance | Weather credits Open-Meteo; current stories use actual attraction provenance; historical text is marked as an arrival snapshot. | Added JVM coverage for current/archived text and absent cache. Visit records still lack detailed source/article metadata; this review does not reconstruct that metadata. |

## Reproduced defect and repair

**A-BUG-01 — saved preference context never recovers after an initial read failure (P2).**

Trigger: the Nearby ViewModel's initial `loadSavedIds` fails, the local store later
becomes readable, and the user returns to the same destination. Before this fix,
`onVisible()` retried visits only. Saved context could remain unknown for the
lifetime of that ViewModel, so an already saved attraction lost its ranking boost
and reason until another saved-store event or ViewModel recreation.

Repair: retry the local saved-ID read on visibility only while context is unknown;
coalesce pending reads and keep the version guard so a late read cannot undo a
newer save event. No HTTP retry or teammate B behavior was changed.

Failure proof: the focused recommendation suite had **12 tests, 2 failures** before
the repair. Both were newly added regressions:

- `failedInitialSavedReadCanRecoverWhenTheScreenReturns`
- `savedRecoveryCoalescesReadsAndCannotUndoANewerSaveEvent`

Both expected a second read after recovery, but the old code made only one. After
repair these pass, including assertions for the saved reason, ordering, coalescing,
late-event precedence and unchanged attraction request count.

## Additional coverage

Eleven JVM cases were added relative to the 710-test baseline:

- Five Nearby cases: two saved-read recovery regressions; filtering candidates
  beyond the top-30 display limit; evening reranking; retained stale attribution.
- Two weather cases: out-of-order/after-clear reads; recently fetched old or future
  observations marked stale.
- One Profile case: repeat visits count as one place and failed visit reads are unknown.
- Three story cases: actual current provenance, archived text without cache, and
  OSM snapshot attribution.

The new weather test fake initially failed to compile because WeatherRepository
also requires `clearCache`; the test fixture was corrected before the passing run.
This was a test-authoring issue, not an application defect.

## Executed commands and results

Environment: Windows, Java 23 launcher, project-managed Gradle daemon toolchain,
Android SDK Platform 37.0. Existing project toolchain and schema v9 retained.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-23'
$env:ANDROID_HOME = 'C:/Users/LENOVO/AppData/Local/Android/Sdk'
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-configuration-cache --console=plain
# Reproduce the two new failures before the fix:
.\gradlew.bat testDebugUnitTest --tests '*RecommendationIntegrationTest' --no-configuration-cache --console=plain
# Verify the repaired and expanded focused suites:
.\gradlew.bat testDebugUnitTest --tests '*RecommendationIntegrationTest' --tests '*WeatherViewModelTest' --tests '*ProfileViewModelTest' --tests '*StoryViewModelTest' --no-configuration-cache --console=plain
# Final full gate, forced execution rather than reusing test/build task outputs:
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --rerun-tasks --no-configuration-cache --console=plain
git diff --check
& "$env:ANDROID_HOME/platform-tools/adb.exe" devices -l
```

| Check | Actual result |
|---|---|
| Original full JVM suite | PASS before new regressions |
| Pre-fix recommendation regressions | Expected FAIL: 12 tests, 2 failures |
| Post-fix focused suites | PASS: 37 tests across Nearby, Weather, Profile and Story |
| Final full JVM suite | PASS: 721 tests in 58 suites, 0 failures, 0 errors, 0 skipped |
| Debug APK | PASS: assembled |
| Android test APK | PASS: assembled; not executed on a device |
| Lint | PASS with warnings: 0 errors, 63 warnings |
| Final Gradle command | Exit 0; BUILD SUCCESSFUL in 1m 57s; 77 actionable tasks executed |
| Resource XML parse | PASS: 107 XML files |
| Obsolete Memory implementation/navigation | Absent, confirmed by source/XML checks |
| Whitespace | `git diff --check` passed; Windows LF/CRLF notices are not whitespace errors |
| Device inventory | `adb devices -l` listed no devices |
| Instrumented UI / manual / physical acceptance | NOT_RUN |

Local generated evidence is in `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/tests/testDebugUnitTest/index.html` and
`app/build/reports/lint-results-debug.xml` (plus its HTML report). APKs are in
`app/build/outputs/apk/debug/` and `app/build/outputs/apk/androidTest/debug/`.

The key suite totals are 9 RecommendationEngine, 13 RecommendationIntegration,
17 WeatherViewModel, 4 ProfileViewModel and 3 StoryViewModel tests, all passing.

Lint warning inventory: 22 unused resources, 16 overdraw, 5 unused-on-older-API
attributes, 5 newer dependency versions, 2 Gradle dependency notices, 3 plural
candidates, 2 useless parents, 2 XML/PNG icon duplicates, 2 RTL symmetry warnings,
and one each for NotifyDataSetChanged, compound drawables, SmallSp and clickable
view accessibility. The latter two point to the 10.5sp nearby-map label and
PlaceMapView's touch listener without `performClick`; they remain quality-review
items. This review did not expand into redesigning the existing map widget.

## Acceptance mapping and remaining work

- A-T01 through A-T08: PASS in JVM policy/integration coverage; see the specific
  suites and fixtures above rather than treating mocks as device evidence.
- A-T09: PASS for source/XML review and APK compilation.
- A-T10 through A-T14: NOT_RUN. No connected device or emulator was available;
  no claim is made about rotation/navigation, real connectivity transitions,
  photo picker, large fonts, landscape, TalkBack, API 26 or real sensors/battery.
- A-T15: PASS with the 63 Lint warnings disclosed above.

Before release, execute the Android/manual/physical rows in the test plan and
address the actual results. Existing Lint warnings must not be called a clean
accessibility/compatibility verdict. REQ-20.2 retries/backoff, saved-detail cache
fallback, backup exclusions and deletion reliability remain teammate B work.
