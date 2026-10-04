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

## Guided manual emulator acceptance

Results below are user-observed and reported through the conversation, not direct
agent UI observations. Preserve existing emulator app data; this is not a verified
clean install. Record simulated location separately from physical sensing.

### IDE recovery and M01: Install and launch

- After the suggested Gradle sync step, the user reported Run was restored. The
  exact recovery mechanism was not independently verified; the crashpad root cause
  remains unresolved.
- Instructions: select the app run configuration and Medium_Phone_API_37.0 device,
  click Run, wait for installation/launch, then stop before interacting with the app.
- User result: Run succeeded; StoryStep shows the Get Started page; no permission
  dialog, crash or other reported prompt.
- Outcome: PASS for installation/launch and initial screen visibility only.
  No fresh-install, permission, network or exploration acceptance is inferred.
- Next: tap Get Started once and report the destination/prompt before responding
  to any permission request. Permission grant/denial checks are still NOT_RUN.

### M02: Location rationale and skip affordance

- Action: user tapped Get Started once.
- User result: the Explore around you screen displays the location explanation
  "We only use it during an active exploration." and Not now / Use my location
  controls. Text/buttons are fully visible; no noticeable lag reported.
- Outcome: PASS for the transition and readable explanation. This does not verify
  that actual collection follows the stated foreground policy.
- Usability finding MAN-01 (OPEN): user reports Not now does not clearly look
  clickable. Treat as a discoverability/visual-affordance issue; functional click
  behavior is not yet tested. No UI change made during this observation.
- Next M03: tap Not now once, verify limited browsing is reachable without a forced
  location request, and record destination, prompts and initial data/error state.
  This is onboarding deferral, not an OS permission-denial test.

### M03: Defer location and browse the home screen

- Action: user tapped Not now. It was functional and opened the home screen.
- User result: map, attraction markers, list and distances appeared; no system
  permission prompt, crash or loading error was reported. Weather text was reported
  as `11°C·CloudyReference area`. No live user-location acceptance is inferred.
- Outcome: PASS for deferral navigation and eventual browse availability. Existing
  OS permission state was not independently inspected; no permission prompt does
  not establish that permission was previously denied or that collection is absent.
- User estimated attraction loading at approximately 20 seconds. MAN-02 (OPEN):
  investigate perceived loading delay, interim feedback and retry/cache behavior.
  This is an approximate user observation, not a timed benchmark; network, provider
  timing and cache state are unknown. No cause is assigned yet.
- Some places lacked pictures and some lacked descriptions. MAN-03 (OPEN for
  inspection): check missing-content presentation in a concrete detail screen.
  Provider incompleteness alone is not classified as an application defect.
- Reference-area label is present in the reported weather text. Its visual spacing
  and whether list distances clearly communicate their reference origin have not
  been inspected; do not infer a layout defect from the transcribed text alone.
- Next M04: open one place with missing description/image, record its identity,
  detail fallback, source attribution, distance/location wording and action controls.

### M04: Sun Ribbon detail screenshot

- Evidence: user supplied local `docs/test screenshot/1.png`; agent inspected the
  image. Added `/docs/test screenshot/` to .gitignore at the user's request; images
  remain local and were not previously tracked. Reviewers get these observations,
  not a committed screenshot artifact.
- Visible result: Sun Ribbon, Public art, Outdoor, Spark not lit. A photograph is
  loaded, with OpenStreetMap / Wikimedia Commons photo attribution. About this place
  explicitly says no description is available from OpenStreetMap. Place-data and
  potential Wikipedia/Wikidata attribution are visible below.
- Controls visible: Back, an outline bookmark icon and Add to walk. No distance or
  reference-origin wording is visible in this screenshot. No obvious text clipping
  is visible. A static screenshot does not establish control behavior or timing.
- Outcome: PASS for readable missing-description fallback on this place. MAN-03
  remains PARTIAL: the missing-image case has not been inspected (this image loaded).
  Detail opening duration and list reference-distance clarity remain unverified.
