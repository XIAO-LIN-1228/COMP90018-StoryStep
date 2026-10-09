# StoryStep: factual report working draft

Revision: 2026-10-09. Status: DRAFT — NOT APPROVED.

This is an AI-assisted working document, separate from final_report_template.md.
Every section and subsection below is unapproved. No section may be labelled final
without the user's explicit approval of that section. Approval of one section does
not approve others; revised text must be reviewed again. No approval is recorded.

This working revision includes implementation-based discussion and dated results,
with explicit placeholders for missing evidence. Contributions remain blank for
members to complete. Physical-device, manual and user testing remain assigned to
other members; existing records are summarized without inventing new executions.
The user's independent feature is in progress and must not be represented as
completed or attributed in the contribution section yet.

## Progress status key

Each section has one progress status, separate from approval:

- `finished`: writing and required supporting material are complete; this does not imply approval.
- `on editing`: drafting, discussion or structural work remains.
- `need support doc`: supporting documents, test evidence or member information are missing.
- `need review`: a draft is available for wording/content review; stated limitations still apply.

`need review` is the standardized spelling of the requested "need reveiw" label.
No subsection is marked `finished` in this revision. All remain NOT APPROVED.
Progress labels summarize the next action, not test outcomes or feature completion.
## Authoring decision: final structure (confirmed 2026-10-09)

Progress: `on editing`. Approval: **NOT APPROVED**.

Keep this rubric-organized document as working material. For final presentation,
consolidate the justification into five themes: software quality; sensing/data/core
algorithms; interface and interaction; verification/performance/dynamic response;
and innovation/potential value. Retain a compact mapping for all 19 criteria and
cross-reference Material evidence rather than repeating it. Preserve the distinct
requirements and evidence gaps while reducing duplication, within ten rendered
pages for the justification section. The decision is recorded in notes.md.
Structural direction is approved; no report prose is thereby finalized. This
recording step does not reorganize or remove the existing drafts.

## Section review register — NOT APPROVED

Progress: `on editing`. Approval: **NOT APPROVED**.

| Template section | Current treatment | Approval |
|---|---|---|
| 1 Overview | Target users/problem/intended experience discussed; draft below | Not approved |
| 2 Video | Placeholder for supplied artifact | Not approved |
| 3.1 Environment | Configuration facts and dated reference environment | Not approved |
| 3.2 Build | Reproducible commands and verification limits | Not approved |
| 3.3 Phone run | Reserved for responsible members | Not approved |
| 3.4 Screenshot | Placeholder for actual IDE evidence | Not approved |
| 4 Contributions | Blank; members fill their own work | Not approved |
| 5 Rubric justification | Selected implementation and innovation drafts; dated tests and explicit evidence gaps | Not approved |
| 6 AI disclosure | Factual inventory; mandatory user review | Not approved |
| 7 References | Evidence pointers, not a completed bibliography | Not approved |

## Cover page — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-COVER: Insert group number, member names, student numbers, emails and GitHub
handles, plus report title/date. Do not add publicity consent without group approval.]

## 1. Target users, problem and intended experience — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep is designed for tourists who are unfamiliar with their surroundings and
need help deciding where to go during a local walking excursion. Their planning
challenge involves identifying available points of interest, judging which are
worth visiting in relation to their own interests, and arranging the selected
places into a practical visiting order. Users' interests guide these choices;
context such as distance, weather and time of day provides supporting information
rather than replacing their decisions. The project aims to connect
these decisions with an understanding of the places themselves, combining local
discovery and place-based interpretation within a coherent exploration experience.

The intended user journey is to discover nearby points of interest, learn about
them and select the places to visit. Based on those selections, the envisioned
experience provides an automatically generated walking itinerary and suggested
departure windows, allowing users to retain the plan for later or begin their
walk. Recording and managing previous journeys would extend this experience beyond
the immediate outing, enabling users to revisit their exploration history. The
overall aim is to support both practical trip planning and a more informed,
engaging encounter with an unfamiliar area.

Authoring boundary: this subsection describes the target experience, not a delivered
feature checklist or measured benefit. Departure-window advice has an existing
concept design in notes.md (Candidate A); the user confirmed on 2026-10-07 that its
implementation will progress alongside this report. Include it in the intended
contribution discussion, with completion and validation pending. Durable pre-trip
plan saving must be checked separately from existing
saved attractions and completed-journey storage. Automatic itinerary planning does
not imply turn-by-turn navigation or a guaranteed optimal route. Confirm actual
delivery before writing the final implementation overview. Interest-led choice is
a design objective, not a claim of implemented preference learning or a user-profile
recommendation model. Discussion direction confirmed; final wording approval pending.

## 2. Demonstration video — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-VIDEO: Supply the YouTube link, duration of no more than ten minutes,
demonstrated build/device and a timestamp map covering rubric-relevant features.
Verify that the link is accessible to assessors. No video completion is claimed.]

