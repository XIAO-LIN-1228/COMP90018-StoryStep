# Test and validation notes

This is the running record for this testing session. Append decisions, commands,
results, fixes and remaining work; preserve earlier outcomes rather than replacing
failed runs with the latest success. Documentation and code comments remain English.

## 2026-10-04: Debug build recovery

- Read the project engineering guidance before investigating the supplied Gradle
  failure summary. Reproduced with `./gradlew.bat assembleDebug --stacktrace --console=plain`.
- Gradle failed in `:app:dexBuilderDebug` while deleting generated DEX directories.
  Java compilation was already up-to-date. The shell initially needed elevated
  access to the user Gradle cache; that sandbox failure was separate from the build issue.
- Stopped the Gradle daemon, then attempted `clean assembleDebug`; `:app:clean`
  still failed. Inspection showed ReadOnly attributes on generated directories
  under `app/build`, inside the OneDrive workspace. No evidence established which
  process set those attributes.
- Verified the target path and cleared ReadOnly attributes only inside `app/build`.
  No source or Gradle DSL changes were needed.
- Re-ran `clean assembleDebug --console=plain`: PASS, 23 seconds, 35 tasks executed.
  Debug APK produced at `app/build/outputs/apk/debug/app-debug.apk`; working tree clean.

## 2026-10-05: Validation sequence and scope

- Reviewed `docs/development_plan.md`, `docs/verification_checklist.md` and recent
  evidence. Prioritize current automated checks, then emulator core-flow acceptance,
  physical sensing, accessibility/performance evidence and submission materials.
  Historical plan entries do not establish current acceptance.
- A Styla request was sent to this workspace by mistake. Verified the mismatch and
  made no changes for that task; the user confirmed continuing StoryStep.
- Ran `./gradlew.bat test assembleDebug lint --console=plain`: PASS in 58 seconds.
  JVM XML totals: 846 tests, 0 failures, 0 errors, 0 skipped. Debug assembly passed;
  Lint reported 0 errors and 112 warnings. Working tree remained clean.
- Provided emulator connection, instrumented-test, E2E exploration, offline,
  permission and lifecycle-recovery steps. Injected emulator coordinates must be
  labelled simulated, not physical GPS/walking evidence. Automated results do not
  establish manual usability, TalkBack or field sensor acceptance.

## 2026-10-05: API 37 instrumented failures and repairs

Baseline: `9eec65a8cae9dd1b62611a468f060815e1856486` on
`docs/assessment-requirements`. Device: `Medium_Phone_API_37.0(AVD) - 17`.

1. User ran `connectedAndroidTest`: 75 tests completed, 7 failures, 0 skips.
   Six accessibility tests failed during Espresso initialization with
   `NoSuchMethodException: InputManager.getInstance`; the Profile presentation test
   failed by dereferencing a missing TextView.
2. Inspected dependency versions and actual Profile/Settings wiring. Espresso 3.5.1
   used the missing reflective API. The official AndroidX Test release notes confirm
   the replacement with `getSystemService` in Espresso 3.7.0:
   https://developer.android.com/jetpack/androidx/releases/test
3. Updated the version catalog: Espresso core/accessibility 3.7.0, Test core/runner
   1.7.0, ext-junit 1.3.0. Kept application technology, SDK and AGP DSL unchanged.
4. Corrected the stale presentation test: assert Profile's saved-count view, click
   the existing Settings control, assert Settings destination and sensor status.
   The application had already moved that information to Settings.
5. Re-ran `connectedAndroidTest`: 73/75 passed. The original failures were resolved;
   working accessibility checks exposed undersized `saved_back` and `profile_settings`
   touch targets (40dp). No checks were suppressed or skipped.
6. Changed shared ScrimButton size to 48dp and padding to 14dp, retaining a 20dp icon
   area. This also affects existing detail, story and Settings buttons using the style.
7. Ran `./gradlew.bat test assembleDebug lint connectedAndroidTest --console=plain`:
   PASS in 33 seconds, 75/75 instrumented tests, 0 skips. Debug assembly passed;
   Lint 0 errors / 107 warnings. JVM task was UP-TO-DATE, reusing the earlier
   846-test result rather than executing those tests again. `git diff --check` passed
   with only line-ending conversion notices.

Detailed evidence: [API 37 verification](docs/verification/EV-API37-INSTRUMENTATION-2026-10-05.md).
Generated reports (local, not committed):

- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

## Branch and Draft PR decision

- User requested this running note, a dedicated validation branch and a Draft PR.
  This authorizes committing and pushing the scoped changes for review.
- Fetched origin and inspected GitHub: no open PR for the assessment branch;
  `origin/main` is `841517d`, and the assessment branch is one commit ahead.
- Created `test/api37-validation` from the existing tested baseline, preserving edits.
- Use `docs/assessment-requirements` as the Draft PR base so the unrelated assessment
  commit is not included in the test diff. This is a stacked PR dependency; integrate
  the assessment changes and check/retarget the test PR to main before final merge.
- Do not merge automatically. Store the resulting Draft PR URL in this session's
  handoff and continue appending future validation results here.