- Next M05: tap the bookmark once, observe state/feedback, return to Saved and
  verify the same place appears and reopens. Do not tap Add to walk yet.

### M05: Save and reopen Sun Ribbon

- User result: tapping the bookmark turned it yellow; no text prompt was shown.
  Saved displayed Sun Ribbon once, without duplicates. Opening it from Saved worked
  and the bookmark remained in its saved state. User reports initial detail opening
  was immediate (qualitative observation, not instrumented timing).
- Outcome: PASS for save interaction, list consistency and reopening detail within
  the current app run. Color change is observed feedback; no missing-toast defect
  is inferred. Non-color/TalkBack state feedback has not been assessed.
- Cross-launch persistence and offline availability are not yet verified.
- Next M06: leave the app, swipe its task away in Recents, reopen via its launcher
  icon and inspect Saved. Keep network and permissions unchanged; do not clear data,
  uninstall or press Android Studio Run. A task dismissal is not proof of OS process
  death, so report this as relaunch persistence rather than process-kill recovery.

### M06: Relaunch and retained save

- User result after the instructed Recents dismissal and launcher reopen: app opened
  directly to the home screen; Saved still contained exactly one Sun Ribbon; detail
  opened and the bookmark remained yellow. No error or noticeable wait reported.
- Outcome: PASS for onboarding completion retention and saved-state consistency
  across this relaunch. OS process death was not independently established.
- Next M07: disable both emulator Wi-Fi and mobile data, verify both toggles are off,
  dismiss/reopen the app and inspect the same saved detail offline. Keep saved data
  and permissions intact; do not unsave or start a walk. Offline result pending.

### M07: Saved detail offline after relaunch

- User confirmed both emulator Wi-Fi and mobile data were off, then followed the
  relaunch and Saved/detail sequence. Sun Ribbon remained listed and opened;
  photograph, missing-description explanation and yellow bookmark were intact.
  No offline prompt, error, persistent loading or noticeable delay was reported.
- Outcome: PASS for offline availability of this previously saved place in the
  reported setup. Connectivity toggles were user-confirmed, not independently
  probed. This does not prove uncached content, offline map tiles, expired-weather
  handling or offline indication on the home/nearby screen.
- Next M08: remain offline, return to the home screen and inspect weather, origin,
  cached/offline labels and attraction-list availability after about 15 seconds.
  Do not infer a defect solely because local saved detail shows no offline banner.

### M08: Home weather and attraction list while offline

- User reports weather text `11°Ccloudy:41 min ago·Reference area`; no Offline,
  Cached or Stale label was visible. Attractions remained browsable, without a
  persistent spinner or error. No refresh/retry control was visible.
- Outcome: PASS for continued list browsing; weather/offline-state communication
  remains UNVERIFIED. Observation age alone does not establish cache fetch age,
  whether a refresh was attempted, or the repository's freshness classification.
- MAN-04 (OPEN): inspect home weather freshness/offline presentation and refresh
  affordance against the implemented cache contract. Do not mark offline/stale
  labeling accepted from this observation or assign a root cause prematurely.
- Next: obtain a home screenshot to inspect exact labels and visible controls before
  restoring connectivity. Preserve current offline state until that observation.

### M08 screenshot supplement

- Agent inspected local ignored `docs/test screenshot/2.png`. Header reads
  `11°C · Cloudy · 44 min ago · Reference area`; separators and spacing are visible.
  No Offline/Cached/Stale label or explicit refresh/retry control is visible in the
  captured viewport. MAN-04 remains open pending behavior/implementation review.
- Map and attraction markers are displayed. Sun Ribbon appears with a yellow saved
  icon, 97 m distance, and reasons including outdoor daytime preference, saved by you
  and not yet visited. Recommended is selected; Nearby is available.
- `Reference area` is visible in the header, but the captured list does not explicitly
  state that its distances are from that reference origin. Flag reference-distance
  clarity for follow-up rather than claiming the 97 m is measured from the user.
- Screenshot alone does not prove network state, freshness classification, map
  completeness or control behavior. Prior offline toggle state was user-reported.
