# StoryStep: factual report working draft

Revision: 2026-10-07. Status: DRAFT — NOT APPROVED.

This is an AI-assisted working document, separate from final_report_template.md.
Every section and subsection below is unapproved. No section may be labelled final
without the user's explicit approval of that section. Approval of one section does
not approve others; revised text must be reviewed again. No approval is recorded.

This first revision drafts only verifiable configuration, procedures and dated
results. Evaluative sections contain questions only. Contributions are left blank
for members to complete. Physical-device, manual and user testing are now assigned
to other members; historical records remain in notes.md, but are not developed into
report claims here. The user's new independent feature is in progress and must not
be represented as completed or attributed in the contribution section yet.

## Section review register — NOT APPROVED

| Template section | Current treatment | Approval |
|---|---|---|
| 1 Overview | Target users/problem/intended experience discussed; draft below | Not approved |
| 2 Video | Placeholder for supplied artifact | Not approved |
| 3.1 Environment | Configuration facts and dated reference environment | Not approved |
| 3.2 Build | Reproducible commands and verification limits | Not approved |
| 3.3 Phone run | Reserved for responsible members | Not approved |
| 3.4 Screenshot | Placeholder for actual IDE evidence | Not approved |
| 4 Contributions | Blank; members fill their own work | Not approved |
| 5 Rubric justification | Automated facts only; evaluations deferred | Not approved |
| 6 AI disclosure | Factual inventory; mandatory user review | Not approved |
| 7 References | Evidence pointers, not a completed bibliography | Not approved |

## 1. Target users, problem and intended experience — DRAFT, NOT APPROVED

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

## 3.1 Configuration and reference environment — DRAFT, NOT APPROVED

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

## 3.2 Build and automated verification procedure — DRAFT, NOT APPROVED

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

## 3.3 Physical-device run — RESERVED, NOT APPROVED

[Responsible members to supply actual phone setup, permissions, sensors, run steps
and results. No phone acceptance text drafted in this revision.]

## 3.4 Compilation screenshot — PLACEHOLDER, NOT APPROVED

[Insert the actual Android Studio Console screenshot with task, date, commit and
observed result. A terminal success line is not the required IDE screenshot.]

## Automated evidence supporting Section 5 — DRAFT, NOT APPROVED

### Execution results — DRAFT, NOT APPROVED

The reference run recorded in EV-API37-INSTRUMENTATION-2026-10-05.md and notes.md
has the following outcomes. These are historical observations, not claims about
an untested final submission commit.

| Check | Recorded outcome | Evidence boundary |
|---|---|---|
| Earlier test assembleDebug lint | Successful; 846 JVM tests, 0 failures/errors/skips; Lint 0 errors / 112 warnings | Original reference-machine run |
| Initial connectedAndroidTest supplied by user | 75 tests, 7 failures, 0 skips | Failure log supplied in conversation |
| Run after dependency/test correction | 73 of 75 passed | Two accessibility target-size failures remained |
| Final combined test assembleDebug lint connectedAndroidTest | Successful; 75/75 device tests, no skips; Debug assembly passed; Lint 0 errors / 107 warnings | API 37 emulator; JVM task UP-TO-DATE, reusing earlier 846-test result |

### Diagnosed failures and changes — DRAFT, NOT APPROVED

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

### 5.9 Responsiveness: evidence boundary — DRAFT, NOT APPROVED

No automated runtime-latency benchmark is established by the cited runs. Build time,
test-suite duration, a passing functional test and a Lint result are not measures of
screen-render latency, frame pacing, network wait or sustained-session resource use.
This revision therefore makes no positive runtime-responsiveness claim. Responsible
members must supply relevant performance evidence for this required criterion.

### 5.15 Reactiveness: evidence boundary — DRAFT, NOT APPROVED

Sensor/external-data reactiveness is distinct from runtime latency. General test
counts do not prove the required dynamic UI behavior. Specific automated scenarios,
inputs, assertions and results must be identified before drafting a supported claim.
Manual/physical-device evidence is reserved for the responsible members. No such
claim is drafted in this revision.

### 5.16 Innovation - Novelty — DRAFT, NOT APPROVED

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

## 4. Individual contributions — BLANK, NOT APPROVED

[Each member fills and confirms their own completed contributions and evidence.
Keep the whole-team breakdown to one page. No entries are preassigned. The user's
independent feature remains in progress and is not listed as completed.]

| Student | Completed contribution | Evidence | Individual/shared responsibility |
|---|---|---|---|
| | | | |

## 6. AI assistance inventory — DRAFT; USER REVIEW MANDATORY; NOT APPROVED

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

No model version, reviewer signoff or validation activity may be invented. A missing
Co-authored-by trailer does not replace the report's required AI disclosure.

## Evaluative sections — DISCUSSION ONLY, NOT APPROVED

Overview, Novelty, Surprise and Tech Knowledge have unapproved drafts above. Remaining sections
require discussion one at a time: agree the intended claim, identify evidence, then
draft for explicit approval.

| Section | Question for later discussion |
|---|---|
| 1 Overview/problem framing | Draft above; discussion direction confirmed, wording awaiting approval |
| 5.6 Quality | Which specific architecture/readability decisions merit explanation, and what code supports them? |
| 5.7 Sensors / 5.8 Connectivity / 5.10 Technical depth | Which implemented mechanisms and exact tests will their owners substantiate? |
| 5.11 Appeal | What visual decisions are intentional, and what evaluation supports their effect? |
| 5.12 Guidelines | Which standards and tested UI behaviors support the claim beyond the recorded target-size fix? |
| 5.13 Flow / 5.14 Language | What do the responsible members' current evaluations establish? |
| 5.16 Novelty | Draft above; includes designed departure advice with implementation/validation pending; wording awaiting approval |
| 5.17 Surprise | Draft above; arrival unlocks and collection motivation, plus progressing departure advice; wording awaiting approval |
| 5.18 Tech Knowledge | Draft above; sensing, external-data quality, decision support and persistence; departure implementation evidence pending; wording awaiting approval |
| 5.19 Cross-Disciplinary | Which external discipline is applied, with what source and implementation evidence? |
| 5.20 Impact | Which benefit is plausible, and which benefit has actually been measured? |

## Required material and references — PLACEHOLDERS, NOT APPROVED

Sections 2 and 5.2-5.5 require the actual video, compilation image, commit-log export
and member-confirmed contribution sheet. This draft does not establish their completion.
Section 7 needs a checked bibliography and attributions. Existing evidence links are
working references, not a completed citation inventory. Retain all 19 rubric criteria;
keep Section 5 within ten rendered pages. No publicity consent is implied.