## 3. Compile and run instructions — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-RUN-INSTRUCTIONS: Reconcile Sections 3.1-3.4 with the final submitted build,
external configuration and actual installation evidence.]

### 3.1 Configuration and reference environment — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

The inspected repository defines an Android application using Java source code and
XML layouts. Java source and target compatibility are set to 11. The Gradle daemon
JVM criterion is 25; these are separate settings. The following table records the
configuration inspected on 2026-10-07, not a finalized submission manifest.

| Setting | Inspected value | Repository evidence |
|---|---|---|
| Android Gradle Plugin | 9.3.1 | gradle/libs.versions.toml |
| Gradle Wrapper | 9.5.0 | gradle/wrapper/gradle-wrapper.properties |
| Daemon JVM criterion | 25 | gradle/gradle-daemon-jvm.properties |
| Java source/target | 11 / 11 | app/build.gradle.kts |
| Android SDK | min 26; compile/target 37 | app/build.gradle.kts |
| Instrumentation runner | androidx.test.runner.AndroidJUnitRunner | app/build.gradle.kts |
| Espresso core/accessibility | 3.7.0 | gradle/libs.versions.toml |
| AndroidX Test core/runner | 1.7.0 | gradle/libs.versions.toml |
| AndroidX ext-junit | 1.3.0 | gradle/libs.versions.toml |

The original recorded device-test run used Medium_Phone_API_37.0, Android 17/API 37.
A subsequent environment snapshot recorded Google Play x86_64 image revision 6 /
extension 22, a 1080 x 2400 display, 420 dpi and font scale 1.0. Those later readings
must not be described as an independently captured snapshot at test execution.
Host and tool details, and the second machine's separately recorded results, are
in notes.md. Do not combine the two machines' results into one execution record.

Local SDK paths belong in ignored local.properties. They vary by machine and are
not supplied as portable repository settings. Final-version values and clean-package
reproducibility must be checked against the actual submission artifact.

### 3.2 Build and automated verification procedure — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

1. Extract the code into a local development directory and open the root containing
   settings.gradle.kts in Android Studio.
2. Install the SDK components required by the repository, configure the local SDK
   path and complete Gradle Sync. Use the checked-in wrapper and dependency versions.
3. From the root, run the automated checks:

```powershell
# Windows PowerShell
.\gradlew.bat test assembleDebug lint --console=plain
```

```bash
# macOS/Linux equivalent; not established as tested by the reference Windows run
./gradlew test assembleDebug lint --console=plain
```

4. Debug APK output: app/build/outputs/apk/debug/app-debug.apk.
5. With a test device/emulator connected and listed by adb devices, run:

```powershell
.\gradlew.bat connectedAndroidTest --console=plain
```

6. Inspect results, not only the Gradle success line:

| Result | Local report |
|---|---|
| JVM tests | app/build/reports/tests/testDebugUnitTest/index.html |
| Instrumented tests | app/build/reports/androidTests/connected/debug/index.html |
| Android Lint | app/build/reports/lint-results-debug.html |

Record device, date, commit, executed versus cached tasks and failures. This draft
does not establish a clean extraction/build of the final code ZIP. A shared OneDrive
.git index conflict was recorded during setup; use independent Git working copies
for reproducible cross-machine work rather than treating cloud sync as Git transport.

### 3.3 Physical-device run — RESERVED, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-PHONE-ENV: Responsible tester to supply phone model, Android/API version,
build/commit, available sensors, app permissions, language, font scale and network
conditions. Then record installation and launch steps and observed results. No
complete physical-device acceptance is established by the records reviewed below.]

### 3.4 Compilation screenshot — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[Insert the actual Android Studio Console screenshot with task, date, commit and
observed result. A terminal success line is not the required IDE screenshot.]

## 4. Individual contributions — BLANK, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[Each member fills and confirms their own completed contributions and evidence.
Keep the whole-team breakdown to one page. No entries are preassigned. The user's
independent feature remains in progress and is not listed as completed.]

| Student | Completed contribution | Evidence | Individual/shared responsibility |
|---|---|---|---|
| | | | |

## 5. Rubric criteria and justification — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-CONSOLIDATION: Retain this criterion-based working structure until the agreed
five-theme consolidation. Final justification, including tables/figures, must fit ten
rendered pages. All criteria must remain traceable.]

### 5.1 Coverage map — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-COVERAGE-MAP: Map all 19 rubric criteria to final sections, code/test evidence
and video timestamps. Do not infer earned marks or completed acceptance.]

### 5.2 Material - Report and video — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-MATERIAL-VIDEO: Cross-reference Section 2 and the final report coverage map;
confirm the video demonstrates the submitted application. Avoid repeating the script.]

### 5.3 Material - Screenshot — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-MATERIAL-SCREENSHOT: Cross-reference the legible Android Studio Console
compilation image in Section 3.4 and identify its build.]

### 5.4 Material - Commit log — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-COMMIT-EXPORT: Export Git history for the submitted code, package it as
required and record the archive name and covered revision. No archive verified yet.]