- Next M09: restore Wi-Fi/mobile data, observe the home screen for about 30 seconds,
  record whether weather/list update automatically; if unchanged, navigate to Saved
  and back once to distinguish reconnect response from screen-entry refresh.

### M09: Restore connectivity and revisit home

- User confirmed connectivity was restored. During the observation interval the
  weather remained unchanged except for the increasing minutes-ago value. Visiting
  Saved and returning to home did not visibly change it. No loading, error, lost
  list or noticeable lag was reported.
- Outcome: PASS for UI continuity following reconnection; weather recovery remains
  UNVERIFIED. An unchanged observation can result from unchanged provider data or
  refresh policy as well as a missing refresh; no successful request is inferred.
- MAN-04 stays OPEN. Distinguish observable lack of new weather from a proven
  network/repository defect. No application code changed during this manual round.
- Next M10: with connectivity restored, dismiss the task and reopen via launcher;
  record complete weather text after about 30 seconds and any error or loading.
  This checks launch behavior, not verified OS process-death recovery.

### M10: Online relaunch refreshes displayed weather

- User reports no lag and that the weather age updated to real-time after the
  instructed online relaunch. Exact resulting text/time and request logs were not
  supplied; describe this as user-observed display recovery, not traced HTTP proof.
- Outcome: PASS for visible weather recovery on this online relaunch. M09's lack of
  visible refresh on reconnection/screen navigation remains a distinct observation;
  MAN-04 stays OPEN for freshness/offline labeling and refresh-trigger review.
- Next M11: tap the bottom Start control once while online, observe the next screen
  or permission prompt without accepting/denying it yet. Do not infer existing
  permission state from the earlier onboarding skip.

### M11: Start opens the empty walk planner

- User tapped bottom Start and supplied local ignored `docs/test screenshot/4.png`.
  Agent inspected it: title Plan a walk, subtitle Choose the places you want to see,
  map, Your stops panel and selected Start navigation tab. The stops panel is blank
  in the captured viewport, without a visible add-place instruction/action.
- User reports no system permission dialog and no active exploration/timer.
- Outcome: planner entry observed; permission and active-session tests remain
  NOT_RUN. Bottom Start is a planning destination, not evidence of session activation.
- MAN-05 (OPEN): empty planner discoverability. The visible panel does not explain
  how to add the first stop; do not infer missing functionality from this screenshot.
- Next M12: return to Explore, reopen saved Sun Ribbon and tap Add to walk once;
  report resulting destination, stop list, actions and any permission/confirmation
  prompt before responding to it. No source changes made for this finding.

### Permission-state inspection before M12

- User paused the planner sequence to confirm whether the emulator can show runtime
  permission dialogs. Initially viewed emulator graphics/crash-report preferences;
  clarified these are not Android app permissions and requested no changes there.
- User then inspected Android App info > StoryStep > Permissions > Location and
  reported Don't allow. No permission setting change was requested or reported.
- This confirms the user-observed current denied setting, not prior denial count
  or whether another request will show a dialog. Precise-location toggle state was
  not reported. Runtime prompt behavior remains NOT_RUN.
- Continue M12 by adding Sun Ribbon from detail to the planner; inspect actual
  start action before triggering a permission request. Do not clear data or grant
  permission through Settings merely to bypass the test.

### M12: First stop added; start action not discoverable

- User confirms Sun Ribbon was added but cannot find the start-walking action.
  Agent inspected local `docs/test screenshot/5.png`: one Sun Ribbon row, Clear,
  reorder/remove controls and map route are visible; Add stop and Start walking
  are absent from the visible collapsed sheet. Header reads one stop/about 150 m/
  around 7 min; row says 97 m from here. Location permission is still user-reported
  Don't allow, so the "from here" wording also needs reference-origin review.
- Code inspection: fragment_plan.xml defines a full-height BottomSheet with a
  weighted list and footer containing plan_add/plan_start. A collapsed full-height
  sheet can leave its footer below the viewport. This is the working explanation,
  pending manual expansion; no layout repair has been applied yet.
