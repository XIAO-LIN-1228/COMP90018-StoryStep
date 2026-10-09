# 分支：docs/project-report

19:45StoryStep — Consolidated Report

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

| Student name                            | Student number | University email | GitHub handle |
| --------------------------------------- | -------------- | ---------------- | ------------- |
| Lin Xiao | 1663572 | lxiao4914@student.unimelb.edu.au | [XIAO-LIN-1228](https://github.com/XIAO-LIN-1228) |
| [PENDING: remaining members] | | | |

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

| Demonstration                         | Timestamp       | Related report section |
| ------------------------------------- | --------------- | ---------------------- |
| Discovery, selection and planning     | [Pending]       | 5.2, 5.3               |
| Physical sensing, arrival and stories | [Pending]       | 5.2, 5.5               |
| External data and changing conditions | [Pending]       | 5.2, 5.4               |
| History, photos and recovery          | [Pending]       | 5.1, 5.3               |
| Departure advice, if delivered        | [Pending scope] | 5.2, 5.5               |

## 3. Compilation and operation

Progress: `need support doc`. Approval: **NOT APPROVED**.

### 3.1 Environment and prerequisites

Progress: `need review`. Approval: **NOT APPROVED**.

The following environment was inspected on 9 October 2026. The command-line build
in Section 3.2 used a fresh source export of `ede3f7e` [R1], the implementation
baseline discussed in this report.

| Component | Reference configuration |
| --- | --- |
| Host | macOS 27.2, Apple Silicon (arm64) |
| Installed IDE | Android Studio 2026.1, build AI-261.26222.65.2613.15948027 |
| Android SDK | Platform API 37 (installed as `android-37.0`, revision 2); Build-Tools 36.0.0; Platform-Tools 37.0.1 |
| Build system | Checked-in Gradle wrapper 9.5.0; Android Gradle Plugin 9.3.1 |
| Java | The verified wrapper launcher used OpenJDK 22.0.2; the checked-in daemon criteria select JDK 25. Android Studio bundles JBR 25.0.2. Application source/target compatibility is Java 11. |
| Application | Java/XML; package `com.comp90018.storystep`; minimum API 26; compile/target API 37 |
| Historical emulator | Pixel_8, API 36, used for the presentation checks in [R2, R4]; not rerun for this build check |

A phone running API 26 or later is required to run the app; GPS, accelerometer and
magnetometer are recommended for the complete sensing demonstration. Steps and
ascent additionally depend on a step counter and barometer. These optional metrics
may be unavailable on a particular device. An emulator can exercise screens and
injected locations, but does not establish physical sensor performance.

For setup, install the SDK Platform and Build-Tools above through Android Studio's
SDK Manager, together with Platform-Tools. Use the bundled JBR as the IDE's Gradle
JDK; the wrapper's daemon criteria select Java 25. The first build needs Internet
access to download Gradle, Maven dependencies and, if absent, the configured JDK.
Accept any SDK licence prompts. Use the checked-in wrapper rather than a separately
installed Gradle version.

Open the extracted code directory containing `settings.gradle.kts` and `gradlew`.
Let Android Studio create `local.properties` with the local SDK location, or set its
`sdk.dir` manually using this machine's SDK path. Do not copy another developer's
absolute path. No API key, account, Firebase setup or separately deployed backend
is required by this baseline; Room creates its local database on first launch.

At runtime, Internet access supplies Overpass attractions, Open-Meteo weather,
Wikimedia descriptions/photos, OpenStreetMap tiles and Valhalla walking routes.
Existing saved records and available caches support limited offline use; an empty
installation cannot provide fresh remote content offline. Provider failures may
produce unavailable content or a labelled fallback.

### 3.2 Build, install and first run

Progress: `need support doc`. Approval: **NOT APPROVED**.

**Build.** Finish Gradle Sync, then run from the extracted project root. On macOS
or Linux:

```bash
chmod +x gradlew
./gradlew assembleDebug --console=plain
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug --console=plain
```

The output is `app/build/outputs/apk/debug/app-debug.apk`. For additional verification,
run `./gradlew test lint --console=plain` on macOS/Linux or
`.\gradlew.bat test lint --console=plain` on Windows. These checks are separate from the compilation result below.

**Install and launch.** On the phone, enable Developer options and USB debugging,
connect a data-capable USB cable, and accept the computer's debugging authorisation.
Select the phone and the `app` run configuration in Android Studio, then click Run.
Alternatively, add the SDK's `platform-tools` directory to `PATH`, run the following
commands, and replace `DEVICE_SERIAL` with the identifier listed by `adb devices -l`:

```text
adb devices -l
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -W -n com.comp90018.storystep/.ui.MainActivity
```

The device must be listed as `device`, not `unauthorized` or `offline`. If USB
installation is blocked, check the phone's installation confirmation and developer
settings. An existing installation signed with another debug key cannot be updated
in place; use a separate test device or remove the old installation only after
preserving any needed records, since uninstalling deletes local app data.

**First run and permissions.** The following walkthrough is based on the inspected
implementation; physical-phone execution remains to be recorded.

1. Enable the phone's location service and Internet connection. Open StoryStep,
   select **Get started**, and continue through **Use my location** or **Not now**.
   The explanation screen itself does not grant Android location permission.
2. In Explore, use **Allow location** when offered. In the Android dialog, allow
   access while using the app and select precise location where that choice is
   available. Approximate access provides reduced discovery; precise route
   recording and automatic arrival need precise access. If access has been
   permanently denied, use **Open settings** to enable it, then return to the app.
3. Wait for a location fix and nearby content. Open a place, select **Add to walk**,
   review it in **Plan**, and select **Start walking**. If a permission or location
   check interrupts the start, complete it and retry. A missing fix remains a
   waiting state rather than a fabricated position.
4. Keep the app in the foreground during the sensing demonstration. Move outdoors
   to observe the recorded route and rotate the phone to observe heading. Reaching
   the selected destination must satisfy the arrival checks before a story is
   offered. Backgrounding pauses journey recording; use the offered resume action
   after returning.
5. Optionally use **Take photo** or **From library**, completing the external camera
   or picker interaction. Select **Stop exploration**, then **Finish journey** to
   save the walk, and open **Journeys** to inspect it. Saved places are accessible
   through Profile. Step counts additionally require physical-activity permission
   on API 29 or later; this baseline checks that permission but does not present a
   dedicated runtime request, so steps may remain unavailable.

For instrumented tests, connect one intended test device/emulator and run
`./gradlew connectedAndroidTest --console=plain` (Windows:
`.\gradlew.bat connectedAndroidTest --console=plain`).

**Verification performed on 9 October 2026.** A fresh `git archive` export of
`ede3f7e340338bd7f10547bec19be471e95158b1` was extracted into a new directory with no
project build outputs; only a local SDK-path file was added. `./gradlew --version`
confirmed Gradle 9.5.0, launcher JDK 22.0.2 and Java 25 daemon criteria.
`./gradlew assembleDebug --console=plain` returned **BUILD SUCCESSFUL**, with all
34 actionable tasks executed, and produced the debug APK. Inspection of the APK
confirmed the package, launcher activity and minimum/target API levels above.
Existing host dependency caches were reused; first-time dependency downloading on
a clean machine was not tested. Windows execution, tests and Lint were not rerun
in this check. The installed IDE version was inspected, but this was a terminal
build and does not supply the Android Studio Console screenshot required below.

[PENDING-INSTALL: No phone or emulator was connected during this verification.
Supply the physical phone model, Android/API version, installation/first-run result
and permission choices. Repeat the build and walkthrough against the actual final
code ZIP, whose submitted revision has not yet been fixed.]

### 3.3 Compilation evidence

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-COMPILE-IMAGE: Insert a legible Android Studio Console screenshot showing
successful compilation, captioned with date and submitted build identity.]