### 5.5 Material - Itemised contributions — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-MATERIAL-CONTRIBUTIONS: Cross-reference the one-page member-confirmed
breakdown in Section 4. Do not infer contributions from commit counts.]

### Automated evidence supporting Section 5 — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

### Execution results — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

The reference run recorded in EV-API37-INSTRUMENTATION-2026-10-05.md and notes.md
has the following outcomes. These are historical observations, not claims about
an untested final submission commit.

| Check | Recorded outcome | Evidence boundary |
|---|---|---|
| Earlier test assembleDebug lint | Successful; 846 JVM tests, 0 failures/errors/skips; Lint 0 errors / 112 warnings | Original reference-machine run |
| Initial connectedAndroidTest supplied by user | 75 tests, 7 failures, 0 skips | Failure log supplied in conversation |
| Run after dependency/test correction | 73 of 75 passed | Two accessibility target-size failures remained |
| Final combined test assembleDebug lint connectedAndroidTest | Successful; 75/75 device tests, no skips; Debug assembly passed; Lint 0 errors / 107 warnings | API 37 emulator; JVM task UP-TO-DATE, reusing earlier 846-test result |

### Subsequent targeted regressions on API 36 — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

Three records dated 2026-10-07 are present in reviewed remote main
`ede3f7e340338bd7f10547bec19be471e95158b1`. They describe executions on earlier
feature/fix commits, not a newly executed complete test suite on that main commit.

| Recorded change / commit | Automated result | Scope and limitations |
| --- | --- | --- |
| Planner recovery and controls, `b99643a` | 854 JVM tests, no failures/errors/skips; app/test APK builds; Lint 0 errors/113 warnings; four planner presentation tests passed at both 100% and 200% font scale on Pixel_8 API 36 | Permission-state and persistence logic, visible controls, rotation and no-session Back; real system-dialog interaction, API 37 and physical GPS not rerun |
| English dates, `b70f12b` | 854 JVM tests, no failures/errors/skips; builds; Lint 0 errors/113 warnings; four API 36 instrumentation tests passed | Test-process Chinese locale and timezone/date boundaries, not physical-phone retest |
| Exploring dashboard, `3079f48` | 862 JVM tests, no failures/errors/skips; builds; Lint 0 errors/113 warnings; three API 36 presentation tests at each of normal and 200% font scale | Synthetic map/heading/route fixtures; not real GPS, magnetometer, camera/gallery round trips or live routing acceptance |

Sources: [planner record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BUGFIX-PLANNER-2026-10-07.md),
[locale record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BUGFIX-DATE-LOCALE-2026-10-07.md),
[exploring record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BIGFIX-EXPLORING-2026-10-07.md).
Counts overlap and must not be summed. The earlier API 37 run and these API 36
regressions use different builds; they do not establish a final multi-version pass.

[PENDING-FINAL-AUTOMATION: On the selected submission commit, record test, assembly,
Lint and connected-test commands, executed/cached status, counts, device/API and
archived results. Explain remaining warnings; do not copy historical counts as a
new run. Result: NOT RECORDED.]

### Manual emulator and physical-device evidence — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

Historical manual emulator feedback in notes.md records successful onboarding via
Not now, nearby map/list display and bookmark persistence. It also records slow
initial attraction loading, incomplete content, the denied-start/Discard loop,
planner control visibility and weather-refresh concerns. These are observations
from that session, not acceptance of the later fixes. The slow-load report was
kept as documentation rather than a separate issue at the user's request.

The [optional-sensors record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-SENSORS-2026-09-23-01.md)
records an API 36 emulator camera round trip and photograph attachment to a
published journey. It also records absent step-counter handling and a synthetic
pressure display. These older observations establish neither physical step/climb
accuracy nor camera behavior on the final build. The October 7 locale record cites
physical-phone feedback about Chinese date labels, but explicitly says the updated
build has not been retested on that phone. The Exploring record similarly leaves
physical movement, compass and external camera/gallery verification outstanding.

Issues #31/#32 have subsequent fixes and focused automated evidence; their old
failures should not be described as confirmed defects on reviewed main. Conversely,
passing focused tests alone does not close all their manual acceptance gaps.
The planner patch explicitly excludes #33/#34. The last queried GitHub state has
all four issues open.