- MAN-05 expanded: core start-action discoverability issue, not just empty-state
  guidance. Permission prompt remains NOT_RUN because start action was not reached.
- Next M12a: drag the sheet's top handle upward to expand it; report whether Add
  stop/Start walking become visible, before tapping start. This is a diagnostic
  workaround, not acceptance of the collapsed layout.

### M12a: Expand the planner sheet

- User reports the sheet expands upward and has three snap positions: full-height,
  default and an intermediate half-screen position. After expansion Add stop and
  Start walking are both fully visible, without bottom-navigation occlusion.
- Outcome: PASS for reaching the existing actions by expanding the sheet. The
  observed behavior supports the collapsed-footer explanation; MAN-05 remains OPEN
  because the default view hid the primary action from the tester. Visibility at
  each individual snap position has not yet been separately recorded.
- Next M13: tap Start walking once from the expanded sheet, then report any system
  location prompt or in-app recovery screen before choosing an option. Keep the
  user-observed Don't allow setting unchanged; permission-request result pending.

### M13: Start while location denied reaches an idle dashboard

- User reports no runtime permission dialog after Start walking; supplied local
  `docs/test screenshot/6.png`, inspected by agent. User requested no repetitive
  questions about the same permission dialog; continue based on observed behavior.
- Visible: Sun Ribbon, timer 0:00, Not exploring, Stop 1 of 1, Distance unavailable
  / waiting for a fix, 0 m travelled, Movement Sensing..., GPS No fix, Climb 0 m,
  reference-area weather and Stop exploration. No permission explanation or
  recovery action is visible in the supplied viewport.
- MAN-06 (OPEN, core-flow blocker for denied permission): Start reaches an idle
  dashboard without actionable permission recovery. Conflicting Not exploring /
  Sensing... / Stop exploration presentation is also observed. Actual sensor
  registration and timer progression were not instrumented; do not claim tracking.
- Permission status Don't allow alone does not establish Android's future dialog
  behavior or denial history. The concrete application-path outcome is the evidence.
- Preserve denied-path failure; do not count a later Settings grant as a repair.
  Next: use Android Back to return to the planner and observe whether the stop is
  retained and whether an active/paused-session recovery banner appears. Then a
  separately labelled manual Settings grant can unblock the positive-path test.

### M13a: Back from denied-start dashboard

- User supplied local `docs/test screenshot/7.png` after the instructed Back action.
  Agent inspected the dialog: Finish this exploration?; text promises progress and
  discovered stories will be saved on this device; Cancel, Discard, Finish journey.
  Background timer is still 0:00. The planner has not been reached.
- Extend MAN-06: a non-exploring dashboard opens a finish/save dialog, making the
  denied-start state misleading. No database/session existence is inferred from UI.
- Next: choose Discard for this test attempt, then inspect destination, planner
  state and Journeys for an unexpected record. This is a scoped test of abandoning
  the current unsuccessful attempt, not deletion of existing history or saves.

### M13b: Discard cannot exit the idle dashboard (FAIL)

- User selected Discard: dialog closed without error, but the dashboard remained.
  Repeating Back/Discard returned to the same screen and could not reach home.
  Agent inspected local `docs/test screenshot/8.png`: Not exploring, 0:00, GPS No fix
  and Stop exploration are still visible. Journeys inspection was blocked.
- Outcome: FAIL; MAN-06 is a reproduced core-flow navigation blocker. Pause the
  manual sequence here; do not keep asking the tester to repeat the same operation
  or change location permission before preserving this failure.
- Initial code evidence: ExplorationFragment only navigates after a session has
  been seen (`sessionSeen`), while PlanFragment requests start and navigates to the
  dashboard regardless of the resulting session state. Trace denied start/discard
  before fixing; a no-session exit must not depend on a real session ending.

### End-of-session handoff

- User ended today's manual testing after M13b. Suggested Force stop (without
  clearing app data), launcher reopen and Saved/Journeys inspection were NOT_RUN;
  no recovery or absence of a spurious history record is claimed.