## 4. Individual contributions

Progress: `need support doc`. Approval: **NOT APPROVED**.

[PENDING-CONTRIBUTIONS: Each member supplies and confirms completed contributions and
supporting evidence. Keep the full breakdown to one rendered page. In-progress work
is not claimed complete, and commit counts do not determine contributions.]

| Member                                            | Completed contribution                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      | Evidence                                                                                                                                                                                                                                                                                                                                                | Individual/shared responsibility                                                                                                                                                                                                                                                   |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GitHub: [rHuan4422](https://github.com/rHuan4422) | **Contextual recommendations:** implemented deterministic scoring, recommendation reasons, Recommended/Nearby modes and updates from changing context. **Profile and reliability:** replaced fixed statistics, removed the unfinished Memory editor and fixed saved-context recovery. **Theme exploration:** implemented the three-stop workflow, arrival-unlocked stories/questions, skip/undo, persistent progress, recovery and complete/partial results. **Verification:** added regression tests and documented JVM, emulator, build and Lint results. | **Recommendations/Profile:** [PR #20](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/20); commits [eed454c](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/eed454c4ecea010a6b40a4e0775e85fcd6b0c852), [816fb16](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/816fb1624bf3dcf12bdbc0657a07f8d8c242aa98), [a4b6256](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/a4b62563c28c8b7ace7cf4d5bb5e934a82c803ed), [9cfbe2f](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/9cfbe2fc709717945509cd4d9c1927d6330225e4); [review and test evidence](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/9cfbe2fc709717945509cd4d9c1927d6330225e4/docs/verification/EV-TEAM-A-REVIEW-2026-09-20.md). **Theme exploration:** [PR #24](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/24); commit [c212cfb](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/c212cfb8806df26847445bb390b6ae7c98801791); [implementation plan](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/c212cfb8806df26847445bb390b6ae7c98801791/docs/theme-exploration-plan.md) and [verification evidence](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/c212cfb8806df26847445bb390b6ae7c98801791/docs/verification/EV-THEME-EXPLORATION-2026-09-21.md). | Responsible for the listed implementation, fixes, tests and documentation, with Codex assistance. Integrated with shared discovery, sensing, session and history infrastructure. Full-suite test totals include teammates' tests; physical-device field acceptance is not claimed. |
| Lin Xiao (GitHub: [XIAO-LIN-1228](https://github.com/XIAO-LIN-1228)) | **Foundation and sensing:** established the layered Java/XML architecture, foreground location and permission handling, session lifecycle, route-distance recording, movement classification and compass guidance. **Persistence and stories:** implemented Room-backed session recovery, quality-aware arrival and stored story snapshots, saved attractions and journey history. **Remote data:** integrated Open-Meteo weather and Overpass attraction discovery with area caching, freshness checks and stale fallback. **Refinement:** added saved-detail fallback, cache retention and accessibility improvements; repaired planner start recovery, date localisation, exploration map/photo controls, compass presentation and attraction image loading. **Verification:** added domain, repository and UI regression tests and recorded build, Lint and emulator evidence. | Foundation/location/session/sensing: [#2](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/2), [#3](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/3), [#4](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/4), [#6](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/6), [#7](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/7). Persistence/arrival/saves/history: [#8](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/8), [#9](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/9), [#10](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/10), [#12](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/12). Weather/attractions: [#11](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/11), [#13](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/13). Refinements: [#21](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/21), [#35](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/35), [#36](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/36), [#37](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/37), [#38](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/38). | Responsible for the listed implementations, tests and evidence within the shared application. Later UI, recommendation, theme and reliability work includes teammates' contributions. Test-suite totals are shared project results; physical-device acceptance is not claimed. |

## 5. System design and evaluation

Progress: `on editing`. Approval: **NOT APPROVED**.

StoryStep connects information about nearby places with the experience of visiting
them. Its architecture separates external data and persistent records from the
sensing and decision rules that drive exploration. The following sections explain
these mechanisms, the user journey they support, and the evidence available for
assessing their behavior. The implementation reviewed is main at `ede3f7e` [R1];
test results apply to the individual revisions identified below.

[PENDING-BASELINE: reconcile implementation and verification with the submitted build.]

### 5.1 System architecture and data handling

Progress: `need support doc`. Approval: **NOT APPROVED**.

The application separates presentation, application coordination, domain calculations,
repositories and platform adapters. Plain-Java classes isolate computational rules
from Android lifecycle code, while adapters such as AndroidSensorHub manage platform
subscriptions. This allows deterministic logic tests without treating simulated
inputs as evidence of physical sensor behavior.

Repositories combine remote information with local storage. Overpass supplies nearby
places, Wikimedia sources enrich descriptions and images, Open-Meteo supplies current
weather, and Valhalla supplies pedestrian routes. Map tiles provide the geographic
display. Requests for the same area can be coalesced, and cached observations are
classified by freshness before being returned or used as labelled fallback data.
Content gaps and failed requests remain possible; caching does not provide complete
offline navigation. Route requests contain the supplied route coordinates, even
though weather and place queries use area-based locations.

Persistent state provides continuity between actions and their visible results.
An arrival is stored before its story becomes available. PlanStartViewModel retains
pending start state across recreation and signals readiness after a session exists.
Room schema version 12 exports schemas and registers explicit migrations to preserve
stored data rather than relying on destructive migration. Saved places and completed
journeys persist; the unstarted walking plan remains in memory.

[PENDING-IMPLEMENTATION-REVIEW: confirm final configuration, migration evidence and
remaining Lint warnings. Review also found a stale version-10 database comment above
its version-12 annotation; reconcile documentation with delivered code.]

### 5.2 Exploration and departure decisions

Progress: `need support doc`. Approval: **NOT APPROVED**.

Users choose destinations, while the app supplies context for comparing them.
Recommendation ranking combines distance, weather, time, saved and unvisited factors
with deterministic explanations. Unsuitable weather data is neutral. This fixed-rule
policy does not learn an interest profile. Selected stops are ordered using a
nearest-neighbour heuristic, after which Valhalla provides a street walking path;
the ordering does not guarantee a globally shortest itinerary.

During exploration, location supports recorded movement and arrival decisions.
The inspected arrival policy requires distance plus reported accuracy within 50 metres,
accuracy within 25 metres, and at least three accepted fixes spanning ten seconds.
Fifteen-second gap and sample-age limits prevent old or disconnected samples from
being treated as a continuous stay. These parameters are starting values whose
physical performance still requires field evidence.

Movement estimation removes a time-dependent gravity estimate and measures variation
in acceleration magnitude over two-second windows. Separate thresholds and agreement
across two windows reduce rapid changes of state near the decision boundary. Heading
processing combines orientation readings with true-heading conversion and smooths
sine/cosine components, avoiding the discontinuity between 359 and 1 degrees. Optional
step and pressure adapters provide walk metrics when available; absent capabilities
are shown as unavailable. These inputs serve different purposes rather than all
jointly determining arrival. Foreground home discovery also acquires location
separately from journey recording.

Departure-window advice is intended to extend destination and ordering decisions to
when to leave. Its design compares precipitation probability, apparent temperature
and wind over a chosen walking duration, then explains the relative suitability of
upcoming departure windows. This remains a planned extension, not verified delivery
or a guarantee of comfort or safety.

[PENDING-DEPARTURE-IMPLEMENTATION: add the delivered algorithm/API, duration aggregation,
timezone and missing-data handling, and boundary-test results once available.]

### 5.3 User experience design

Progress: `need support doc`. Approval: **NOT APPROVED**.

Explore, Plan, Journeys and Profile organise the main user journey. Visitors move
from nearby discovery to attraction details and planning, then to exploration and
stored history. Saved places and settings are reachable through Profile. Arrival
prompts offer View story or Later, and questions in the curated theme are optional,
allowing users to decide how much interpretation to engage with during a walk.

The planner keeps Add and Start outside its sliding content so users do not need to
expand the sheet to find the main actions. It checks location access and waits for
session creation before navigating. Cancellation preserves planning, and Back from
a no-session exploration state does not offer to discard a nonexistent journey [R2].
These choices connect navigation to the underlying state and provide a route back
when an action cannot be completed.

MainActivity applies system-bar insets and delegates tab destination handling to
NavigationUI. Shared resources define 48dp touch targets; selected controls expand
with text and status updates use live regions. The recorded planner checks still
show bottom-tab clipping at 200% font scale, and automated checks do not establish
spoken-label quality or TalkBack reading order [R2].

Interface text distinguishes missing measurements and recoverable actions, including
waiting for a location fix and retrying an arrival save. A fallback route is labelled
as a straight-line plan. English date formatting was corrected for non-English device
settings [R3]. Permission wording needs to match both home discovery and journey
recording: “during an active exploration” may suggest a narrower lifecycle. Weather
labels and the prominence of Not now also need final-build confirmation.

[PENDING-VISUAL-DESIGN: add selected final screenshots and an agreed account of visual
hierarchy, typography, colour and spacing. PENDING-UI: supply the final walkthrough,
large-font, TalkBack and language observations; retain unresolved limitations.]

### 5.4 Verification results and limitations

Progress: `need support doc`. Approval: **NOT APPROVED**.

Verification combines pure-Java tests, instrumented presentation checks and manual
device observation. Deterministic fixtures exercise rules, failure paths and rendering;
physical sensing and complete user journeys need device evidence. The final submitted
build's results remain to be supplied.

| Evidence                         | Result and scope                                                                                                        |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| Submitted build                  | [PENDING-FINAL-TESTS: commit, environment, commands, executed/cached tasks, counts, failures/skips, Lint and artifacts] |
| Physical walkthrough             | [PENDING-PHONE: device/API, permissions, route/arrival, media persistence and recovery observations]                    |
| Loading and interaction timing   | [PENDING-TIMINGS: defined actions, raw durations, network/cache conditions and whether controls remained usable]        |
| Responses to changing conditions | [PENDING-DYNAMIC-BEHAVIOR: movement/heading and offline/reconnect observations with timestamps]                         |

Historical records establish a baseline. An earlier API 37 run recorded 75/75
instrumented tests after corrections [R5]. October 7 records report 854 JVM tests
and four planner presentation cases at each of two font scales [R2], 854 JVM tests
and four locale cases [R3], then 862 JVM tests and three exploration cases at each
scale [R4]. The latter device runs used API 36 and reported successful builds with
zero Lint errors and 113 warnings. Counts overlap and are not summed. Exploration
inputs were synthetic; subsequent home-location and photo changes require evidence
beyond these historical results.

One repair illustrates how tests informed implementation. Denied planned-walk starts
had led to an idle exploration/exit problem. The revised flow gates navigation on
session creation and retains recovery on the planner. Focused tests cover denial,
delayed or failed writes and no-session Back [R2]; real system-dialog and final-phone
acceptance remain separate. The detailed failure history is retained in that record.

Two questions remain distinct: how long an action takes, and whether the interface
updates correctly when position, heading or network conditions change. Build times
and test totals answer neither. Historical manual feedback identified slow initial
content loading and weather-refresh concerns, but did not constitute a controlled
latency benchmark. Real sensing accuracy, final camera/gallery round trips, TalkBack
and broader device coverage remain unestablished by the reviewed records [R4, R6].
These gaps must be resolved or retained as limitations of the submitted evaluation.

### 5.5 Design contribution and application value

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep is designed around the transition from deciding where to go to understanding
what is encountered there. It connects autonomous place selection and contextual
planning with arrival-linked interpretation and local exploration records. Related
products already offer itinerary planning, route weather or GPS stories, including
Wanderlog, komoot and izi.TRAVEL [R7–R9]. The contribution is this particular visitor
experience, rather than a claim to have invented those individual capabilities.

In the three-stop Parkville theme, arrival reveals a story and an optional question
about visible features such as a tower or arches. Progress accumulates as places are
visited, inviting users to discover another story and build a collection of place
experiences. This extends a map destination into an observation activity without
requiring points, badges or a leaderboard. Architectural and botanical content links
the digital interaction to the physical setting and its history [R10]. Whether this
actually increases motivation or learning has not been measured.

The departure-advice concept would complement that experience by helping users choose
when to begin, as described in Section 5.2. Together, the intended benefits are less
effort in organising an unfamiliar outing and greater attention to places encountered.
Saved places and journey history also support returning to earlier discoveries.
Content coverage, forecast uncertainty and device capabilities constrain those benefits.
Without user evaluation, they remain potential value rather than demonstrated gains
in planning efficiency, satisfaction or understanding.

## 6. AI and other assistance acknowledgement

Progress: `need review`. Approval: **NOT APPROVED — USER REVIEW MANDATORY**.

[PENDING-AI-DISCLOSURE: Confirm and convert this inventory into final disclosure prose.
Each member must add their own tool use and actual review; no independent-authorship
or human-validation claim is inferred.]

| Recorded tool use                                 | Assistance requiring acknowledgement                                                                                                 |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| OpenAI Codex                                      | Test investigation and related dependency/test/resource edits; guided manual checks and transcribed feedback; notes and issue drafts |
| OpenAI Codex, including requested research agents | Competitor/source research, selected code review, report drafts, editing and structural consolidation                                |
| Other members/tools                               | [Pending actual tools, uses, affected material and human review]                                                                     |

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