| Placeholder | Required action and evidence | Current result |
| --- | --- | --- |
| PENDING-MANUAL-PERMISSIONS | On the chosen final build, exercise first permission request, denial/settings-required denial, cancellation, return from Settings and explicit Start. Confirm no nonexistent-session Discard loop; record device/API and video or steps. | Not established for final build |
| PENDING-MANUAL-LAYOUT | Repeat planner states at all three sheet positions, portrait/landscape and 100%/200% font. Record control visibility and bottom-navigation label clipping separately. | Focused API 36 automation only; final manual acceptance pending |
| PENDING-PHYSICAL-SENSORS | Record available hardware; perform stationary, walking and rotation-only trials, known-destination arrival and pause/resume. Compare compass to a stated reference, step count to a counted walk and climb to a stated reference. Record errors, unavailable states and actual traces. | No physical accuracy conclusion |
| PENDING-PHYSICAL-MEDIA | Capture and import images using the phone's real apps, finish/publish, reopen the journey and verify retained images. Record permissions, failures and build. | Final-phone round trips not recorded |
| PENDING-NETWORK-RECOVERY | Test empty/cache-filled offline states, reconnect without relaunch, verify labels and refresh, unavailable route service and fallback labeling. Record time, network transitions and observed UI for #33. | Current end-to-end recovery not established |
| PENDING-PHONE-LOCALE | On the reporting phone with Chinese system language, inspect Profile, journey list/detail and date labels after installing the fix. | Synthetic locale tests passed; physical retest pending |
| PENDING-PERFORMANCE-ACCESSIBILITY | Measure interaction/network waits and sustained-walk battery behavior on a named device; conduct TalkBack and large-font manual checks. Include duration, method and observations. | No general responsiveness, battery or accessibility pass |

Testing remains assigned to the responsible members. This review did not operate
an emulator/phone or run Gradle. Placeholders are requests for evidence, not claims
that the listed checks have been executed.

### Diagnosed failures and changes — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

Six initial failures occurred in Espresso input injection with an unavailable
InputManager.getInstance method. AndroidX Test dependencies were updated. A seventh
failure came from a presentation test looking for sensor status in Profile after
that information moved to Settings. The assertion was changed to open Settings
through the existing Profile control and check the destination and status there.

With the checks running, two 40dp touch targets were reported. The shared button
size changed to 48dp with 14dp padding, retaining the previous 20dp icon area. The
subsequent 75-test run passed; accessibility checks were not suppressed. These facts
support a description of the validation/repair process. They do not independently
establish code readability, complete accessibility or all user flows.