- Manual acceptance is blocked by MAN-06. Automated 75-test success predates these
  manual findings and does not establish that the permission/planner flow works.
- User authorized publishing the records and creating five GitHub issues: MAN-06
  and MAN-05 as bugs, MAN-04 and MAN-02 as investigations, MAN-01 as usability.
  MAN-03 stays on the test checklist: missing-description fallback passed; a
  missing-image detail case has not been tested.
- Publish only notes and the screenshot ignore rule for this round. Screenshots
  remain local; issue bodies must describe inspected evidence without pretending
  that local image filenames are downloadable GitHub attachments.
- Keep PR #30 in draft and use non-closing issue references. No application fix,
  merge or issue closure is authorized by this documentation handoff alone.

### Published issue mapping and revised triage

- User subsequently clarified that MAN-02 (approximately 20-second attraction
  loading) is low priority and should remain documentation-only, without a GitHub
  issue. This supersedes the five-issue proposal above. No MAN-02 issue was created.
  Keep the observed delay and measurement limitations for future reference.
- Other findings retain the originally agreed issue treatment:

| Finding | GitHub record | Status at handoff |
|---|---|---|
| MAN-06 denied-start/Discard loop | [#31](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/31) | Open bug; core-flow blocker |
| MAN-05 planner action visibility | [#32](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/32) | Open bug |
| MAN-04 offline weather/reconnect | [#33](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/33) | Open investigation; cause unconfirmed |
| MAN-01 Not now affordance | [#34](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/34) | Open usability enhancement |
| MAN-02 initial loading estimate | This document only | Low priority; no issue per user |
| MAN-03 missing-content fallback | This document only | Description passed; missing-image case not run |

- Manual observations and screenshot ignore rule published in `c89975b`; PR #30
  remains draft with non-closing references to the four issues. Local screenshots
  have not been uploaded; issue descriptions disclose this evidence limitation.
- Next session: address/retest #31 before resuming the blocked manual exploration
  sequence. The force-stop recovery and history inspection remain NOT_RUN.

### Exact resumption checkpoint — environment left running

- User confirms the local environment has NOT been interrupted and is available
  to continue at any time. Android Studio and the existing emulator session are
  being retained. This is a user-confirmed handoff state, not a guarantee against
  later sleep, process termination or changes by other work.
- Branch: `test/api37-validation`; Draft PR #30. Code under manual test is represented
  by `9cfa2dd`; later commits only update documentation and screenshot ignore rules.
- Last completed action: M13b, selecting Discard after denied-location Start walking.
  Last observed app screen is screenshot 8: Sun Ribbon, Not exploring, timer 0:00,
  GPS No fix, Stop exploration. Back/Discard loops to the same dashboard (#31).
- Network was restored before M10 and has not been reported disabled since.
  Location permission remains Don't allow; neither Ask every time nor a manual
  grant has been performed. Sun Ribbon was saved and added as the sole planned stop.
- Do NOT assume a clean install, fresh emulator, ended session or successful discard.
  No app-data clear, uninstall, emulator wipe, force stop or Settings permission
  grant was performed as part of this checkpoint. Task-dismissal relaunches earlier
  in the test are recorded separately. No new code fix for #31 is installed.
- Next safe action: resume from the existing screen and confirm it still matches
  screenshot 8. Preserve the denied-permission reproduction for diagnosing #31.
  Do not repeat the already-proven Back/Discard loop as another test.
- If proceeding with the previously suggested recovery, explicitly record Force
  stop -> launcher reopen -> Saved/Journeys inspection as NEW actions. They remain
  NOT_RUN, including checking for an unintended zero-duration journey. Manual grant
  and positive-path exploration remain pending and must not be called a fix for #31.
- Continue the one-step-at-a-time question/feedback workflow and append observations
  here. Screenshots remain local in ignored `docs/test screenshot/` (1-8 available);
  not all supplied files have been inspected. Do not change unrelated PostgreSQL
  services or ports for this Android task.
