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