Evidence: [dated verification](verification/EV-API37-INSTRUMENTATION-2026-10-05.md),
[session notes](../notes.md), [PR #30](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/30).

### 5.6 Implementation - Quality — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

The reviewed main commit separates Android presentation, application coordination,
domain calculations, repositories and platform adapters. For example,
`MovementClassifier`, `ArrivalPolicy` and `CircularSmoother` express computation in
plain Java, while `AndroidSensorHub` handles platform subscriptions. This separation
makes computational rules independently testable and exposes platform behavior as
a distinct verification responsibility. `PlanStartViewModel` retains pending start
state across recreation and only reports readiness after a session exists, linking
navigation to persistence rather than assuming a start request succeeded.

Repository policies explicitly handle asynchronous work and cached data. Room's
version-12 database exports schemas and registers migrations instead of selecting
destructive migration. These are concrete maintainability and data-preservation
design choices, not proof that every path is correct. Review also found a stale
version-10 description above the actual version-12 database annotation, illustrating
that documentation consistency still needs attention. Recorded Lint runs have
warnings, and no blanket claim of warning-free or uniformly reviewed code is made.

[PENDING-QUALITY-REVIEW: Owners to review final changed files for naming, formatting,
stale comments and error handling; identify warning categories and migration-test
results on the submission build. Record reviewer, commit and findings. No project-wide
readability or migration-coverage signoff is recorded here.]

### 5.7 Implementation - Sensors — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

Location supports nearby discovery, journey recording, distance and arrival
decisions. Accelerometer input feeds movement estimation; accelerometer and
magnetometer inputs support orientation, with true-heading conversion and circular
smoothing used for directional presentation. `AndroidSensorHub` manages shared
subscriptions and releases unused sensors. Optional step-counter and pressure
adapters support walk metrics, and camera integration associates photographs with
a journey. Missing optional capabilities are represented as unavailable rather
than invented zero measurements.

Reviewed main includes a separate foreground home-discovery location flow in
`NearbyAttractionsViewModel`; location acquisition must not be described as occurring
only after a journey starts. Journey recording remains a separate concern. Multiple
sensors are integrated, but this does not mean all six jointly determine arrival.
Historical synthetic tests establish selected calculation and display behavior;
they do not establish outdoor accuracy, calibration or battery cost. Complete
PENDING-PHYSICAL-SENSORS and PENDING-PHYSICAL-MEDIA before claiming hardware acceptance.

### 5.8 Implementation - Connectivity — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

The app consumes external place, media, weather and routing information through
remote data sources: Overpass supplies nearby places, Wikimedia sources enrich
content, Open-Meteo supplies current conditions and Valhalla provides pedestrian
routes through requested points. Map tiles provide the geographic display. Cached
repositories separate network acquisition from presentation; the weather repository
coalesces requests by area, dispatches I/O separately from callbacks, and classifies
cached observations before returning fresh or fallback results. Content availability
and service failures remain visible limitations rather than guaranteed coverage.

This is client integration with external services, not evidence of a self-hosted
project backend. Area-based weather/place queries do not imply all requests hide
precise coordinates: `ValhallaRouteRequest` sends the supplied route coordinates.
Caching does not establish complete offline navigation. Departure forecasts and any
new backend remain implementation/validation pending. PENDING-NETWORK-RECOVERY must
be completed for final-build service and offline/reconnect claims; deployment-specific
provider/key configuration must be checked before final compilation instructions.

### 5.9 Responsiveness: evidence boundary — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

No automated runtime-latency benchmark is established by the cited runs. Build time,
test-suite duration, a passing functional test and a Lint result are not measures of
screen-render latency, frame pacing, network wait or sustained-session resource use.
This revision therefore makes no positive runtime-responsiveness claim. Responsible
members must supply relevant performance evidence for this required criterion.

### 5.10 Implementation - Technical depth — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

Arrival detection combines spatial uncertainty with temporal evidence. The inspected
default policy requires distance plus reported accuracy within 50 metres, accuracy
within 25 metres and at least three accepted fixes spanning ten seconds; fifteen-second
gap and sample-age limits prevent treating disconnected or old samples as a continuous
stay. These are implementation parameters, not empirically proven optimal thresholds.

Movement classification removes a time-dependent gravity estimate, measures variation
in linear-acceleration magnitude over two-second windows, and uses separate stationary
and moving thresholds with agreement across two windows. Circular heading smoothing
operates on sine/cosine components to avoid the discontinuity between 359 and 1 degrees.
These mechanisms address noisy and cyclic sensor inputs; their field accuracy still
requires recorded trials. Recommendation ranking applies explicit weighted factors
with deterministic ordering and neutral treatment of unsuitable weather data. Planned
stop ordering uses a nearest-neighbour heuristic; street-path computation is delegated
to Valhalla, so neither a novel shortest-path algorithm nor global route optimality
is claimed.

[PENDING-DEPARTURE-TECHNICAL-EVIDENCE: Inspect the delivered forecast-window algorithm,
duration aggregation, scoring/explanations, timezone and missing-data handling; record
exact code and boundary-test results. The concept alone is not implemented technical
depth. PENDING-PHYSICAL-SENSORS remains necessary for real sensing performance.]

Review basis for Sections 5.6-5.8 and 5.10: remote main
`ede3f7e340338bd7f10547bec19be471e95158b1`, inspected on 2026-10-09 without merging
it into this documentation branch. Code references are under
`app/src/main/java/com/comp90018/storystep/`; named classes are evidence anchors,
not a claim of exhaustive code review. Historical test results are summarized above.

### 5.11 User Interface - Appeal — PLACEHOLDER, NOT APPROVED

Progress: `on editing`. Approval: **NOT APPROVED**.

[PENDING-APPEAL: Review final screenshots and discuss visual hierarchy, typography,
colour, spacing and map/content balance. Describe intentional design choices with
examples; distinguish those choices from user preference. Obtain actual feedback
before claiming aesthetic satisfaction. Discussion and prose drafting pending.]

### 5.12 User Interface - Guidelines — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

StoryStep integrates Android navigation and permission flows with Material controls
and dialogs. MainActivity enables edge-to-edge rendering and applies system-bar
insets. The custom bottom tab bar delegates destination selection to NavigationUI.
Shared resources define a 48dp minimum touch target; selected layouts use expandable
button heights and polite live regions for status changes. These are identifiable
platform-integration measures, not proof of universal guideline compliance.

Historical API 37 accessibility checks contributed to correcting undersized controls.
AccessibilityChecksTest scans six screen hierarchies, but cannot establish meaningful
spoken labels or reading order. The October 7 API 36 planner record supports control
visibility at normal and 200% font scales, while also reporting bottom-navigation
label clipping at 200%. Complete large-text and assistive-technology acceptance
therefore remains outstanding.

[PENDING-UI-GUIDELINES: On the final build, record device/API, font scale and navigation
mode; check all main screens/dialogs with TalkBack, focus order, meaningful labels,
touch targets, system-bar clearance and large text. Recheck bottom-tab clipping.
Identify applicable Android guidance for final citations. No complete accessibility
or platform-guideline conformance is established here.]

### 5.13 User Interface - Flow — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

The navigation graph connects onboarding and optional location access to nearby
discovery, attraction details, planning, exploration and journey history. Explore,
Plan, Journeys and Profile provide top-level destinations, with saved places and
settings reachable through Profile. Arrival prompts offer View story or Later,
while the curated theme exposes station progress and optional questions. These
routes structure the transition from discovering places to visiting and reviewing them.

The revised planner places Add a place and Start walking outside its sliding content.
Starting checks location access and services; PlanStartViewModel waits for a stored
session before signaling navigation readiness. Cancellation preserves planning, and
the no-session Back path does not offer to discard a nonexistent journey. Focused
planner tests support these recovery and layout changes, but do not establish a
complete first-time-user walkthrough. Earlier manual feedback found Not now
inconspicuous; the planner patch explicitly leaves #34 outside its scope.

[PENDING-UI-FLOW: Complete fresh onboarding, decline and later grant location,
discover/select places, start/pause/resume/finish a walk, and reopen saved places/history
on the final build. Record back-navigation, cancellation, recovery and confusing
steps; repeat relevant steps after rotation/restart. Unstarted plans are not claimed
to persist. Departure advice's entry, duration selection and return path require
implementation and a separate walkthrough. No complete user-tested flow pass is recorded.]

### 5.14 User Interface - Language — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

English resource strings describe actions and states, including Retry, Retry saving
this arrival, Waiting for a location fix and Compass unavailable. Text distinguishes
missing measurements from valid values and labels the fallback route as a straight-line
plan. These choices aim to explain what is known and what users can do, without
implying precise navigation or successful persistence prematurely. HistoryText formats
dates in English while retaining the relevant timezone; the October 7 locale tests
exercise Chinese process configuration and date boundaries to support that change.

Some wording needs further review. The onboarding statement "We only use it during
an active exploration" may suggest location is obtained only after a journey starts,
whereas reviewed main also supports foreground home discovery. Permission explanations
should distinguish discovery from journey recording and match the actual lifecycle.
Earlier weather-label concerns and Not now feedback also show that text presence
alone does not establish clarity or prominence.

[PENDING-UI-LANGUAGE: Review rendered permission/onboarding text against actual home
and journey location behavior; agree and verify any revised wording. Check weather
age/reference-area/offline labels through network transitions and English date labels
on the reporting phone with Chinese system language. Ask intended users to explain
key actions/messages and record misunderstandings before claiming comprehension.
Departure explanations require review once implemented. Complete localization and
user comprehension have not been demonstrated.]

Evidence basis for 5.12-5.14: selected code at remote main `ede3f7e`, including
MainActivity, nav_graph.xml, PlanFragment, PlanStartViewModel, ThemeFragment,
strings.xml, dimens.xml, HistoryText and AccessibilityChecksTest. Dated planner/locale
and historical manual records are linked in the testing section above. This is
static inspection and evidence synthesis, not new UI testing. All drafts are NOT APPROVED.

### 5.15 Reactiveness: evidence boundary — DRAFT, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

Sensor/external-data reactiveness is distinct from runtime latency. The October 7
Exploring record supports selected rendering behavior with injected heading/route
fixtures and JVM checks of heading expiry and stale route responses. It does not
establish physical sensor tracking or live network recovery. Complete
PENDING-PHYSICAL-SENSORS and PENDING-NETWORK-RECOVERY before broader claims; general
test counts alone do not prove real-world dynamic behavior.

### 5.16 Innovation - Novelty — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep's proposed novelty lies in connecting practical decisions with on-site
understanding for tourists unfamiliar with their surroundings. Existing products
already support itinerary organisation, route weather and location-triggered
interpretation: examples include [Wanderlog](https://wanderlog.com/plan-a-trip),
[komoot](https://www.komoot.com/premium/weather) and
[izi.TRAVEL](https://www.izi.travel/en/app). StoryStep brings these needs into a
local walking experience: users select places, receive explainable contextual
recommendations and a walking itinerary, and access stories after confirmed
arrival. A curated campus theme further connects visits with optional observation
questions. This design supports users' own choices while extending discovery into
an opportunity to understand the places they visit.

The departure-window feature, whose concept is designed and whose implementation
will progress alongside this report, extends that decision support to when to
leave. It is designed to compare forecast conditions across the user's intended
walking duration and explain the relative suitability of upcoming departure
windows using precipitation probability, apparent temperature and wind. The
intended contribution is actionable advice covering an outing's duration, connecting
where to go and in what order with when to begin. We therefore frame novelty as a
specific integration of visitor decisions and place-based interpretation, rather
than claiming the first use of maps, weather or location-triggered stories.

Authoring/evidence boundary: this is an unapproved evaluative draft. The comparison
does not establish market-wide uniqueness or superiority. Departure advice requires
implementation and validation evidence before completed-feature wording is used;
no comfort improvement or educational outcome has been measured here. Curated
questions are not generated for arbitrary selected places. Supporting code and
comparison sources: [research record](novelty_comparison_research.md); concept:
[Candidate A in session notes](../notes.md). Reconcile final delivery and citations
before submission; unresolved manual-test issues remain recorded separately.

### 5.17 Innovation - Surprise — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep extends the familiar experience of finding a destination on a map by
making arrival an opportunity for discovery. In the curated campus theme, confirmed
arrival unlocks a place's story and optional observation question, inviting users
to notice details such as a building's tower or repeated arches. Each visited stop
therefore adds both a physical experience and a piece of local knowledge. Visible
station status and retained progress are intended to encourage an interest in
collecting these experiences: users can see what they have explored and may feel
motivated to discover the next story or complete the theme. The collection consists
of visited places, unlocked content and exploration records, connecting a sense of
progress with attention to the surrounding environment.

The departure-window feature being developed alongside this report is intended to
provide a complementary extension: weather information becomes a reasoned suggestion
about when to begin an outing. By comparing forecast windows across an intended
walking duration, it aims to help users act on weather information rather than
interpret individual readings themselves. Together, these designs extend a basic
map-and-weather experience into prompts for when to explore and what to discover
on arrival.

Authoring/evidence boundary: the collection motivation and sense of surprise are
design intentions, not measured user responses or evidence of increased engagement.
The implemented theme has three curated Parkville stops; questions do not apply to
every attraction. No badges, points, leaderboard or reward system is claimed.
`ThemeFragment` controls arrival-gated content, `RoomThemeRepository` validates
arrival before accepting answers, and `ThemeCatalog` supplies the observation
content. Departure advice has an existing concept design but its implementation
was not found in the inspected workspace; inspect and validate the progressing
implementation before converting this paragraph to completed-feature wording.
This subsection remains subject to user review and is not final.

### 5.18 Innovation - Tech Knowledge — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep integrates mobile sensing, geospatial computation, external data and
persistent application state to connect physical exploration with digital content.
Location samples are checked for accuracy, freshness and sustained proximity before
an arrival is accepted. A visit is then persisted before its story is presented,
linking sensing decisions to recoverable exploration records. Movement classification
and heading processing provide additional context and directional guidance; they
serve distinct purposes rather than all determining arrival.

The application also combines heterogeneous information sources: OpenStreetMap
supplies places, Wikimedia sources enrich their descriptions and images, Open-Meteo
provides current weather, and Valhalla supplies pedestrian routes. Local rule-based
ranking translates distance, weather, time, saved places and previous visits into
recommendations with explanatory reasons. Cache and freshness policies help manage
network-dependent information, including preventing stale or wrong-area weather
from influencing ranking. This connects data acquisition and quality assessment
with user-facing decisions, while local storage retains visits and completed journeys.

The departure-window feature progressing alongside this report extends the same
approach to temporal decision support. Its design compares forecast conditions over
the intended walking duration, ranks candidate departure windows and explains their
relative suitability. Timezone, missing-data and freshness handling are part of the
design so that the result represents a usable timing suggestion rather than a list
of forecast readings. Across these mechanisms, the technical contribution is the
coordination of sensing, information processing, decision rules and state persistence
within a mobile exploration workflow.

Authoring/evidence boundary: implementation evidence is recorded in the
[code/comparison research](novelty_comparison_research.md), including
`ArrivalDetector`, `ExplorationCoordinator`, `RecommendationEngine`, the cached
repositories and the Valhalla integration. This is not a claim of learned
personalization, globally optimal routing, complete offline navigation or fault-free
end-to-end behavior. Departure-window algorithms, interfaces and any backend division
require inspection and validation as implementation progresses; no deployed custom
backend is claimed. This draft does not establish physical sensor accuracy or new
test results and remains subject to explicit user approval.

### 5.19 Innovation - Cross-Disciplinary — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep connects mobile computing with local heritage interpretation and
observation-based learning. Its curated Parkville theme combines location-triggered
access with short accounts of campus architecture and botany. Questions ask users
to observe features in front of them, such as the Old Quad's arches or the System
Garden's surviving tower. The physical setting therefore becomes part of the
interaction: location establishes arrival, while the content directs attention
to the site's history and visible details. Retained station progress also applies
a collection-oriented interaction design, intended to encourage further discovery
without making optional questions a requirement for recording a visit.

The departure-window concept adds an application of weather information to tourism
planning. It proposes interpreting precipitation probability, apparent temperature
and wind across an intended walk, then presenting explained timing alternatives.
This connects environmental information with a practical visitor decision rather
than treating weather as a separate display. Together, the theme and timing advice
link computational mechanisms to understanding places and planning an outing.

Authoring/evidence boundary: `ThemeCatalog`, `ThemeFragment` and
`RoomThemeRepository` support the implemented theme description; the catalog retains
links to university sources for its content. Observation and collection are design
approaches here, not claims of a validated educational or psychological intervention.
Departure advice remains subject to implementation inspection and validation, and
no scientifically validated human-comfort model is claimed. Final references must
retain the content sources. This subsection is DRAFT, NOT APPROVED.

### 5.20 Innovation - Impact — DRAFT, NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep aims to help tourists unfamiliar with an area turn uncertain intentions
into a manageable local outing. Bringing nearby places, contextual recommendations
and walking plans together may reduce the effort of identifying destinations and
organising a visit. Users retain their own choices, while explanations provide
context for deciding which places suit the outing. Arrival-linked stories and
optional observation questions offer opportunities to notice and understand local
features that might otherwise be passed without attention. Saved places and
completed-journey records support returning to earlier discoveries and reviewing
past exploration.

Departure-window advice is intended to extend this support to the timing of a walk,
helping users compare forecast conditions across its expected duration. Its potential
benefit is a more informed departure choice, not guaranteed comfort or safety. The
project's intended impact is therefore practical and experiential: supporting
planning decisions while encouraging a closer engagement with unfamiliar surroundings.

Authoring/evidence boundary: these are potential benefits, not measured outcomes.
The recorded automated tests do not establish reduced planning time, improved
learning, greater engagement or user satisfaction. Responsible members' user and
field evaluations are needed to assess those outcomes. Content coverage, forecast
uncertainty, device capabilities and unresolved manual-test issues limit the current
experience; departure advice also requires delivery evidence. No health, economic
or environmental benefit is inferred. This subsection is DRAFT, NOT APPROVED.

## 6. AI assistance inventory — DRAFT; USER REVIEW MANDATORY; NOT APPROVED

Progress: `need review`. Approval: **NOT APPROVED**.

This is a factual inventory for review, not approved disclosure prose. The user must
review each entry and the eventual wording before any of this section is finalized.
Other members must supply their tool use; this session is not a complete team record.

| Tool | Recorded assistance | Human review status |
|---|---|---|
| OpenAI Codex | Prepared report structure and editable draft from supplied assessment instructions | User review required; not approved |
| OpenAI Codex | Investigated failures, edited test dependencies/test code/button resources and ran tool-driven verification | User must describe actual review; no independent-authorship claim |
| OpenAI Codex | Suggested manual test steps, transcribed user-reported results and inspected supplied screenshots | Historical assistance only; current manual testing belongs to other members |
| OpenAI Codex | Drafted issue descriptions, notes and optional feature proposals | User review required; proposed implementation not claimed complete |
| OpenAI Codex, including two user-requested research agents | Compared official product sources with application code and drafted Section 5.16 following the user's discussion and scope correction | User review required; Novelty prose not approved |
| OpenAI Codex | Inspected arrival-gated theme behavior and drafted Section 5.17 from the agreed collection-motivation and departure-advice discussion | User review required; Surprise prose not approved |
| OpenAI Codex | Drafted Section 5.18 from the agreed technical-integration outline and earlier code inspection, separating delivered mechanisms from departure-advice design | User review required; Tech Knowledge prose not approved |
| [Other tools: members to supply] | [Concrete uses and affected material] | [Actual review to be supplied] |
| OpenAI Codex | Reviewed selected remote-main code and dated verification documents; drafted Quality, Sensors, Connectivity, Technical depth and test-evidence summaries with pending checks | User review required; no new tests executed or sections approved |
| OpenAI Codex | Drafted Sections 5.19 and 5.20 from prior code inspection and design discussion, distinguishing intended benefits from measured outcomes | User review required; both subsections not approved |

No model version, reviewer signoff or validation activity may be invented. A missing
Co-authored-by trailer does not replace the report's required AI disclosure.

## Evaluative sections — DISCUSSION ONLY, NOT APPROVED

Progress: `on editing`. Approval: **NOT APPROVED**.

Overview, selected implementation sections, testing summaries and Innovation Sections
5.16-5.20 have unapproved drafts above. Remaining sections
require discussion one at a time: agree the intended claim, identify evidence, then
draft for explicit approval.

| Section | Question for later discussion |
|---|---|
| 1 Overview/problem framing | Draft above; discussion direction confirmed, wording awaiting approval |
| 5.6 Quality | Draft based on selected remote-main code; final owner review and warnings/migration evidence pending |
| 5.7 Sensors / 5.8 Connectivity / 5.10 Technical depth | Drafts above; physical validation, final network recovery and departure implementation evidence explicitly pending |
| 5.11 Appeal | What visual decisions are intentional, and what evaluation supports their effect? |
| 5.12 Guidelines | Draft above; selected platform measures, with TalkBack and large-text gaps explicit |
| 5.13 Flow / 5.14 Language | Drafts above; navigation/recovery and state wording, with walkthrough/comprehension placeholders |
| 5.16 Novelty | Draft above; includes designed departure advice with implementation/validation pending; wording awaiting approval |
| 5.17 Surprise | Draft above; arrival unlocks and collection motivation, plus progressing departure advice; wording awaiting approval |
| 5.18 Tech Knowledge | Draft above; sensing, external-data quality, decision support and persistence; departure implementation evidence pending; wording awaiting approval |
| 5.19 Cross-Disciplinary | Draft above; heritage interpretation, on-site observation and weather-informed tourism planning; awaiting review |
| 5.20 Impact | Draft above; potential planning and exploration benefits, with outcomes unmeasured; awaiting review |

## 7. References and attributions — PLACEHOLDER, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-REFERENCES: Consolidate cited competitor, content, technical and tool sources
into a checked reference list. Retain attribution, use a consistent format and verify
that citations support the final wording. Existing links are working evidence only.]

## Required material and references — PLACEHOLDERS, NOT APPROVED

Progress: `need support doc`. Approval: **NOT APPROVED**.

Sections 2 and 5.2-5.5 require the actual video, compilation image, commit-log export
and member-confirmed contribution sheet. This draft does not establish their completion.
Section 7 needs a checked bibliography and attributions. Existing evidence links are
working references, not a completed citation inventory. Retain all 19 rubric criteria;
keep Section 5 within ten rendered pages. No publicity consent is implied.