- Published repair/evidence commit `9cfa2dd` and created
  [Draft PR #30](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/30).
  Upstream is `origin/test/api37-validation`; no merge was performed.

## Remaining work and next safe step

### Environment inventory outside Gradle (collected after tests, 2026-10-05)

Per user direction, Gradle-declared dependencies and build settings are not duplicated
here. This snapshot describes the current workstation/AVD, not independently proven
settings at the instant of the earlier successful run. These values describe the
reference setup; they are not universal minimum requirements for teammates.

| Component | Observed value |
|---|---|
| Host OS | Windows build 22631.5624, release 23H2; registry ProductName reports `Windows 10 Pro` (retained literally, not used to infer marketing edition) |
| Shell | Windows PowerShell 5.1.22621.5624 |
| Android Studio | AI-262.9437.185.2621.16467767; data directory AndroidStudio2026.2.1 |
| IDE bundled runtime | JetBrains JBR 25.0.3, x86_64; separate from Gradle toolchain |
| Android Platform Tools / ADB | 37.0.1-15733141 / ADB 1.0.41 |
| Android Emulator | 37.2.12.0, build 16428233 |
| AVD / primary ABI | Medium_Phone_API_37.0 / x86_64 |
| System image package | system-images;android-37.0;google_apis_playstore;x86_64, revision 6, extension 22 |
| Image identity | Android 17, CE2A.260420.019 / 15611780, security patch 2026-05-05 |
| Image tags | google_apis_playstore, ai_glasses_compatible; Play Store enabled |
| AVD resources | 10 virtual CPU cores; 2048 MB RAM; 10G data partition |
| Graphics / boot configuration | GPU enabled, mode auto; fast boot enabled, no forced cold boot |
| Display | 1080 x 2400; 420 dpi; font scale 1.0; night mode no |
| Rotation | Auto-rotation setting 1; user_rotation 0; active orientation during tests not captured |
| Language | Image default en-US; persist.sys.locale unset, active per-app override not verified |
| Accessibility | enabled_accessibility_services unset at collection; no manual TalkBack acceptance |
| Animation settings | Window and transition scale 1.0; animator duration setting unset (not assumed disabled) |
| Configured simulated sensors | Accelerometer, GPS, magnetic field, orientation, pressure, light and proximity enabled |
| Camera / audio | Rear virtualscene; front emulated; audio input enabled |
| Storage context | Repository under a OneDrive directory; generated-folder ReadOnly incident documented below/above |

Sources: Android Studio product-info.json/JBR release file, SDK image source.properties,
AVD config.ini, Windows registry, ADB getprop/wm/settings and the earlier version query.
No private account data, full desktop logs or personal emulator contents are committed.
Host GPU driver/virtualization backend, active network/proxy conditions, voice data,
app permission/data state and snapshot contents at test execution were not captured.
Record them for subsequent tests where relevant; do not fabricate a retrospective
environment match. Real-provider calls, audio and physical sensors remain separate gates.

### Disabled IDE Run button follow-up

- User reports Studio/emulator remain operational after the host error, but Run is
  disabled; user confirmed `app` is selected.
- Saved configuration exists and targets `StoryStep.app`; recent logs show indexing
  completed. No post-dependency-change sync was observed in the inspected log.
- Suggested File > Sync Project with Gradle Files, then recheck the device selector
  and Run. Unsynced build changes are a hypothesis, not a confirmed diagnosis:
  https://developer.android.com/build documents disabled run configurations in that state.
- No IDE configuration was edited, caches invalidated or emulator data reset.
  Awaiting user confirmation of sync outcome; host crash cause remains unresolved.

### Host incident during subsequent environment collection

- User requested recording environment dependencies outside Gradle configuration.
  During that later collection, the user reported a Windows `crashpad_handler.exe`
  application error, exception `0x80000003`, address `0x00007FF7A7CA7965`.
- An `emulator.exe -version` query had just been executed. Temporal proximity is
  recorded, but causation is not established. Paused further emulator executable
  queries; no process was killed, no emulator data wiped and no source fix attempted.
- Read-only process inspection found an Android SDK crashpad handler as well as
  handlers belonging to other desktop applications. The dialog alone does not
  identify which executable failed. Android Studio and the emulator QEMU process
  were still present; process presence does not prove UI responsiveness.
- An attempted recent Windows Application-event query yielded no usable record.
  Root cause remains unresolved. This occurred after the successful 75-test run
  and is not recorded as a StoryStep Android test failure.
- Partial post-test snapshot: ADB 37.0.1-15733141; emulator 37.2.12.0, build 16428233;
  ADB reported Windows 10.0.22631; PowerShell 5.1.22621.5624; connected device
  `emulator-5554`, model `sdk_gphone64_x86_64`; ABI list `x86_64,arm64-v8a`;
  image fingerprint `google/sdk_gphone64_x86_64/emu64xa:17/CE2A.260420.019/15611780:user/release-keys`;
  screen 1080x2400, density 420 dpi, font scale 1.0. These are later observations,
  not a contemporaneous snapshot of the completed test run. Full environment
  documentation and Draft PR update remain pending.

- Manual emulator E2E-01: nearby discovery, save, select target, start, simulated
  arrival, story, finish and persisted history. Then E2E-02 offline and E2E-04 recovery.
- Inspect layouts affected by the shared button size; automated accessibility
  checks do not constitute visual acceptance of every screen.
- Physical GPS/motion/compass, haptics, voice behavior, TalkBack, large fonts,
  additional Android versions and resource measurements remain unverified this round.
- Preserve the report's separate runtime responsiveness and sensor/data UI
  reactiveness criteria; do not infer rubric scores from a successful build.
- No release, submission, merge or complete rubric acceptance has been claimed.

AI assistance: Codex investigated failures, edited test dependencies, the presentation
test and resources, ran verification, and prepared these records and the Draft PR.
