# Theme exploration implementation and verification

Date: 2026-09-21. Local branch: `feature/theme-exploration`.
Base: `b2ef81ddb39599aef68e10297f4e26ab6ec0972f`. Changes remain uncommitted; no push or merge.

## Implemented behavior

- Nearby → **Theme explorations** opens the Parkville buildings/botany theme with three sourced
  stops. Browsing does not start collection. Explicit start/resume checks location access/services.
- A single exploration session owns all stations. Users choose pending destinations, follow the
  existing approximate guidance and unlock stories/questions through the existing arrival policy.
- Arrival counts derive from durable visits; duplicate callbacks do not increase progress. Past
  journeys and unrelated places do not unlock stations. Optional quiz answers do not create visits.
- Skip/undo, answer changes, pause/resume and interrupted-session recovery preserve progress.
  A failed skip preserves the target; a failed arrival write exposes an error without unlocking a story.
- Finish saves complete or partial results. Skips stay separate; finished answers/progress are
  read-only. Existing directions, story and journey-detail screens link to the theme.
- Room schema 10 adds theme definition snapshots and station rows. Session creation and all initial
  theme rows are transactional. Cache deletion cannot remove this user-owned content; discarding
  a run or deleting its history cascades exactly its owned theme records.
- New-run destination selection no longer writes into the previous completed session. Story source
  attribution remains correct regardless of asynchronous story/theme loading order. Repeated
  station view IDs cannot restore another station's answer during Android view restoration.

## Executed results

Environment: Windows, Gradle 9.5.0 / AGP 9.3.1, JDK 25 toolchain, API 36 Google APIs x86_64
emulator `StoryStepThemeApi36` with WHPX acceleration. This is an emulator, not a physical phone.

| Check | Result |
|---|---|
| All debug JVM tests | **780 passed, 0 failed, 0 errors, 0 skipped** |
| New theme JVM coverage | 34 tests across model, repository, coordinator and ViewModel |
| Debug APK | **PASS** |
| Android test APK | **PASS** |
| Complete `connectedDebugAndroidTest` suite | **73 passed, 0 failed, 0 errors, 0 skipped** |
| New device coverage | 5 SQLite tests, 1 migration test, 2 presentation tests, 1 accessibility screen test |
| Lint Debug | **0 errors, 63 warnings** |
| Theme UI with no active network, landscape, font scale 1.5 | **2 passed**; screenshots reviewed |
| Theme UI normal-font landscape capture/recreation verification | **2 passed**; screenshots reviewed |
| Git diff whitespace check | **PASS** |

The 73 device tests include all existing migration/storage/presentation checks and automated
accessibility checks. Their pass does not establish manual TalkBack reading order or outdoor GPS
accuracy. Extra UI runs repeat two existing cases under changed conditions; they are not additional
unique tests.

Commands used (with installed `JAVA_HOME` and `ANDROID_HOME`):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --no-configuration-cache --console=plain
.\gradlew.bat connectedDebugAndroidTest lintDebug --no-configuration-cache --console=plain
adb -s emulator-5554 shell am instrument -w -r -e class com.comp90018.storystep.ui.ThemePresentationTest com.comp90018.storystep.test/androidx.test.runner.AndroidJUnitRunner
```

The offline variation disabled emulator Wi-Fi and mobile data; `dumpsys connectivity` reported
`Active default network: none`. Font scale was 1.5 and captured screenshots were 2400×1080. The
normal-font run used font scale 1.0. Captures remained landscape despite requesting portrait via
settings and WindowManager; only the actual captured orientation is claimed here. The complete
73-test suite ran before changing the emulator from its initial portrait configuration.
Emulator network/font settings were restored afterwards. These UI tests use explicit persisted visit fixtures, not actual
walked GPS tracks. The JVM coordinator tests separately replay good/poor location fixes and
failed arrival writes.

## Failures investigated before final passing runs

- First compile exposed two required coordinator-observer callbacks missing from ThemeViewModel;
  implemented the callbacks. The ViewModel's I/O tests were moved into `@Before` so the Android
  architecture executor test rule is active before LiveData publishes.
- Offline Gradle resolution initially lacked Espresso accessibility and UTP host artifacts.
  Dependencies were resolved online; the subsequent complete build/device execution passed.
- The first SQLite recovery assertion incorrectly expected the repository to convert ACTIVE to
  PAUSED. Storage correctly preserves the last checkpoint; the state machine owns that conversion.
  The test now checks the actual recovery boundary, alongside the no-subscription coordinator test.
- The first device run had 7 failures: the assertion above plus six root-focus timeouts caused by
  a **System UI isn't responding** emulator dialog. The dialog was dismissed, the emulator was
  kept awake and the complete 73-test suite rerun. No tests were disabled or excluded.
- Android retained landscape during the additional UI screenshot runs despite rotation reset
  requests. The screenshots were inspected and recorded under their actual landscape orientation.

## Reproducible artifacts

- [Machine-readable counts and APK SHA-256](theme-exploration-2026-09-21/summary.json)
- [Full 73-test XML result](<theme-exploration-2026-09-21/TEST-StoryStepThemeApi36(AVD) - 16-_app-.xml>)
- [Offline / landscape / large-font runner output](theme-exploration-2026-09-21/offline-large-font-landscape.txt)
- [Normal-font landscape runner output](theme-exploration-2026-09-21/normal-font-landscape-ui.txt)
- [Normal-font landscape progress screenshot](theme-exploration-2026-09-21/normal-font-landscape/theme-restored-progress.png)
- [Offline large-font landscape screenshot](theme-exploration-2026-09-21/offline-landscape/theme-restored-progress.png)

Generated local reports: `app/build/reports/tests/testDebugUnitTest/index.html`,
`app/build/reports/androidTests/connected/debug/index.html`,
`app/build/reports/lint-results-debug.html`. Installable artifact:
`app/build/outputs/apk/debug/app-debug.apk`.

## Remaining acceptance limits

No physical-device field walk, battery measurement, TalkBack walkthrough, dark-mode walkthrough,
or additional Android-version matrix was executed. The bundled site's approximate coordinates
and current pedestrian access still require on-site verification. No claim is made that every
building is open or that map tiles are fully available offline. Real process-kill behavior is covered
by database reopen/state-machine recovery tests, not a manually walked process-kill scenario.

See the [implementation/test plan](../theme-exploration-plan.md) for scope, invariants and content
sources. All planned software components are implemented; the field checks above remain explicit
release/demo acceptance work. Voice playback is outside this theme-only implementation.
