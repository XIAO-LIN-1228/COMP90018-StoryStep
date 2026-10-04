# API 37 instrumentation repair and verification

- Date: 2026-10-05
- Baseline: `9eec65a8cae9dd1b62611a468f060815e1856486`
- Execution branch: `docs/assessment-requirements`; repairs subsequently moved to
  `test/api37-validation` for review. See the running record in `notes.md`.
- Device: `Medium_Phone_API_37.0(AVD) - 17` (emulator).
- Relevant cases: AT-Q01A, API 37 portion of AT-Q01B, automated portion of AT-Q03A.

## Diagnosis and changes

The user supplied a complete run with 75 tests, 7 failures and no skips. Six
accessibility cases failed before checking the UI because Espresso 3.5.1 reflected
on the unavailable `InputManager.getInstance()` method. Updated Espresso core and
accessibility to 3.7.0, AndroidX Test core/runner to 1.7.0 and ext-junit to 1.3.0.
The [official release notes](https://developer.android.com/jetpack/androidx/releases/test)
document the replacement of that reflection with `getSystemService` in Espresso 3.7.0.

The seventh failure was an outdated Profile assertion: sensor capabilities now
live in Settings. The test retains the Profile saved-count assertion, clicks its
existing Settings button, verifies the destination and checks the sensor text there.

The first repaired run passed 73 of 75 tests. Real accessibility checks then
reported 40dp touch targets for `saved_back` and `profile_settings`. Their shared
ScrimButton target is now 48dp with 14dp padding, retaining the previous 20dp icon
area. This also applies to the existing detail, story and Settings users of that style.
No test was skipped or accessibility result suppressed.

## Final verification

Command: `./gradlew.bat test assembleDebug lint connectedAndroidTest --console=plain`

- PASS: exit 0, BUILD SUCCESSFUL in 33 seconds.
- PASS: all 75 instrumented tests on the API 37 emulator; no failures or skips.
- PASS: JVM task up-to-date, reusing the earlier 846-test result (0 failures,
  errors or skips); these tests were not re-executed by the final command.
- PASS: debug APK assembly.
- PASS: Lint, 0 errors and 107 warnings (previously 112 warnings).
- PASS: `git diff --check` (only Git line-ending conversion notices).

Local reports:

- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`

## Limits and next step

This is automated emulator evidence, not physical sensor, field walk, TalkBack,
large-font, visual layout, resource usage or full release acceptance. Other Android
versions were not run. Next: execute E2E-01 manually, then offline and lifecycle
recovery scenarios in `docs/verification_checklist.md`.

AI assistance: Codex diagnosed the supplied failures, updated test dependencies,
the stale UI test and button resources, executed verification and drafted this record.
