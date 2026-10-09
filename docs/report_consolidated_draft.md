# StoryStep — Consolidated Report

Revision: 2026-10-09. Progress: `on editing`. Approval: **NOT APPROVED**.

Authoring note: this is the formal-structure working version, not an approved final
submission. The criterion-by-criterion draft remains unchanged in
[report_individual_working_draft.md](report_individual_working_draft.md).
Progress uses `finished`, `on editing`, `need support doc` or `need review` independently
of approval. No section is final. Resolve PENDING items and remove authoring/status
annotations before approved PDF export; never remove an evidence gap by implying a pass.
Section 5 must fit ten rendered pages, including its tables and figures; pagination
has not yet been verified. Detailed test requests remain in the
local-only team handoff at docs/tempdoc/report_evidence_handoff_zh.md (not published to Git), rather than being repeated throughout.

## Cover page

Progress: `need support doc`. Approval: **NOT APPROVED**.

**Subject:** COMP90018 — Mobile Computing Systems Programming

**Project:** StoryStep

**Group:** [PENDING-GROUP]

**Submission date:** [PENDING-DATE]

**Submitted commit:** [PENDING-FINAL-COMMIT]

| Student name | Student number | University email | GitHub handle |
| --- | --- | --- | --- |
| [PENDING: one confirmed row per member] | | | |

<!-- Publicity consent is optional; do not insert it without explicit group agreement. -->

## 1. Application overview

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep is an Android tourism companion for visitors unfamiliar with their
surroundings. It addresses three connected decisions: which nearby places are
available, which fit the visitor's interests, and how to organise a walk between
them. Users retain destination choice, while distance, weather and time provide
supporting context. The application combines nearby discovery and walking plans
with arrival-linked stories, a curated campus observation theme, saved places and
local journey records. Its purpose is to connect practical planning with an
opportunity to understand the places encountered.

Departure-window advice is being developed alongside this report to extend support
to when to leave. Its design compares forecast conditions over the intended walking
duration and explains the relative suitability of candidate departure times. It is
not yet counted as verified delivery. The inspected implementation also does not
persist unstarted plans or provide turn-by-turn navigation; saved places and completed
journeys are separate from saving an itinerary for later.

[PENDING-SCOPE: Reconcile this overview and Section 5 with the final submitted commit,
especially departure advice and any optional, currently unmerged provider features.]

## 2. Demonstration video

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-VIDEO: Insert accessible YouTube link, duration (maximum ten minutes),
demonstrated commit/APK and physical device. Supply the timestamp map below.]

| Demonstration | Timestamp | Related report section |
| --- | --- | --- |
| Discovery, selection and planning | [Pending] | 5.2, 5.3 |
| Physical sensing, arrival and stories | [Pending] | 5.2, 5.5 |
| External data and changing conditions | [Pending] | 5.2, 5.4 |
| History, photos and recovery | [Pending] | 5.1, 5.3 |
| Departure advice, if delivered | [Pending scope] | 5.2, 5.5 |

## 3. Compilation and operation

Progress: `need support doc`. Approval: **NOT APPROVED**.

### 3.1 Environment and prerequisites

Progress: `need support doc`. Approval: **NOT APPROVED**.

The inspected configuration uses Java/XML, Java 11 source compatibility, Android
minSdk 26 and compile/target SDK 37, AGP 9.3.1 and Gradle 9.5.0. The Gradle daemon
toolchain criterion is JDK 25, distinct from Java source compatibility. These are
reference values, pending verification against the submission archive.

[PENDING-ENV: Supply tested host OS, Android Studio and SDK components, toolchain
provisioning requirements, physical phone/API and emulator image if used. List
Internet-dependent services and any required configuration; never publish secrets.
Confirm optional providers against delivered scope rather than assuming every remote
branch is included. Local SDK paths belong in ignored local.properties.]

### 3.2 Build and install

Progress: `need support doc`. Approval: **NOT APPROVED**.

Open the extracted project root in Android Studio, configure the required SDK and
complete Gradle Sync. Use the checked-in wrapper from the project root:

```powershell
.\gradlew.bat test assembleDebug lint --console=plain
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. Connect
a phone with USB debugging enabled, authorize the host, select the device and run
the app configuration in Android Studio. For instrumentation, verify the target
with `adb devices` and run:

```powershell
.\gradlew.bat connectedAndroidTest --console=plain
```

[PENDING-INSTALL: Validate these steps from the packaged code on the stated environment;
record permission choices and the first-run result. Do not claim a clean-package or
physical-device pass until executed.]

### 3.3 Compilation evidence

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-COMPILE-IMAGE: Insert a legible Android Studio Console screenshot showing
successful compilation, captioned with date and submitted build identity.]

## 4. Individual contributions

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-CONTRIBUTIONS: Each member supplies and confirms completed contributions and
supporting evidence. Keep the full breakdown to one rendered page. In-progress work
is not claimed complete, and commit counts do not determine contributions.]

| Member | Completed contribution | Evidence | Individual/shared responsibility |
| --- | --- | --- | --- |
| [To be completed by members] | | | |

## 5. Design, implementation and evaluation

Progress: `on editing`. Approval: **NOT APPROVED**.

The following five themes cover the rubric without repeating the same mechanism
under separate headings. The map also locates the four required material criteria.

| Rubric criterion | Where addressed / evidence |
| --- | --- |
| Report & Video | Sections 1–7; video and timestamps in Section 2 |
| Screenshot | Section 3.3 |
| Commit log | [PENDING-COMMIT-ARCHIVE: export for submitted code and record ZIP name] |
| Itemised contributions | Section 4, one page |
| Quality | 5.1 |
| Sensors | 5.2; physical evidence in 5.4 |
| Connectivity | 5.2; recovery evidence in 5.4 |
| Responsiveness | 5.4, measured runtime behavior |
| Technical depth | 5.2, sensing and decision mechanisms |
| Appeal | 5.3, visual design evidence |
| Guidelines | 5.3, platform/accessibility measures |
| Flow | 5.3, navigation and recovery |
| Language | 5.3, explanations and consistency |
| Reactiveness | 5.4, responses to sensor/external changes |
| Novelty | 5.5, specific integration and comparison |
| Surprise | 5.5, arrival-linked discovery |
| Tech Knowledge | 5.2 and 5.5, integrated computing concepts |
| Cross-Disciplinary | 5.5, interpretation and observation |
| Impact | 5.5, potential value and evidence limits |

Implementation discussion is based on inspected remote main `ede3f7e` [R1].
Historical test records below apply to their stated revisions, not automatically to
that commit or the eventual submission. [PENDING-BASELINE: update final evidence map.]

### 5.1 Software design and implementation quality

Progress: `need support doc`. Approval: **NOT APPROVED**.

StoryStep separates presentation, application coordination, domain computations,
repositories and platform adapters. Plain-Java classes such as MovementClassifier,
ArrivalPolicy and CircularSmoother isolate algorithms from Android lifecycle code;
AndroidSensorHub owns platform subscriptions. This supports deterministic tests
while keeping real sensor behavior a separate validation responsibility.

State handling connects actions to durable results. PlanStartViewModel retains a
pending start across recreation and signals readiness only when a session exists.
Arrival processing saves a visit before exposing its story. Room schema version 12
exports schemas and registers explicit migrations rather than destructive migration.
These mechanisms support recoverable records and make failure paths explicit.

The design is not evidence of uniform code quality or complete correctness. Review
found a stale version-10 database comment above the version-12 annotation, and recorded
Lint runs still contain warnings. [PENDING-QUALITY: owners to confirm final review,
warning disposition and applicable migration-test results.]

### 5.2 Sensing, external data and core algorithms

Progress: `need support doc`. Approval: **NOT APPROVED**.

Location supports discovery, recorded movement and arrival. Accelerometer input
supports movement estimation; accelerometer and magnetometer readings support
orientation, with true-heading conversion and circular smoothing. Optional step and
pressure adapters supply walk metrics when available, and camera integration attaches
photographs. Missing capabilities are represented as unavailable. Main includes
foreground home discovery separately from journey recording; acquiring location is
not restricted to an already-started journey.

Arrival detection combines spatial uncertainty with temporal evidence. The inspected
policy requires distance plus accuracy within 50 metres, accuracy within 25 metres,
and at least three accepted fixes spanning ten seconds, with fifteen-second gap and
sample-age limits. Movement classification removes a time-dependent gravity estimate,
measures magnitude variation over two-second windows and uses hysteresis plus
agreement across two windows. Heading smoothing operates on sine/cosine components,
avoiding the discontinuity between 359 and 1 degrees. These are substantive sensing
mechanisms, but their starting parameters are not field-validated accuracy guarantees.

Overpass supplies nearby places, Wikimedia sources enrich descriptions/images,
Open-Meteo supplies current weather and Valhalla supplies pedestrian routes; map
tiles provide the geographic display. Cached repositories classify freshness,
coalesce requests and provide labelled fallback behavior. Route requests contain
their supplied coordinates: coarse weather/place queries do not imply that every
request avoids precise positions. Cached content is not complete offline navigation.

Recommendation ranking combines distance, weather, time, saved and unvisited factors
with deterministic reasons; unsuitable weather data is neutral. This is a rule-based
policy, not learned personalization. Stop ordering uses a nearest-neighbour heuristic;
street-path computation is delegated to Valhalla rather than claimed as a new routing
algorithm or globally optimal plan. Together, these mechanisms integrate sensing,
geospatial computation, data-quality handling and persistent state for a user task.

[PENDING-DEPARTURE-IMPLEMENTATION: add the delivered forecast-window algorithm/API,
duration aggregation, explanation and boundary-test evidence once implemented.
The current concept does not establish completed technical depth or a custom backend.]

### 5.3 Interface design and interaction

Progress: `need support doc`. Approval: **NOT APPROVED**.

Explore, Plan, Journeys and Profile form the main navigation, with saved places and
settings reachable through Profile. Detail, exploration and history screens connect
selection with visiting and recollection. Arrival prompts offer View story or Later;
theme questions remain optional. The revised planner puts Add and Start outside the
sliding content, checks access and waits for session creation before navigation.
Cancellation preserves planning, and no-session Back does not discard a nonexistent
journey. These are intended to keep actions and recovery understandable [R2].

MainActivity applies system-bar insets and delegates tab destination handling to
NavigationUI. Material controls, a shared 48dp touch-target resource, expandable
button heights and selected live-region status updates provide concrete platform
integration. Automated accessibility checks do not establish useful spoken labels
or reading order. The planner record still identifies bottom-tab clipping at 200%
font scale, so general large-text compliance is not claimed [R2].

English strings distinguish missing values and recoverable actions, including waiting
for a location fix and retrying an arrival save. The fallback route is labelled a
straight-line plan. English date formatting was corrected for non-English device
settings, with targeted automated evidence [R3]. Permission wording still needs
reconciliation with home discovery: “during an active exploration” may imply a
narrower location lifecycle than the implementation. Weather labels and Not now
prominence also need final-build confirmation.

[PENDING-APPEAL: insert selected final screenshots and an agreed explanation of
typography, colour, spacing and map/content hierarchy. Do not invent aesthetic
preferences. PENDING-UI: supply final walkthrough, font/TalkBack and language results;
retain observed limitations rather than declaring full guideline compliance.]

### 5.4 Verification, runtime performance and dynamic response

Progress: `need support doc`. Approval: **NOT APPROVED**.

Verification combines pure-Java tests, instrumented presentation checks and manual
device observation. They answer different questions: deterministic fixtures can
exercise failure paths and rendering, while hardware and end-to-end behavior require
device evidence. The final submission's results are not yet established here.

| Evidence | Result and scope |
| --- | --- |
| Final submitted build | [PENDING-FINAL-TESTS: commit, environment, commands, executed/cached tasks, counts, failures/skips, Lint and artifacts] |
| Physical core walkthrough | [PENDING-PHONE: device/API, permissions, route/arrival, media persistence and recovery observations] |
| Runtime responsiveness | [PENDING-TIMINGS: defined actions, raw durations, network/cache conditions and whether controls remained responsive] |
| Dynamic response | [PENDING-REACTIVENESS: actual movement/heading and offline/reconnect behavior with timestamps] |

Historical evidence provides a baseline, not final acceptance. The earlier API 37
run recorded 75/75 instrumented tests after corrections [R5]. October 7 records
report 854 JVM tests and four planner presentation cases at each of two font scales
[R2], 854 JVM tests and four locale cases [R3], then 862 JVM tests and three exploration
cases at each scale [R4]. The latter runs used API 36; all three records report
successful builds and Lint with zero errors and 113 warnings. Counts overlap and
are not summed. Exploration fixtures were synthetic, and later home-location and
photo changes require evidence beyond those results.

A representative correction illustrates the validation process: denied planned-walk
starts previously led to an idle exploration/exit problem. The revised flow gates
navigation on session creation and keeps recovery on the planner; focused tests
cover denial, delayed/failed writes and no-session Back [R2]. Actual system-dialog
and final-phone acceptance remain separate. Full debugging history stays in the
source records rather than being reproduced here.

Responsiveness concerns delays while operating the app; Reactiveness concerns changes
driven by sensor or external input. Neither follows from build time or test count.
Historical manual feedback observed slow initial content loading and weather-refresh
concerns; it is not a controlled latency benchmark. Real sensing accuracy, final
camera/gallery round trips, TalkBack and broader device coverage remain unestablished
by the reviewed records [R4, R6]. Final limitations must reflect supplied evidence,
including failed and unexecuted cases, rather than assuming fixes passed.

### 5.5 Innovation and potential value

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep's proposed novelty is a specific integration of visitor decisions and
place interpretation. It connects autonomous selection and contextual planning with
content accessed after arrival and local records of exploration. This is not a claim
to invent itinerary planning, weather assistance or GPS stories: Wanderlog, komoot
and izi.TRAVEL already document related capabilities [R7–R9]. The comparison supports
a bounded account of the project's design, not market-wide uniqueness.

Arrival unlocks extend a familiar map interaction into discovery. In the three-stop
Parkville theme, a visit reveals a story and an optional question about visible
features such as a tower or arches. Retained station progress is intended to encourage
interest in collecting place experiences and discovering the next story. This frames
Surprise as a designed extension of navigation, not a measured emotional response;
no points, badges or leaderboard are claimed.

The same interaction connects computing with heritage interpretation and observation.
Location provides the participation condition while sourced architectural and botanical
content directs attention to the real setting [R10]. This illustrates cross-disciplinary
application; it does not establish an educational effect. The sensing, data and
persistence integration in Section 5.2 supplies the computing-knowledge basis.

Departure-window advice extends the intended decision sequence to when to leave.
Its concept compares upcoming weather over a chosen walking duration using precipitation
probability, apparent temperature and wind, with explanations for relative suitability.
Implementation and validation are pending; it is not a safety or comfort guarantee.
It remains part of the planned contribution, to be reconciled with final delivery.

The potential impact is to reduce the effort of organising an unfamiliar local outing
and encourage attention to places encountered. These are plausible aims, not measured
reductions in planning time or gains in engagement, satisfaction or learning. Saved
places and journey history support revisiting discoveries; content gaps, forecast
uncertainty and device limitations constrain the experience. Without user evaluation,
the report retains potential-value wording rather than claiming demonstrated benefit.

## 6. AI and other assistance acknowledgement

Progress: `need review`. Approval: **NOT APPROVED — USER REVIEW MANDATORY**.

[PENDING-AI-DISCLOSURE: Confirm and convert this inventory into final disclosure prose.
Each member must add their own tool use and actual review; no independent-authorship
or human-validation claim is inferred.]

| Recorded tool use | Assistance requiring acknowledgement |
| --- | --- |
| OpenAI Codex | Test investigation and related dependency/test/resource edits; guided manual checks and transcribed feedback; notes and issue drafts |
| OpenAI Codex, including requested research agents | Competitor/source research, selected code review, report drafts, editing and structural consolidation |
| Other members/tools | [Pending actual tools, uses, affected material and human review] |

The detailed assistance inventory remains in the preserved working draft. Omitting
a Co-authored-by commit trailer does not replace acknowledgement in the report.

## 7. References and attributions

Progress: `on editing`. Approval: **NOT APPROVED**.

Working references below support the consolidated text. [PENDING-REFERENCES: finalize
consistent citations, content/licence attributions and any additional sources after
scope and wording review. This is not a complete submission bibliography.]

- **R1:** [Inspected StoryStep source, ede3f7e](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/tree/ede3f7e340338bd7f10547bec19be471e95158b1).
- **R2:** [Planner regression record, 2026-10-07](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BUGFIX-PLANNER-2026-10-07.md).
- **R3:** [Locale regression record, 2026-10-07](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BUGFIX-DATE-LOCALE-2026-10-07.md).
- **R4:** [Exploring regression record, 2026-10-07](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-BIGFIX-EXPLORING-2026-10-07.md).
- **R5:** [API 37 validation record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/62a51f43b03adeda1a5ffc6d503504bee53dafcc/docs/verification/EV-API37-INSTRUMENTATION-2026-10-05.md).
- **R6:** [Historical manual-test notes](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/62a51f43b03adeda1a5ffc6d503504bee53dafcc/notes.md).
- **R7:** [Wanderlog itinerary planning](https://wanderlog.com/plan-a-trip).
- **R8:** [komoot route weather](https://www.komoot.com/premium/weather).
- **R9:** [izi.TRAVEL app features](https://www.izi.travel/en/app).
- **R10:** [Theme content and embedded university source links](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/app/src/main/java/com/comp90018/storystep/data/repository/ThemeCatalog.java).

<!-- Packaging check: required outer ZIP name, PDF, code ZIP and Git-log ZIP must be
verified separately. Do not present missing artifacts as delivered. -->
