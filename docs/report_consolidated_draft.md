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
| Penghua He | 1637978 | penghuah@student.unimelb.edu.au | [Penghua-He](https://github.com/Penghua-He) |
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
| Supplementary Windows setup | Windows PowerShell; IntelliJ IDEA 2024.3.2.2; Eclipse Temurin JDK 25.0.4; local Android SDK; OPPO X9 Pro physical phone |

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

Penghua He also verified the project on Windows. IntelliJ IDEA 2024.3.2.2 was
configured with Eclipse Temurin JDK 25.0.4, and the checked-in wrapper was run from
the repository root on 7 October 2026:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain
```

The run returned **BUILD SUCCESSFUL**. Of 48 actionable tasks, 16 were executed and
32 were up-to-date; the generated unit-test reports contained 858 tests with no
failures. The debug APK was installed on an OPPO X9 Pro through USB debugging and
launched successfully. The installed application continued to run independently
after the USB cable was disconnected.

The fresh macOS archive build above was command-line only; the installation and
physical-phone evidence comes from the separate Windows and OPPO run.

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
| Penghua He (GitHub: [Penghua-He](https://github.com/Penghua-He)) | **Reliability and data handling:** implemented bounded retry, backoff and rate-limit handling for attraction and weather requests, journey-photo cleanup and backup exclusions. **Stories and feedback:** added narration with pause/continue, speech-rate controls, readable fallback and one-shot feedback after persisted arrivals. **Recommendations and coverage:** added conservative opening-status evaluation, bounded popularity weighting, additional attraction types, trusted fallback names and source-identity deduplication. **Location refinement:** separated home discovery from journey recording and prevented reference coordinates from appearing as the user's location. **Verification:** added regression tests and verification records for these changes. | [PR #19](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/19), [PR #26](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/26), [PR #27](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/27), [PR #28](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/28), [PR #29](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/pull/29); commits [b6bed78](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/b6bed7893d18c679227921f57a8680a1550640f9), [2a56a3e](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/2a56a3e), [6eb8b89](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/6eb8b89), [ce81182](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/ce81182), [a59f6b0](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/a59f6b0), [ebebeaf](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/commit/ebebeaf53e9ec704f75a4873dfd2c0770c162ecd). | Individually responsible for the listed incremental features, fixes and related tests, integrated with the team's existing data, recommendation, exploration and persistence infrastructure. |

## 5. System design and evaluation

Progress: `need review`. Approval: **NOT APPROVED**.

StoryStep must keep a walk consistent when readings are noisy, requests finish
late or the app is interrupted. This section explains the design and its evidence.
Source references use `ede3f7e` [R1]; tests apply to the revisions in Section 5.4.
The final submission revision and ten-page PDF limit still need checking.

**Rubric coverage.** This table locates the argument for each criterion. It also
identifies required material that is still outstanding; it does not assign marks.

| Rubric criterion | Report location | Argument or evidence |
| --- | --- | --- |
| Material - Report & Video | Sections 2 and 5 | Design and evaluation below; video link and timestamps pending |
| Material - Screenshot | Section 3.3 | Android Studio Console screenshot pending |
| Material - Commit log | Submission package | Commit-log ZIP and its coverage of the submitted revision still to be verified |
| Material - Itemised contributions | Section 4 | Member contributions and PR evidence; remaining members and one-page layout pending |
| Implementation - Quality | 5.1, 5.4 | Separation of responsibilities, explicit storage contracts and regression cases |
| Implementation - Sensors | 5.2, 5.4 | Location, movement and heading processing, with distinct validity checks |
| Implementation - Connectivity | 5.1 | External data changes discovery, weather context and route presentation; caches handle failures |
| Implementation - Responsiveness | 5.3, 5.4 | Background work and controlled redraws; measured interaction timings pending |
| Implementation - Technical depth | 5.1, 5.2, 5.4 | Recovery, repeat-safe writes, noisy sensing and out-of-order responses |
| User Interface - Appeal | 5.3 | Shared colour and type resources, map hierarchy and route styles; final screenshots pending |
| User Interface - Guidelines | 5.3, 5.4 | Android navigation, insets, scalable controls and accessibility behaviour; remaining checks stated |
| User Interface - Flow | 5.3 | Discovery-to-history path and recovery from interrupted starts |
| User Interface - Language | 5.3 | Action labels, unavailable states and qualified fallback information |
| User Interface - Reactiveness | 5.2, 5.3 | Changes to position, heading and stored state update the display; final demonstration pending |
| Innovation - Novelty | 5.5 | A visitor workflow combining contextual choice, arrival-linked interpretation and local records |
| Innovation - Surprise | 5.5 | An arrival opens an observation activity as well as a destination record |
| Innovation - Tech Knowledge | 5.5 | Integration of sensing, state, storage, network and interface concepts |
| Innovation - Cross-Disciplinary | 5.5 | Architectural and botanical observation applied through location-specific questions |
| Innovation - Impact | 5.5 | Potential benefits for unfamiliar visitors, with limits on measured outcomes |

### 5.1 System architecture and data handling

Fragments display ViewModel state. `ExplorationCoordinator` manages sessions and
sensing; domain classes calculate results; repositories load and persist them.
`AppContainer` wires the components. Android calls stay in platform adapters, while
distance, arrival, movement and ranking remain plain Java. Tests can therefore
supply clocks, readings and failed stores without running a screen [R1].

Starting a walk changes storage and navigation together. The planner waits for
session creation before navigating; failed writes remain retryable, duplicate taps
are ignored, and ViewModel state retains the pending result across recreation [R2].
Central coordination keeps those transitions consistent across screens.

Room schema version 12 uses exported schemas and explicit migrations to preserve
records across upgrades. Sequence numbers prevent retried route writes from
duplicating points; visits are unique per session and attraction. A story is offered
after its visit is saved, with failed writes reported for retry. Its snapshot keeps
the content recorded on arrival even if provider data changes. Repository and
coordinator tests cover these contracts; fake DAOs do not prove SQLite behaviour.

Recovery restores progress as paused, without treating the old fix as live or
joining the missing interval into the trail. Saved places and finished journeys
persist, but an unstarted ordered plan remains in memory. The [persistence record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-REQ14-REQ15-2026-09-04-01.md) documents the initial implementation and tests.

**External data and failure handling.** Network access supplies the places,
weather and route geometry used during a walk.

| Source | Use in StoryStep | Behaviour when data is unavailable |
| --- | --- | --- |
| OpenStreetMap Overpass | Nearby attraction records | Use an eligible cached area with a stale label, or report unavailable content |
| Wikidata, Wikipedia and Wikimedia Commons | Descriptions, facts and images | Retain available place information; use artwork when a photo cannot load |
| Open-Meteo | Weather display and recommendation context | Label eligible cached weather; unsuitable weather is neutral in ranking |
| Valhalla | Walking geometry between selected stops | Show a labelled straight-line plan, distinct from the recorded trail |
| OpenStreetMap tile service | Geographic map display | Tile coverage depends on available downloads/cache; this is not complete offline navigation |

Requests for the same area share one pending fetch. I/O runs on a background
executor and results return on the UI executor. Attractions refresh after 24 hours
and expire after 30 days; weather refreshes after 30 minutes and expires after six
hours, measured from fetch time. A failed refresh can return eligible cached data
with a stale label. Bounded retries distinguish transient failures, rate limits and
invalid responses, limiting repeated work [R1].

Saved snapshots and journey records are separate from provider caches. The
implemented 30-day cleanup removes eligible provider entries without erasing those
user records; the policy note still requires team approval of the window. Backup
exclusions are configured but lack device verification. Weather and attraction
queries use area-based locations; routing sends route coordinates, so local storage
does not imply that all location-related data stays on the phone.

### 5.2 Sensing, arrival and destination decisions

Arrival uses location quality and dwell time. Movement and heading provide
separate context and guidance.

| Input | Processing and purpose | Invalid or missing input |
| --- | --- | --- |
| Location | Distance/bearing, route accumulation and sustained arrival checks | Reject unsuitable fixes; reduced discovery with approximate permission; no automatic arrival without precise access |
| Accelerometer | Remove a gravity estimate and measure variation over two-second windows | Report unavailable movement when readings cannot support an estimate |
| Accelerometer and magnetometer | Orientation, magnetic-to-true-heading conversion and circular smoothing | Fall back to available distance/cardinal guidance when heading is missing, stale or unreliable |
| Optional step counter and barometer | Steps and metres climbed during a walk | Omit the unavailable measurement; steps also depend on permission |

The route filter checks accuracy, age, displacement and implied speed to keep
stationary drift from adding travel. Arrival needs those stationary readings to
establish a stay. It therefore evaluates fixes separately, rather than receiving
only the samples accepted as movement.

Arrival requires distance plus reported accuracy within 50 metres, accuracy within
25 metres, and three qualifying fixes spanning ten seconds. Fifteen-second age and
gap limits reject stale or interrupted stays. `standingStillAtTheDestinationStillArrives`
checks the separation from route filtering. Passing through, changed targets,
repeat arrivals and failed writes have regression cases. These starting thresholds
have not been validated as a physical accuracy guarantee
([arrival verification](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-REQ12-REQ13-2026-09-05-01.md)).

Movement classification uses 0.35 and 0.7 m/s² thresholds and two agreeing windows
to change an established state, reducing switching near a boundary. Heading uses
sine/cosine smoothing so 359° and 1° average near north. Declination converts magnetic
to true heading; readings expire after five seconds. Controlled fixtures exercise
these rules, while device-specific behaviour still needs field measurements.

Journey collection pauses in the background. Home discovery separately obtains
location while visible and authorised, so browsing does not create a journey.

Recommendations combine distance, weather, time, saved status and prior visits,
with opening-status and bounded popularity adjustments. Unsuitable weather is
neutral; reason codes explain the fixed-rule ranking. Users choose the stops.
Nearest-neighbour ordering and a requested street route support planning without
guaranteeing a globally shortest itinerary [R1].

Departure-window advice remains outside the verified baseline discussed here.
If included in the submitted build, its forecast source, duration aggregation,
timezone handling and tests must be added before it is counted as delivered.

### 5.3 User experience and interface behaviour

Explore, attraction details, Plan, exploration and Journeys follow the sequence of
choosing, walking and reviewing. Profile holds saved places and settings. View story
or Later and optional theme questions leave the visitor control over interruptions.

**Flow and recovery.** Add a place and Start walking remain outside the sliding
sheet, visible without expansion. Permission refusal preserves the plan; permanent
denial offers app settings. Navigation waits for a saved session. With no session,
Back returns without asking to discard a nonexistent journey. These controls follow
the actual state, including when a start fails [R2].

**Appearance and hierarchy.** Shared resources use warm light walking surfaces,
dark journey surfaces and Bricolage heading/body/label styles. Amber marks unread
stories; category colours distinguish places. Final screenshots should show these
choices. Outdoor legibility and appeal have not been assessed through user studies.

Exploration reserves map space and keeps Stop reachable. Take photo and From
library are separate actions. Dashed planned geometry, a solid recorded trail and
numbered stops distinguish intention from travel. Recenter returns to the accepted
position; a straight-line fallback is labelled as an estimate [R4].

**Android conventions and language.** MainActivity uses system-bar insets and
NavigationUI. Controls use 48dp touch-target resources, scalable text and flexible
heights. Status text has live regions; compass announcements are limited to cardinal
changes. Selected API 36 layouts passed normal/200% font checks, but bottom-tab
clipping remains and TalkBack reading order is unverified [R2, R4].

Labels distinguish waiting for GPS, unavailable heading and failed arrival saves.
The user is told when a route or cached result is a fallback. English journey-date
formatting is explicit even under non-English device settings [R3]. One remaining
wording issue is that the onboarding explanation says location is used during an
active exploration, although foreground home discovery also obtains location.
That explanation needs to cover both uses.

**Responsiveness and reactiveness.** Route requests run off the UI thread, wait
for an accepted origin and do not repeat for every GPS update. Results from an old
request cannot replace an edited plan or publish after its ViewModel is cleared.
Heading and timer updates do not rebuild every route overlay [R4]. These choices
avoid unnecessary work; they are not substitutes for measured loading times.

Reactiveness concerns the meaning of the update: a new accepted fix extends the
trail, a changed heading rotates the compass, an expired heading becomes
unavailable, and a saved arrival offers a story. Context changes can alter the
recommendation order. Existing tests exercise these state transitions and selected
rendering paths. The final video should show the corresponding physical input and
visible response, including an offline/reconnect case.

### 5.4 Verification results and limitations

JVM tests isolate calculations and failures; instrumented tests exercise Android
storage and screens; manual checks observe a particular phone setup. Builds do not
establish sensor accuracy; synthetic UI fixtures do not exercise real camera or
gallery interactions.

Penghua He's completed feature-branch verification records are retained below.
Each row describes a separate revision; the test counts must not be added together.

| Date / revision | Command | Environment | Actual result | Evidence |
| --- | --- | --- | --- | --- |
| 27 September 2026 / `feature/voice-story` | `./gradlew test assembleDebug lint` | Gradle JVM/build/Lint | 827 JVM tests; 0 failures, errors or skips; Lint 0 errors and 112 warnings; build successful | [Voice-story record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-VOICE-STORY-2026-09-27.md) |
| 27 September 2026 / `feature/opening-status` | `./gradlew test assembleDebug assembleDebugAndroidTest lint` | Gradle JVM/build/Lint | 838 JVM tests; 0 failures, errors or skips; Lint 0 errors and 112 warnings; debug and Android-test APKs built | [Opening-status record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-OPENING-STATUS-2026-09-27.md) |
| 27 September 2026 / `feature/popularity-ranking` | `./gradlew test assembleDebug assembleDebugAndroidTest lint` | Gradle JVM/build/Lint | 841 JVM tests; 0 failures, errors or skips; Lint 0 errors and 112 warnings; debug and Android-test APKs built | [Popularity-ranking record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-POPULARITY-RANKING-2026-09-27.md) |
| 27 September 2026 / `feature/attraction-coverage` | `./gradlew test assembleDebug assembleDebugAndroidTest lint` | Gradle JVM/build/Lint | 846 JVM tests; 0 failures, errors or skips; Lint 0 errors and 112 warnings; debug and Android-test APKs built | [Attraction-coverage record](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/blob/ede3f7e340338bd7f10547bec19be471e95158b1/docs/verification/EV-ATTRACTION-COVERAGE-2026-09-27.md) |
| 7 October 2026 / `fix/home-location`, committed as `ebebeaf` | `.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --console=plain` | Windows, Temurin JDK 25.0.4 | 858 JVM tests with 0 failures; build and Lint successful; 16 tasks executed and 32 up-to-date | Generated reports at `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html` |

An earlier API 37 record reports 75/75 instrumented tests after corrections [R5].
The 7 October planner run reports 854 JVM tests and four presentation cases at each
of normal and 200% font scale [R2]. The locale run reports 854 JVM tests and four
API 36 cases [R3]. The exploration run reports 862 JVM tests and three presentation
cases at each font scale, also on API 36 [R4]. Those October records report
successful builds, zero Lint errors and 113 warnings. Their inputs and revision
limits remain relevant; these are not results for an unspecified final package.

Section 3.2 records a fresh macOS build of `ede3f7e` and Penghua He's Windows/OPPO
X9 Pro installation. He reports launch, onboarding display, operation after USB
disconnection and resolution of encountered problems. The phone's Android/API
version, individual fixes and retest cases are not yet listed. Installation evidence
is present; a complete final-build walkthrough remains outstanding.

**What the regression cases establish.** The following cases connect implementation
choices to specific failure modes rather than relying only on suite totals.

| Case | Behaviour checked | Evidence and limit |
| --- | --- | --- |
| Denied or delayed walk start | No premature navigation; failed writes can be retried; duplicate starts are ignored | Planner JVM and API 36 presentation checks [R2]; real system-dialog behaviour not rerun |
| Stationary arrival and write failure | Staying still can qualify; failed storage is not announced as a saved visit; repeated arrivals do not create another visit | Arrival coordinator tests and the historical arrival record linked in 5.2; physical thresholds unvalidated |
| Repeated route checkpoint and recovery | Re-offered points do not duplicate the trace; restored progress carries no live last fix | `RoomSessionStoreTest` [R1]; fake DAOs do not test SQLite itself |
| Edited plan or offline routing | An old response cannot replace a new plan; failed routing retains a labelled estimate separate from travel | `ExplorationPlannedRouteTest` and [R4]; no live-service latency measurement |
| Stale heading and layout changes | Heading expiry, map update cadence and selected controls at normal/large font sizes | JVM and synthetic API 36 checks [R4]; no physical compass or camera validation |

For example, denied starts previously reached an idle exploration page. Gating
navigation on permission and session creation fixed the flow; tests for denial,
delayed writes, retries and no-session Back now guard that repair [R2].

The remaining evaluation work is specific:

| Outstanding evidence | What must be recorded |
| --- | --- |
| Final submitted build | Commit, environment, commands, executed/cached tasks, failures/skips, Lint findings and accessible reports |
| Physical walkthrough | Phone/API, permission choices, route/arrival, photo persistence and recovery results for the installed build |
| Responsiveness | Defined loading actions, measured durations, network/cache conditions and whether controls remain usable |
| Reactiveness and accessibility | Input-to-display changes, offline/reconnect behaviour, final large-font checks and TalkBack observations |

Historical feedback mentions slow initial loading and weather-refresh concerns
[R6], but gives no controlled latency benchmark. No claim of measured speed,
physical sensing accuracy or improved user satisfaction is made here. The final
report should replace these evidence requests with results or retain the unresolved
items as limitations.

### 5.5 Design contribution and application value

**Novelty and surprise.** StoryStep combines contextual choice, an explicit walk,
arrival-linked interpretation and local records. Itinerary planning in Wanderlog
[R7], route weather in komoot [R8], and GPS stories/quizzes in izi.TRAVEL [R9] show
that the components have precedents. The contribution is the implemented visitor
workflow, with no claim to have invented the individual features.

At Old Quad, arrival reveals a story and a question about visible arches; other
stops focus on towers and the System Garden [R10]. Reaching a map point starts an
observation activity. That transition is the intended surprise, with answering
optional and no leaderboard required.

**Computing knowledge.** Geometry, signal processing, state transitions, database
consistency and asynchronous requests work together in the same walk. Their
boundaries create technical depth: an unread story must survive interruption after
its visit is saved, while an old route response must not replace a new plan.
Sections 5.1–5.4 explain how implementation and tests address both.

**Cross-disciplinary application.** Architectural and botanical observation shape
the theme's questions. Prompts refer to visible features at each stop, with university
sources attached to the narratives [R10]. Location makes the prompt relevant to
the surroundings; a learning benefit has not been measured.

**Potential impact.** Combining discovery and interpretation may help unfamiliar
visitors organise an outing and notice places they would otherwise pass. Saved
places and journey photographs support returning to those discoveries. Coverage,
location quality and connectivity limit usefulness; no comparative study has
measured planning-time or satisfaction gains. Departure advice remains future work
for this baseline and is not counted as a delivered benefit.

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
