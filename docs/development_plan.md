# StoryStep: Requirement-Driven Development Plan

Prepared: 2026-08-31. Planning baseline: `762662e` (`framework-build`).

Status: proposed execution plan, not a claim that the features or tests below are complete.

Current frontend assessment: `feature/frontend-ui` contains the frontend implementation at
`142c26f` and is integrating `origin/main@204708f`, including the REQ-02 process-wide session
lifecycle. The branch is pushed for review; Android and physical-device acceptance remain partial.

## 1. How to use this plan

This document turns the scope in [README](../README.md) into independently implementable and verifiable requirements. [CLAUDE.md](../CLAUDE.md) remains the engineering rulebook. The [assignment plan](assignment1_project_plan_draft.md) remains the high-level project/report draft; this document refines its delivery order, not the approved product scope.

Repository guidance: `AGENTS.md` points to the restored root `CLAUDE.md`. Its current-state section
was reconciled with the frontend/location integration while preserving the supplied scope, build,
technology and architecture constraints.

Read the documents in this order:

1. `CLAUDE.md` and `README.md`: constraints and scope.
2. This plan: behavior, dependencies, task IDs and acceptance conditions.
3. [Verification checklist](verification_checklist.md): requirement status, executable acceptance cases and evidence records.

Use one requirement ID per issue, splitting larger requirements into the numbered subtasks below. One PR may implement a subset, but the requirement is not accepted until all its acceptance conditions are verified. Code generation, successful compilation and merging are not substitutes for acceptance evidence.

Project documentation and code comments stay in English. Team discussion can use any language.

## 2. Baseline: reuse rather than rebuild

| Area | Present at the planning baseline | Remaining work |
|---|---|---|
| Identity/build | Lowercase `com.comp90018.storystep`, Java/XML, AGP/SDK setup | Preserve; run compatibility checks after changes |
| Domain | `Attraction`, `GeoPoint`, category enum, `GeoCalculator` | Location samples, sessions, motion, arrival and ranking policies |
| Discovery UI | Nearby and detail Fragments, ViewModels, navigation, basic states | Real origin, selection, freshness and recoverable states |
| Repository | Interface and synchronous in-memory implementation | Room-backed runtime source and remote/cache coordination |
| Local data | Attraction entity/DAO/mapper and unconnected database | Schema exports, migrations, saves, sessions, route points, visits, weather cache |
| Remote data | Generic Retrofit contract, DTO/mapper, client factory | Actual service choice, credentials, requests, weather and failure behavior |
| Device interfaces | `LocationSource`, `HeadingSource`, `ConnectivityMonitor` | Android implementations, timestamps, motion source and lifecycle ownership |
| Tests | Six JVM test classes; identity and DAO instrumented test classes | Requirement tests, real lifecycle integration and physical-device evidence |

Historical verification from 2026-08-29: `test assembleDebug` succeeded; stored JVM reports contain 45 tests and no failures; Lint reported 0 errors and 24 warnings. These are baseline observations, not fresh evidence for future requirements. No physical-device verification is established by these reports. SET-01 must rerun the baseline before implementation.

Reinspect the working tree before each task: implementation may advance while this plan is being prepared. In particular, local location/test dependency additions were observed during preparation and were left untouched. The commit-based baseline is not an instruction to remove newer work or duplicate an existing implementation.

Specific integration work already visible in the code:

- `AppContainer.getReferenceOrigin()` returns `DEMO_ORIGIN`; the nearby ViewModel has a final origin. REQ-03/04 must replace the fixed runtime assumption.
- `RepositoryCallback` already requires Room/network implementations to deliver on the main thread. Preserve and test that contract; do not report it as missing.
- `GeoPoint` checks coordinate ranges but does not explicitly reject `NaN`. REQ-03 adds finite-input tests and validation before sensor/remote input is trusted.
- `ApiClientFactory` used BASIC logging with a comment claiming keys could never leak. That was too broad — request URLs carry credentials or coordinates — and it was resolved on the weather branch before the first real request: logging now records method, host, path and status only, and the `logging-interceptor` dependency is gone.
- Room schema export is disabled and backup rules are templates. Complete REQ-14 and NFR-04 before storing real user routes.
- An attraction cache refresh must not erase saved attractions or visits. Review the existing `REPLACE` upsert when introducing relationships.

### 2.1 Current frontend implementation snapshot

The frontend is a cross-iteration clickable prototype, not a completed iteration. Its screens and
navigation reach later user journeys, while most of the corresponding domain, persistence, sensor
and network requirements remain incomplete. A rendered page or navigation action is therefore
recorded as `FOUNDATION_ONLY` unless it is connected to the required production behavior.

| Frontend area | Plan coverage | Current assessment on `feature/frontend-ui` | Next acceptance gate |
|---|---|---|---|
| App shell, onboarding and location rationale | I0; REQ-01; NFR-03 | XML screens, edge-to-edge shell, bottom navigation and explicit location explanation are implemented | Fresh-install Android test, first-run behavior, Back/Up and accessibility checks |
| Home / nearby discovery | I1; REQ-01, REQ-03, REQ-04 | Professional home UI is integrated with the foreground location adapter, permission recovery, reference/live-origin labels and repository-backed attraction list | API 31+ permission matrix, provider-off/stale-fix tests, rotation/navigation tests and physical fix evidence |
| Attraction details and start confirmation | I1; REQ-05 | ID-based repository loading, error presentation, confirmation dialog and exploration navigation exist | Bind destination to a real session; replace Save toast with REQ-17 persistence; add restoration/error tests |
| Active exploration | I2; REQ-02, REQ-06, REQ-07, REQ-10, REQ-11 | Designed map/dashboard and stop dialog exist; one process-wide coordinator owns the real foreground location session, but dashboard values and route remain prototype presentation | Bind the dashboard to coordinator samples, then add route/motion/heading data and durable checkpoints |
| Paused-session recovery | I1-I2; REQ-02, REQ-15 | Recovery appears only for a real paused process session; Resume, Finish and Discard command the shared coordinator, and elapsed time/segment come from its session summary | Implement Room-backed restoration after process death and verify the full interruption matrix |
| Arrival, story and memory | I4; REQ-12, REQ-13 | Complete clickable visual flow exists, including a preview-only arrival action | Replace preview with quality-aware arrival detection and exactly-once persisted visit/story delivery |
| Journeys, summary and detail | I4; REQ-15, REQ-16 | List and detail read real finished sessions through `HistoryRepository`; segmented route sketch, movement/weather/interrupted labels, per-session and clear-all deletion, and story reopening addressed by session id | Re-run AT-16A/AT-16B on a physical device; `JourneySummaryFragment`'s own body is still prototype content apart from the journey id it now passes |
| Saved | I3; REQ-17 | Durable `saved_attractions` table and process-scoped repository; detail, nearby rows and the saved list share one saved-id set; per-attraction toggle serialisation; Clear saved places wired, and REQ-16's clear-all is proved not to touch it; the snapshot carries its own indoor classification and provenance at schema 6 | Re-run AT-17B now that REQ-18 supplies a refreshable remote cache; fall back to the saved copy when the detail screen's cache lookup misses; make the saved search control real |
| Weather and contextual presentation | I3-I4; REQ-19, REQ-21, REQ-22 | Real Open-Meteo observations on the nearby card and the exploration dashboard, cached per 0.05-degree area, refreshed after 30 min, served stale-and-labelled when a refresh fails and reported as offline when nothing is cached; the observation is now copied onto the session row at schema version 5 and shown, labelled by observation time, on the journey-detail screen; chips remain fixtures | Implement REQ-20.2 retry/backoff; add ranking/reasons (REQ-21/22) and a visible provider attribution |
| Profile, capability recovery and privacy/data | NFR-03/04 and lifecycle support | Settings/recovery presentation and a dedicated Privacy & Data page exist; permissions open real system settings and destructive controls are confirmed prototype actions | Bind real permission/provider/session states and implement scoped history/saved deletion with backup-policy verification |

Iteration-level interpretation:

- I0 frontend readiness is substantially present, but accessibility and multi-device evidence are
  not complete.
- I1 is the active functional stage: REQ-01 and REQ-03 are merged with partial verification;
  REQ-04/05 remain in review or progress.
- I2-I4 have useful UI prototypes for parallel development, but they do not pass their iteration
  exit gates because route/sensor binding, persistence, network and arrival behaviors are missing.
- I5 stabilization has not started. A successful local build is regression evidence, not release
  acceptance.

Detailed command results and limitations are recorded in
[EV-FRONTEND-2026-08-31-01](verification/EV-FRONTEND-2026-08-31-01.md).

## 3. Scope, assumptions and decisions

### 3.1 Required product coverage

| Source requirement | Implementation IDs |
|---|---|
| Nearby discovery and attraction information | REQ-03, REQ-04, REQ-05, REQ-14, REQ-18 |
| User-controlled foreground exploration | REQ-01, REQ-02, REQ-06, REQ-07, REQ-15 |
| Accelerometer movement estimation | REQ-08, REQ-09 |
| Compass, distance and direction | REQ-03, REQ-07, REQ-10, REQ-11 |
| Location-triggered stories | REQ-12, REQ-13 |
| History and saved attractions | REQ-15, REQ-16, REQ-17 |
| Weather and contextual recommendations | REQ-19, REQ-21, REQ-22 |
| Meaningful Internet use and offline behavior | REQ-18, REQ-19, REQ-20 |
| Privacy, reliability, accessibility and evidence | NFR-01 through NFR-06 |

Attraction images, richer category preferences, cloud synchronization and community ratings/tips are stretch work, not prerequisites for this MVP. No account system or Firebase project is required merely to demonstrate Internet connectivity. Real attraction retrieval plus weather affecting recommendations satisfies this plan's connectivity objective; the actual course rubric still needs human confirmation.

Do not add turn-by-turn routing, background tracking, booking, payments, chat, AR navigation, emergency features, large-scale social networking or iOS support.

### 3.2 Decision register

The defaults below make planning concrete. They are proposed product policies, not facts from the rubric. Record approval or a replacement before the affected implementation starts. Unrelated tasks can proceed while a decision is open.

| ID | Decision and proposed default | Owner | Resolve before |
|---|---|---|---|
| D-01 | Replace M1-M5 with actual members; confirm dates, capacity and submission artifacts | M1/team | Iteration planning |
| D-02 | Select attraction and weather services; approve licensing, caching, authentication, quotas and cost | M5 + M1 | REQ-18/19 production integration. **Weather half resolved 2026-09-05: Open-Meteo** — HTTPS, free for non-commercial use, no API key and no account, so no credential has to be kept out of Git and a reviewer can run the app with no setup. Rejected: OpenWeatherMap, which needs a registered key the rest of the team would not hold. **Attraction half resolved 2026-09-07: OpenStreetMap Overpass API** — HTTPS, no key, no account, and ODbL explicitly permits the caching REQ-20's offline behaviour depends on. Rejected: Google Places and Foursquare, both of which need a key bound to a billing account; Wikipedia GeoSearch as the *primary* source, because its results are articles near a point rather than attractions, with no category or indoor field to map — kept as the intended enrichment follow-up. Full note: [D-02 attraction provider](decisions/D-02-attraction-provider.md) |
| D-03 | Obtain the official rubric; verify sensor-count interpretation, cloud expectations, report/demo and AI-use disclosure rules | M1/team | Claiming assessment compliance |
| D-04 | Foreground policy: background/lock pauses collection; returning requires explicit Resume; rotation does not create a new session | M1 + M4 | REQ-02 |
| D-05 | Track visits for the selected destination; do not unlock every nearby attraction simultaneously | M1 + M2 | REQ-12 |
| D-06 | Store a sampled local route; exclude sensitive app data from backup/transfer; retain until user deletion, with no public route export | M4 + M1 | REQ-07/15 real data |
| D-07 | Initial sensing/cache/scoring settings below; tune against controlled and physical trials | M3 + M5 + M1 | Final sensor/context acceptance |

Do not create accounts, buy plans, deploy a backend or upload routes to resolve these decisions without a separate team authorization. If a service cannot be selected, keep fixtures and contracts testable, but mark real-network acceptance BLOCKED rather than complete.

### 3.3 Fixed engineering constraints

Keep Java application code, XML layouts, manual dependency injection, ViewModel/LiveData, Room, Retrofit/OkHttp, Java 11 source/target and API 26/37 settings. Use the version catalog for dependencies. Preserve the AGP 9 DSL documented in `CLAUDE.md`; do not copy older Gradle syntax or migrate technologies as part of a feature.

The package root is `com.comp90018.storystep`. Keep `domain` free of Android imports. There is no requirement to split the project into Gradle modules or introduce a DI, reactive or navigation framework.

## 4. Shared architecture and behavioral contracts

### 4.1 Ownership and data flow

The following are proposed additions, not claims that the classes already exist. Add only what the selected requirement needs.

| Layer/package | Responsibility and proposed additions | Must not own |
|---|---|---|
| `domain.model` | `LocationSample`, `SessionSummary`, `WeatherSnapshot`, `Visit`, `MovementState`, result metadata | Android `Location`, `SensorEvent`, Room entities or Retrofit DTOs |
| `domain.session` | Pure session transitions and duration policy | Android lifecycle registration |
| `domain.geo` | Existing maths, location-quality and route policies | Views or providers |
| `domain.motion` | Filtering and movement classifier | `SensorManager` |
| `domain.guidance` / `domain.arrival` | Angular smoothing, cardinal labels, arrival state | Fragment navigation |
| `domain.recommendation` | Deterministic score and reason codes | HTTP calls, UI strings or ML services |
| `domain.repository` | Separate attraction, weather, session and saved-attraction contracts | One oversized all-feature repository |
| `platform.location` / `platform.sensor` | Android adapters, availability, timestamps, registration and cleanup | Business scoring and persistence schemas |
| `application` | A small `ExplorationCoordinator` composing session state, sources, calculations and repositories | Views; a general-purpose service framework |
| `data.local`, `data.remote`, `data.repository` | Storage, provider DTOs, mapping, cache policy, I/O and callback dispatch | Activity references |
| `ui.*` | ViewModels render state; Fragments forward actions and observe view lifecycle | Direct sensor registration, SQL, HTTP or geographic formulae |
| `di` | App-process assembly, shared executors, clock and test seams | Demo position as an unconditional real-user origin |

One coordinator owns the active session and its location subscription. It distributes accepted samples to nearby distance, route accumulation, arrival and heading-declination consumers. Multiple screens must not each start another GPS listener. A shared accelerometer adapter may fan out to motion and heading without duplicate registration. Dependency direction is composition from the application layer into domain contracts and platform/data adapters.

Android lifecycle events are forwarded into the coordinator by a thin adapter. The Activity does not contain the session or sensing calculations. Do not rely on ViewModel `onCleared()` alone to stop tracking: it may remain alive while the app is not visible.

### 4.2 Session semantics

| State/event | Next state | Required side effects |
|---|---|---|
| IDLE + Start | STARTING | Explain/request access; no samples or history yet |
| STARTING + granted, app visible, session record created | ACTIVE | One session ID; start sources once; waiting for a fix is visible |
| STARTING + denied/cancelled/persistence failure | IDLE | No tracking; actionable explanation; no false successful session |
| ACTIVE + app background/lock, permission loss or location provider loss | PAUSED | Unregister sources, freeze active duration, checkpoint, reject queued late samples |
| ACTIVE + screen navigation/rotation | ACTIVE | Same logical session and totals; no duplicate subscription; rebind UI safely |
| PAUSED + explicit Resume while visible and access available | ACTIVE | Same session ID; begin a new route segment; reset stale sensor/arrival windows |
| ACTIVE or PAUSED + Stop | STOPPING, then COMPLETED after save | Stop sources immediately; persist once; show retry if save fails |
| ACTIVE or PAUSED + Discard | STOPPING, then IDLE after deletion | Stop sources immediately; remove the unfinished session without adding history |
| Relaunch with an unfinished record | PAUSED/INTERRUPTED presentation | No automatic sensor restart; offer Resume or Finish with preserved checkpoint |

At most one unfinished session is allowed. Stop is idempotent. STARTING and STOPPING disable duplicate actions. Missing motion/heading sensors do not stop an otherwise usable location session. A stale/low-quality fix shows degraded guidance; it is not fabricated as a valid arrival.

Active duration excludes paused time. Store wall-clock start/end for display, but use a monotonic clock for elapsed intervals. Across process death/reboot, use persisted accumulated duration, never subtract monotonic values from different boots. An interrupted record is not falsely labeled a normally completed session.

Before Start, users may browse cached/seed attractions, saved places and history without collecting location. Label the reference area and data origin. Never present distances from `DEMO_ORIGIN` as distances from the user. Retained last-session location is labeled with its age; do not silently restart it.

### 4.3 Contract details to agree in SET-01

- Location: immutable coordinate, horizontal accuracy, wall timestamp, monotonic timestamp and origin/freshness metadata. Reject non-finite values and out-of-order samples. Test synthetic sources are visibly separate from real sources.
- Threading: slow I/O stays on a bounded executor; repository callbacks are dispatched to the main thread, matching the existing callback contract. Pure tests inject executors and clocks.
- Concurrency: one-shot requests complete once; a request/session generation token prevents cancelled or old results overwriting newer state. Dispose subscriptions and ignore callbacks after disposal.
- Cached data: content can coexist with refreshing, stale or offline metadata. Do not erase usable content just to show a spinner. Typed reasons distinguish permission, no fix, no cache, timeout, rate limit, malformed response and configuration errors.
- Nearest versus recommended: retain the nearest-first `AttractionRepository` contract. Apply contextual ranking in a separate domain/use-case layer and label the chosen sort mode.
- Story events: an accepted visit is keyed by `(sessionId, attractionId)`. Store it transactionally before announcing it. A persistent unread/read marker allows recovery without creating another visit or replaying a consumed UI event on rotation.

### 4.4 Minimal persistence model

These are logical fields, not mandatory final table/class names. M4 owns schema versions; coordinate migrations before merging parallel branches.

| Record | Minimum fields/invariants |
|---|---|
| Attraction cache | Stable provider-qualified ID, name, category, description/story content when available, indoor classification including UNKNOWN, coordinates, provenance, fetched time |
| Session | ID, state, start/end time, accumulated active duration/distance, selected destination ID/snapshot, movement-duration summary, weather snapshot, last checkpoint time |
| Route point | Session ID, sequence, segment ID, coordinate, accuracy and timestamp; bounded sampled storage, not raw sensor streams |
| Visit | Unique session/attraction pair, arrival time, attraction/content snapshot or durable reference, unread/read presentation state |
| Saved attraction | Unique attraction ID, saved time and an offline-readable snapshot or cache-retention guarantee |
| Weather cache | Location bucket, units, condition, temperature, observation time, fetch time and provenance |

Deleting a session removes only its owned route points and visits, not independent saves. Refreshing/evicting cache must not cascade into user history. Unknown indoor classification must not be silently treated as confirmed outdoor information; evolve the current boolean model carefully or keep explicit companion metadata.

### 4.5 Initial settings to test, not guarantees

Put selected values in named configuration objects; do not duplicate magic numbers across screens. Approve D-07 before treating them as release settings.

| Parameter | Proposed initial value/policy | Verification |
|---|---|---|
| Nearby radius | 3,000 m; inclusive boundary | Deterministic just-inside/at/outside tests |
| Location acquisition | Request approximately every 2-5 s while active; allow repeated stationary fixes | Actual callbacks vary; measure timestamps, not assumed cadence |
| Stale fix | Older than 15 s is unusable for live arrival/route updates | Fake clock boundary tests |
| Route-quality threshold | Accuracy <= 30 m; reject jumps implying > 8 m/s; ignore displacement <= 3 m | Synthetic routes plus outdoor drift trials; not a travel-speed classifier |
| Arrival | Selected target; distance + accuracy <= 50 m; accuracy <= 25 m; >= 3 accepted fixes spanning >= 10 s | Reset on bad/outside/stale fixes or gaps > 15 s; never rely on one fix |
| Approximate-only permission | Discovery allowed with approximate labeling; disable route precision and automatic arrival | API 31+ permission tests; no request loops |
| Motion sampling | Start with a 50,000 microsecond request (~20 Hz); timestamp-driven 2 s windows | Tune filtering/thresholds using recorded stationary/walking fixtures |
| Heading smoothing | Circular smoothing; never ordinary averaging across 359/0 degrees | Wraparound and rotation tests |
| Cache freshness | Attractions 24 h, weather 30 min; older data can display as stale | Inject clock; stale weather is neutral for ranking |
| Network refresh | No request per sensor event; one in flight per key; weather refresh only on expiry/meaningful area change or user retry | Fake service call counts and rate-limit tests |
| Checkpoint/route sampling | At most 5 s uncheckpointed active data under normal operation; sample at <= 1 stored point/5 s | Interrupted-process test and a 60-minute bounded-storage run |

These conservative defaults trade missed triggers for avoiding false arrival claims. Tune them with evidence rather than broadening them until a demo happens to pass.

## 5. Team roles and issue sizing

Roles mirror the existing five-person draft. Replace labels with real names in the team board, not with invented identities.

| Role | Primary responsibility | Typical review partner |
|---|---|---|
| M1 | Integration, location, session contracts, arrival | M3 for calculations; M4 for persistence |
| M2 | Discovery/details, exploration UI, accessibility, acceptance UX | M5 or M1 |
| M3 | Motion, orientation, guidance, physical sensing evidence | M1 |
| M4 | Room, session durability, history, saves, deletion/backup | M1 |
| M5 | Service selection, remote/cache/weather, recommendations | M2 or M4 |

Sizing is a planning aid, not a calendar promise: S = about 0.5-1 focused developer-day, M = 1-2, L = 2-3. Includes local tests, excludes dependency waiting. Split an L issue into the numbered tasks if it exceeds two days without an integrated result. Reserve roughly 25% of available iteration capacity for integration, review, physical tests and defects; recalibrate after the first iteration. Do not assign five people concurrent edits to `AppContainer`, `ViewModelFactory`, `nav_graph.xml`, the version catalog or a schema version.

## 6. Enabling tasks

### SET-01 Baseline, contracts and deterministic fixtures

Owner M1; reviewer M4; iteration I0; size M.

- SET-01.1: Rerun build/JVM/Lint; launch on an emulator; record commit and results without marking future requirements verified.
- SET-01.2: Approve D-04/05/06, define the minimal sample/session/result contracts from section 4 and their test seams. Avoid implementing every proposed class in advance.
- SET-01.3: Add reusable fake clock, location/sensor sources, executors and repository fixtures in test sources; debug fixture injection must be explicitly labeled and excluded from normal release flow.
- SET-01.4: Create the first requirement issues, assign owner/reviewer and use the verification ledger. Confirm the integration branch includes `762662e`; do not start features from an older `main` accidentally.

Exit: baseline evidence, agreed contracts, fake-driven sample-to-state smoke test and a runnable next issue. D-01/D-03 remain visibly open if the team has not supplied the information.

### SET-02 Service-selection spike

Owner M5; reviewer M1; start I0, close before real-network work; size M.

- SET-02.1: Compare at least two viable candidates for each of attraction and weather data: local coverage, HTTPS, schema, quotas, auth, billing, attribution, cache/redistribution rights and expected outages.
- SET-02.2: Capture sanitized sample responses and an adapter mapping. Use documented endpoints; the current generic `GET attractions` is not evidence that a real provider supports it.
- SET-02.3: Confirm a small Melbourne/campus demo dataset with stable IDs and source attribution. Do not invent landmark facts or represent fixtures as live API results.
- SET-02.4: Record the selected provider and rejected alternatives in a decision note, with safe configuration instructions and an expiry/rate-limit strategy. Resolve D-02 with team approval.

Exit: an approved, reproducible request for each service using team-authorized configuration, or an explicit blocker. This is not authorization to purchase or provision services.

## 7. Functional requirement cards

All cards inherit NFR-01/02/04 safeguards and the Definition of Done. Acceptance case IDs refer to the companion checklist. New test class names below are implementation targets, not tests already executed.

### REQ-01 Permission and entry

User outcome: browse without tracking, and understand/control location access when starting exploration.

Owner M1; reviewer M2; I1; M; depends SET-01. Baseline: not implemented.

- REQ-01.1: Add concise purpose/foreground/privacy explanation and Start entry action; no startup permission dialog.
- REQ-01.2: Implement Android runtime permission flow, including precise, approximate, one-time and denied outcomes. Request fine/coarse together when precision is needed; handle coarse-only access separately. See [Android permission guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime).
- REQ-01.3: Handle rationale, permanently denied/settings recovery and disabled providers without repeated prompts. Recheck permission/provider state on Resume; do not mistake every first denial for permanent denial.
- REQ-01.4: Add UI-state tests and a device permission matrix. Do not request background location or unrelated permissions.

Code areas: `platform.location`, `ui.nearby`/permission UI, manifest and strings.

Acceptance: AT-01A, AT-01B. No source starts before Start plus permission; denied/coarse users retain meaningful browsing; settings recovery requires explicit user continuation.

### REQ-02 Foreground session lifecycle

User outcome: one clearly controlled session, with reliable stop/pause/resume behavior.

Owner M1; reviewer M4; I1; L; depends SET-01, REQ-01. Baseline: not implemented.

- REQ-02.1: Implement the section 4.2 pure state machine and idempotent commands with injected clock and fake session storage.
- REQ-02.2: Add coordinator ownership of subscriptions; gate starts on visibility/access; reject late callbacks by session generation.
- REQ-02.3: Connect background/lock, foreground return, rotation and permission/provider-loss transitions. Deliver minimal visible Start/Stop/Resume controls in I1; do not defer the user's way to stop collection until the I2 dashboard. Screen changes inside the app retain the same logical session.
- REQ-02.4: Write `SessionStateMachineTest` and coordinator subscription-count tests; bind real persistence in REQ-15.

Code areas: `domain.session`, `application`, lifecycle adapter, `di`.

Acceptance: AT-02A, AT-02B. Double Start/Stop does not duplicate sessions; no collection after pause/stop; return does not silently resume; rotation preserves identity.

### REQ-03 Real location and quality-aware samples

User outcome: position-derived features use current, valid coordinates and disclose unavailable/poor data.

Owner M1; reviewer M3; I1; L; depends SET-01, REQ-01, REQ-02. Baseline: interface only.

- REQ-03.1: Introduce immutable timestamped samples and finite/range/accuracy validation; add `NaN`/infinity tests to `GeoPoint` and remote mapping.
- REQ-03.2: Implement a foreground Android location adapter behind `LocationSource`. Inspect any newer provider/dependency work first. Record the team's choice of platform location APIs or an already-approved fused provider, including behavior on unsupported devices; do not add, remove or replace provider dependencies merely to match this baseline.
- REQ-03.3: Apply permission/accuracy/freshness policies and typed unavailable reasons; expose a shared changing origin instead of `DEMO_ORIGIN` as real location.
- REQ-03.4: Test normal, stale, out-of-order, duplicate, invalid, permission-revoked and provider-disabled samples with a fake clock; record a real device fix separately.

Code areas: `domain.model`, `domain.geo`, `platform.location`, `di`, `GeoPoint` tests.

Acceptance: AT-03A, AT-03B. Non-finite or stale samples never enter route/arrival calculations; a current fix updates consumers; unavailable data never becomes `(0,0)` or a fake user origin.

### REQ-04 Dynamic nearby discovery

User outcome: see relevant attractions around the active origin with clear data/freshness states.

Owner M2; reviewer M5; I1; M; depends REQ-03, REQ-14. Baseline: fixed-origin list and basic states.

- REQ-04.1: Refactor the final-origin ViewModel input to consume/update location state; preserve test injection and the existing screen.
- REQ-04.2: Filter within the inclusive radius and retain stable nearest-first ordering; throttle re-queries and ignore out-of-date responses.
- REQ-04.3: Distinguish loading, empty, content, refreshing, cached/stale, permission-limited and no-fix states; preserve useful content during refresh failures.
- REQ-04.4: Add origin-change/radius/tie/late-response tests and list retry/rotation UI tests. Clearly label demo/reference-area browsing before a session.

Code areas: `ui.nearby`, `AttractionRepository`, `AppContainer`, list resources.

Acceptance: AT-04A, AT-04B. Moving an injected origin changes distances/order without recreating the app; old request completion cannot restore the old list.

### REQ-05 Attraction details and destination selection

User outcome: understand a place and select it for exploration without changing location privacy rules.

Owner M2; reviewer M1; I1; M; depends REQ-04, REQ-02. Baseline: read-only detail screen.

- REQ-05.1: Preserve ID-based navigation; show name, category, description, indoor/outdoor/unknown and source attribution when available.
- REQ-05.2: Add Select destination/Explore action. From idle, selection routes through explicit Start; from an active session it changes only the destination.
- REQ-05.3: Handle missing/deleted IDs, loading, recoverable error and unavailable content; add retry and predictable Back navigation.
- REQ-05.4: Test process/rotation restoration, target switching and unavailable content; add the Save control when REQ-17 is integrated.

Code areas: `ui.detail`, navigation graph, coordinator destination state.

Acceptance: AT-05A, AT-05B. The selected ID survives rotation; target switching resets only target-specific arrival state; opening details alone starts no tracking.

### REQ-06 Active exploration dashboard

User outcome: see the session status and stop it easily.

Owner M2; reviewer M1; I2; M; depends REQ-02, REQ-05. Baseline: not implemented.

- REQ-06.1: Add Fragment/ViewModel/XML showing destination, session state, active duration, distance and visible Stop/Resume controls.
- REQ-06.2: Bind route, motion, direction and weather as independent optional sections; missing context must show unavailable, not zero or fabricated values.
- REQ-06.3: Keep controls reachable at large font sizes and after navigation; show STOPPING/save-failure states without allowing new collection.
- REQ-06.4: Test timer with a fake clock and all state/control combinations; UI timers redraw rather than persist every tick.

Code areas: proposed `ui.exploration`, factory, graph and strings.

Acceptance: AT-06A, AT-06B. Duration excludes pauses, Stop remains visible, and partial data availability does not block ending the session. Final verification includes I3 weather wiring.

### REQ-07 Approximate route and travelled distance

User outcome: review a plausible sampled route and total, not a turn-by-turn path.

Owner M1; reviewer M3; I2; L; depends REQ-03, REQ-02, REQ-15. Baseline: point-to-point maths only.

- REQ-07.1: Implement a pure route accumulator using quality-controlled samples and the existing geographic calculator.
- REQ-07.2: Ignore tiny drift and impossible jumps; break segments across pause/no-fix gaps; never draw or count a straight bridge across missing travel.
- REQ-07.3: Persist bounded sampled points and totals through session storage. Do not store every raw sensor event; see D-06 and checkpoint policy.
- REQ-07.4: Add synthetic path, stationary drift, jump, resume and unknown-motion tests. When REQ-09 is available, use reliable stationary context conservatively; missing motion must not disable GPS accumulation.

Code areas: `domain.geo`/`domain.session`, coordinator, session repository.

Acceptance: AT-07A, AT-07B. Known synthetic segments sum within the test tolerance; rejected data adds zero; route segments remain separate. Physical accuracy is recorded, not assumed.

### REQ-08 Accelerometer acquisition

User outcome: motion context is collected only during an authorized visible session.

Owner M3; reviewer M1; I2; M; depends REQ-02, SET-01. Baseline: no motion source.

- REQ-08.1: Add a framework-free motion-source contract and timestamped x/y/z samples with units.
- REQ-08.2: Implement `SensorManager` availability and lifecycle-safe registration; share accelerometer readings with heading where practical.
- REQ-08.3: Copy event values before asynchronous processing; bound buffers and keep callbacks short. Do not infer actual cadence from a requested rate.
- REQ-08.4: Test no-sensor, repeated start/stop, late event and registration failure; log only sanitized sample statistics in test builds. See [sensor lifecycle guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview).

Code areas: `platform.sensor`, `domain.model`, coordinator.

Acceptance: AT-08A, AT-08B. Zero/one registration as expected, no retained stream after Stop, and absence becomes UNAVAILABLE rather than a crash.

### REQ-09 Movement-state estimation

User outcome: receive an approximate stationary/moving estimate and a session summary.

Owner M3; reviewer M1; I2; L; depends REQ-08. Baseline: not implemented.

- REQ-09.1: Implement gravity removal/filtering, timestamp-based windows and a documented activity measure in plain Java.
- REQ-09.2: Add hysteresis/dwell to avoid label flicker; include UNKNOWN/UNAVAILABLE for startup, gaps and missing sensors.
- REQ-09.3: Accumulate durations by state, not raw callback counts; reset stale windows on pause/resume; integrate the dashboard and route policy.
- REQ-09.4: Establish stationary, walking, phone-rotation and noisy fixture labels; tune thresholds on separate trials and record false positives/negatives.

Code areas: proposed `domain.motion`, session summaries and exploration UI.

Acceptance: AT-09A, AT-09B. All labeled deterministic fixtures meet the agreed classifier expectations; unavailable is not reported as stationary. No claim of medical/activity recognition accuracy.

### REQ-10 Heading acquisition and normalization

User outcome: a heading compatible with the true-north geographic bearing.

Owner M3; reviewer M1; I2; L; depends REQ-03, REQ-08. Baseline: `HeadingSource` contract only.

- REQ-10.1: Combine accelerometer and magnetic-field readings with rotation matrix/orientation APIs; handle display rotation and invalid matrices. Use [position sensor guidance](https://developer.android.com/develop/sensors-and-location/sensors/sensors_position), not deprecated orientation sensors.
- REQ-10.2: Convert magnetic heading to true heading using location/time-based declination; normalize and smooth circularly. The platform estimate can use [GeomagneticField](https://developer.android.com/reference/android/hardware/GeomagneticField); record any altitude approximation.
- REQ-10.3: Propagate accuracy, missing sensor and stale heading states; do not expose magnetic north as true north when correction context is unavailable.
- REQ-10.4: Unit-test normalization/declination sign/wraparound with injected values; physically test portrait/landscape and slow turns away from magnetic interference.

Code areas: `platform.sensor`, proposed `domain.guidance`, existing `GeoCalculator`.

Acceptance: AT-10A, AT-10B. Example magnetic 350 degrees + declination 15 degrees yields true 5 degrees; 359/1 smoothing stays near north; unusable heading is explicitly reported.

### REQ-11 Approximate directional guidance

User outcome: understand where the destination lies without mistaking it for a safe walking route.

Owner M2; reviewer M3; I2; M; depends REQ-06, REQ-10, REQ-03. Baseline: geographic bearing maths only.

- REQ-11.1: Combine true heading and target bearing through the existing relative-bearing method; render an accessible arrow and text direction.
- REQ-11.2: Show approximate distance and cardinal bearing when heading is unavailable; suppress live guidance when the location is stale/unavailable.
- REQ-11.3: Explain compass unreliability/calibration and straight-line guidance limitations. Do not introduce road routes or safety claims.
- REQ-11.4: Test all cardinal directions, wraparound, identical coordinates, target changes and low-accuracy states; verify arrow/text agreement on a device.

Code areas: `domain.guidance`, `ui.exploration`, resources.

Acceptance: AT-11A, AT-11B. Clockwise target difference produces the correct visual turn; missing compass still allows distance/cardinal information if location is usable.

### REQ-12 Arrival detection

User outcome: a nearby observation triggers arrival only when sustained and credible.

Owner M1; reviewer M3; I4 (pure fixtures may be prepared in I2); M; depends REQ-03, REQ-05, REQ-15. Baseline: not implemented.

- REQ-12.1: Implement a pure configurable arrival detector for the selected attraction using section 4.5 distance, accuracy, count and dwell rules.
- REQ-12.2: Reset candidate state on target change, pause, stale/outside/poor-quality fixes and long gaps; ignore duplicate timestamps.
- REQ-12.3: Require an active session and usable precise access; emit an arrival candidate to visit persistence, not direct Fragment navigation.
- REQ-12.4: Test exact threshold boundaries, noisy entry/exit, one lucky fix, target switches and revisits; field-test near at least two selected landmarks.

Code areas: proposed `domain.arrival`, coordinator.

Acceptance: AT-12A, AT-12B. The sustained inside sequence qualifies; a single/noisy/coarse fix does not. Final once-per-session behavior is enforced by REQ-13.

### REQ-13 Story reveal and exactly-once visit

User outcome: discover the place's story on arrival without duplicate notifications or lost visits.

Owner M2; reviewer M4; I4; M; depends REQ-12, REQ-15, REQ-05. Baseline: descriptions exist, arrival flow does not.

- REQ-13.1: Persist the visit with a unique session/attraction constraint and unread/read state in a transaction; on storage failure show retry without announcing a successful saved visit.
- REQ-13.2: Present an in-app story screen/banner with attraction identity, readable content and Continue exploration. Existing detail descriptions may remain accessible before arrival; no artificial content lock is required.
- REQ-13.3: Restore unconsumed stories after interruption without reinserting visits. Reopening historical content is allowed; re-emitting a consumed arrival alert on rotation is not.
- REQ-13.4: Test duplicate arrival callbacks, repeated entry, rotation, process recovery, missing story text and source attribution. Never generate unverified historical claims as fallback content.

Code areas: proposed `ui.story`, visit DAO/repository, coordinator and graph.

Acceptance: AT-13A, AT-13B. One visit row per pair; one consumed arrival event; saved/offline story text remains readable where available.

### REQ-14 Runtime local attraction cache and migrations

User outcome: usable local attraction content independent of a current network connection.

Owner M4; reviewer M1; I1; L; depends SET-01. Baseline: unconnected Room scaffold.

- REQ-14.1: Enable schema export with the existing Java annotation-processing setup; export the existing version before changing it and wire migration-test assets.
- REQ-14.2: Add an executor-backed Room repository, process-scoped database and correct main-thread callback delivery; initially seed a small attributed dataset idempotently.
- REQ-14.3: Add freshness/provenance/unknown-classification metadata as needed, bump schema versions and preserve data. Cache operations must be independent of saved/session deletion.
- REQ-14.4: Test DAO round trips, filtering, missing ID, duplicate seeds, transaction failures and every shipped migration. Preserve the pinned Room 2.x API unless a separate upgrade is approved; see [Room 2.x guidance](https://developer.android.com/training/data-storage/room/v2) and [schema export](https://developer.android.com/reference/androidx/room/Database).

Code areas: `data.local`, `data.repository`, `di`, app build config and schema assets.

Acceptance: AT-14A, AT-14B. Relaunch reads local content; migrations preserve fixture data; no main-thread DB access or destructive migration of user data.

### REQ-15 Durable sessions, routes and visits

User outcome: completed history survives relaunch and interruptions do not silently discard everything.

Owner M4; reviewer M1; begin I1, complete I2; L; depends REQ-14, REQ-02 contracts. Baseline: not implemented.

- REQ-15.1: Add session/route/visit models, DAOs, repository and migrations with unique IDs, segment boundaries and appropriate indexes. Merge this contract/schema before REQ-07 consumes it.
- REQ-15.2: Implement transactional create/checkpoint/finish and idempotent operations; checkpoint on transitions and at the bounded interval, not every UI tick.
- REQ-15.3: Restore unfinished work in a non-collecting paused/interrupted state; protect accumulated duration across clock changes/reboot; allow user Resume or Finish.
- REQ-15.4: Test process interruption, failed writes, double finish, duplicate visits and recovery. Preserve destination/content snapshots when remote records later disappear.

Code areas: `domain.repository`, `data.local`, `data.repository`, coordinator.

Acceptance: AT-15A, AT-15B. Completed sessions survive process death; an unfinished checkpoint restores without automatic tracking; final save failure is visible and retryable.

### REQ-16 Visit history and deletion

User outcome: browse previous explorations, their visits and approximate route summaries, and remove personal records.

Owner M4; reviewer M2; I4; M; depends REQ-15, REQ-07, REQ-13. Baseline: not implemented.

- REQ-16.1: Add history list/detail screens with date, duration, distance, visits, movement/weather summary and interrupted-state labels.
- REQ-16.2: Display a simple local segmented route sketch or ordered route summary; no map SDK or routing service is necessary. Do not connect segment gaps.
- REQ-16.3: Add confirmed per-session deletion, cancel behavior and clear-all-history control with explicit scope; preserve independently saved attractions.
- REQ-16.4: Test empty/large history, ordering, invalid session IDs, offline relaunch and deletion cascades. Use synthetic routes for shared evidence.

Code areas: `ui.journey` (**deviation from the proposed `ui.history`**: the whole user-facing vocabulary is "journey", `SessionRecoveryFragment` already lives there, and a rename would churn `nav_graph.xml`, every layout `android:name` and every import for no behavioural gain; new classes are named for what they do — `HistoryRepository`, `HistoryText`, `RouteSketchView`, `SessionHistoryEntry`), a new `domain.repository.HistoryRepository` over the existing session DAOs, graph and layouts.

Acceptance: AT-16A, AT-16B. History matches stored totals/visits, deletion removes owned route data, and the result remains correct after relaunch.

### REQ-17 Saved attractions

User outcome: save/unsave and revisit attractions even when offline.

Owner M4; reviewer M2; I3; M; depends REQ-14, REQ-05. Baseline: not implemented.

- REQ-17.1: Implement a separate saved-attraction repository/table with stable identity and durable offline content.
- REQ-17.2: Add Save/Unsave feedback to details and a saved list with empty/error states; keep UI state synchronized across screens.
- REQ-17.3: Serialize rapid toggles or disable until resolved; preserve saves when the remote cache refreshes/evicts a place.
- REQ-17.4: Test duplicate saves, failed writes, relaunch, offline details and independent removal; expose saved IDs to recommendation context.

Code areas: `domain.repository`, `data.local`, proposed `ui.saved`, `ui.detail`.

Acceptance: AT-17A, AT-17B. One saved record per attraction; persistence and UI agree; cache changes never silently unsave a place.

### REQ-18 Real remote attraction content

User outcome: discover data fetched from an approved Internet service, not just bundled fixtures.

Owner M5; reviewer M4; I3; L; depends SET-02, REQ-14, NFR-04 network safeguards. Baseline: generic service scaffold only.

- REQ-18.1: Adapt the chosen endpoint, pagination/limits and provider-specific DTOs into app-owned models; use stable qualified IDs and attribution.
- REQ-18.2: Wire real configuration and HTTPS requests through repository I/O; include the normal Internet permission. Do not invent an endpoint matching the scaffold.
- REQ-18.3: Validate required IDs/coordinates, preserve unknown optional fields, deduplicate records and distinguish malformed responses from a valid empty result.
- REQ-18.4: Test success, invalid/missing fields, non-finite coordinates, pagination, HTTP errors and cancellation with a local mock service; separately capture sanitized real-service evidence.

Implemented 2026-09-07 against **OpenStreetMap Overpass** (D-02 resolved; see
[the decision note](decisions/D-02-attraction-provider.md)). `OverpassQueryBuilder` composes the
Overpass QL, `OverpassAttractionSource` is the only class touching a socket and types every failure,
and `RemoteAttractionMapper` validates, classifies and deduplicates before anything reaches the
domain. `CachedAttractionRepository` puts the Room cache in front of it with the same cache-first,
coalescing, stale-fallback and atomic-replace policies as the weather path, plus an
`attraction_areas` table so a legitimately empty area is distinguishable from one never fetched.
Schema 6 makes the indoor classification tri-state wherever a place is stored — the cache, the saved
snapshot and the session destination — which is REQ-14.3's deferred unknown-classification work.
REQ-20.2 retry/backoff is deliberately **not** included; it remains open for both network paths.

Code areas: `data.remote`, `data.repository`, `ApiClientFactory`, manifest and safe build configuration.

Acceptance: AT-18A, AT-18B. Real data reaches the normal UI/cache and required attribution is visible; invalid records cannot crash or poison domain calculations.

### REQ-19 Weather context

User outcome: see current-area weather with age/source information, and get a useful fallback when unavailable.

Owner M5; reviewer M2; I3; M; depends SET-02, REQ-03, REQ-14, NFR-04 network safeguards. Baseline: not implemented.

- REQ-19.1: Add separate weather contract/DTO/mapper with explicit units, condition enum, observation/fetch times and location key.
- REQ-19.2: Fetch for the permitted current/reference area without sending route histories; cache snapshots and throttle calls independently of location frequency.
- REQ-19.3: Display temperature/condition and freshness on discovery/exploration; copy the relevant snapshot into session summaries, labeled by time. **Done** — the coordinator captures on the session's first accepted fix (not at `create`, where only the reference origin exists) with one retry at Stop, and `SessionStore.saveSessionContext` writes it; journey detail renders it through `WeatherText.formatRecorded`.
- REQ-19.4: Test sunny/rain/unknown, missing fields, unit conversion, stale/mismatched-area data and offline failure. Weather failure must not block a session.

Code areas: `domain.model`, `domain.repository`, `data.remote/local`, nearby/exploration UI.

Acceptance: AT-19A, AT-19B. Known fixture units map correctly, live source can be demonstrated, and stale/unknown weather is not presented as freshly observed.

### REQ-20 Cache, connectivity and failure recovery

User outcome: slow, unavailable or malformed network data does not erase useful local functionality.

Owner M5; reviewer M4; I3; L; depends REQ-14, REQ-18, REQ-19. Baseline: basic error states and connectivity interface.

- REQ-20.1: Implement freshness/cache-as-fallback policy, explicit content provenance and a connectivity adapter. Connectivity is a hint; handle actual request failures regardless of its value.
- REQ-20.2: Coalesce identical requests; implement bounded timeouts and deliberate retry/backoff/rate-limit behavior. Never automatically retry authentication/configuration errors forever.
- REQ-20.3: Preserve content during refresh; distinguish no cached content, valid empty remote result, stale content, 401/403, 429, 5xx, timeout and invalid payload.
- REQ-20.4: Test offline warm/cold launches, slow responses, timeout, malformed/partly invalid payloads and older request completion. A successful cache write must be atomic and must not replace a valid dataset with a wholly corrupt response.

Code areas: `data.repository`, `platform.connectivity`, UI result metadata.

Acceptance: AT-20A, AT-20B. Warm offline launch retains labeled content and saved/history flows; cold offline launch is actionable, not an infinite spinner or silent fake-live substitution.

### REQ-21 Deterministic contextual ranking

User outcome: the order changes for understandable contextual reasons while remaining predictable.

Owner M5; reviewer M3; I3; L; depends REQ-17, REQ-19, REQ-15 context contracts. Baseline: distance ordering only.

- REQ-21.1: Define a pure scoring input/output with distance, usable weather, local time, indoor/unknown classification, saved state and previous visits. Filter invalid/out-of-range candidates before scoring.
- REQ-21.2: Implement documented bounded component scores. Initial proposal: `0.45*distance + 0.25*weather + 0.10*time + 0.10*saved + 0.10*unvisited`, where each component is in [0,1] and higher is better.
- REQ-21.3: Use neutral 0.5 for unavailable contextual components; use `1-min(distance/radius,1)` for distance. Rain: indoor 1, outdoor 0, unknown 0.5; dry/unknown weather: neutral. Initial time preference: local 08:00-17:59 gives outdoor 1 and indoor 0.5; other times give indoor 1 and outdoor 0.5; unknown type/time is neutral. This is a preference, not daylight/opening-hour knowledge. Saved scores 1 if saved, 0 if known unsaved; unvisited scores 1 if not visited, 0 if visited; unavailable history is neutral. Tie-break by distance then stable ID.
- REQ-21.4: Add fixture tests for rainy/clear changes, exact ties, absent context, stale weather, saved/visited changes and invalid inputs. Keep the repository's nearest-first contract unchanged.

Code areas: proposed `domain.recommendation`, a composing use case/coordinator, tests.

Acceptance: AT-21A, AT-21B. Same inputs produce identical scores/order; a controlled rain fixture favors the indoor candidate; missing inputs do not produce NaN or eliminate all options. Final weights need D-07 approval.

### REQ-22 Recommendation presentation and integrated context

User outcome: see why a place is recommended and choose distance-only order if preferred.

Owner M2; reviewer M5; I4; M; depends REQ-04, REQ-21, REQ-17, REQ-13. Baseline: not implemented.

- REQ-22.1: Add Recommended/Nearby ordering controls and short localized reason text generated from score reason codes, not arbitrary prose.
- REQ-22.2: Recompute on meaningful location/weather/save/visit changes while keeping selection stable. Do not re-create the whole screen per sensor sample.
- REQ-22.3: Treat saved attractions as the minimum MVP preference signal; do not introduce a separate profile/preferences system unless approved.
- REQ-22.4: Verify online/rain/offline scenarios, stale weather becoming neutral, visit updates and transparent unknown states. No unsupported claims such as a venue being open now.

Code areas: `ui.nearby`, recommendation use case, shared labels and state.

Acceptance: AT-22A, AT-22B. Reasons agree with actual inputs, mode selection is labeled, and network loss retains sensible recommendations without pretending stale weather is current.

## 8. Quality requirements

### NFR-01 Build, identity and compatibility

Owner M1; reviewer M4; I0 and every iteration; S recurring. Baseline: build identity established.

- NFR-01.1: Preserve the fixed language/layout/SDK/package/build-DSL constraints and existing unit tests.
- NFR-01.2: Run JVM tests, Debug build and Lint for each PR; compare warnings with the documented baseline instead of treating all warnings as failures or upgrading every dependency blindly.
- NFR-01.3: Run identity/launch checks on API 26 and the configured target API emulator when available; include at least API 31+ for approximate-location behavior.
- NFR-01.4: Record toolchain/device prerequisites and distinguish missing SDK/emulator/network from a source-code defect.

Acceptance: AT-Q01A, AT-Q01B. No new unexplained errors, identity regression or incompatible runtime use; current build/test evidence is attached to the PR.

### NFR-02 Architecture, lifecycle and concurrency

Owner M1; reviewer M3; I0 onward; M recurring. Baseline: separation and callback contract exist.

- NFR-02.1: Keep domain algorithms framework-free, repositories separate, and dependencies injected through the existing container/factory.
- NFR-02.2: Serialize session state mutations, dispatch repository results to main, and cancel/ignore superseded callbacks; bound executors and queues.
- NFR-02.3: Observe with the view lifecycle, release Fragment view references in `onDestroyView`, and avoid Activity/View references in process-scoped services.
- NFR-02.4: Add delayed-response, rotation, navigation, pause/resume and duplicate-listener regressions. Test sensors stopping without relying only on `onCleared()`.

Acceptance: AT-Q02A, AT-Q02B. No domain Android imports, main-thread I/O, stale-result overwrite or duplicate active device subscription in controlled tests.

### NFR-03 Navigation, accessibility and failure-state UX

Owner M2; reviewer M5; I0 design, every UI iteration, I5 final; M recurring.

- NFR-03.1: Agree compact wireframes for entry, nearby, detail, exploration, story, saved and history; re-use Material XML and consistent system-bar inset handling.
- NFR-03.2: Specify loading/content/empty/error/offline/unavailable states for each relevant screen; every recoverable problem has a safe next action.
- NFR-03.3: Verify readable contrast, labels, TalkBack order, >= 48dp interactive targets and large fonts; avoid announcing compass changes continuously to accessibility services.
- NFR-03.4: Test small portrait/landscape layouts, 200% font scale where supported, Back/Up navigation and interrupted flows; record short task-based usability findings.

Acceptance: AT-Q03A, AT-Q03B. Essential controls remain visible/reachable, information is not color-only, and no common failure state traps the user.

### NFR-04 Privacy, credentials and data lifecycle

Owner M4; reviewer M5; I0 decisions, before real data/network, I5 audit; M.

- NFR-04.1: Enforce user-started foreground-only collection, minimal requested permissions and sensitive-route minimization. No analytics, uploads or public route export by default.
- NFR-04.2: Keep secrets out of Git/test fixtures/evidence. `local.properties` protects source control only; embedded APK configuration must not be treated as a server-side secret. D-02 must choose an appropriate provider authentication model; do not create a proxy service without approval.
- NFR-04.3: Remove or sanitize request logging before real credentials/coordinates are used. BASIC includes URLs in the pinned [OkHttp 4.12 interceptor source](https://raw.githubusercontent.com/square/okhttp/parent-4.12.0/okhttp-logging-interceptor/src/main/kotlin/okhttp3/logging/HttpLoggingInterceptor.kt); header redaction alone does not protect query parameters. Verify with synthetic sentinel values, not real secrets.
- NFR-04.4: Implement D-06 retention/deletion and explicit backup/transfer exclusions in both existing XML rule files. If data shares a database, exclude that database rather than pretending to exclude individual tables. Verify older and newer OS paths; see [Android Auto Backup rules](https://developer.android.com/identity/data/autobackup).

Acceptance: AT-Q04A, AT-Q04B. Sentinel credentials/coordinates are absent from logs/evidence; sensitive data is excluded according to policy; Delete and Stop actually take effect. Do not certify platform backup behavior without device evidence.

### NFR-05 Responsiveness, battery and bounded storage

Owner M3; reviewer M1; I2 onward, I5 final; M.

- NFR-05.1: Keep I/O and heavy processing off the main thread; throttle UI updates and network calls independently of sample rate.
- NFR-05.2: Use the slowest sampling sufficient for tested behavior; unregister unused sources and keep buffers/storage bounded.
- NFR-05.3: Run 20-minute active and stopped-session device trials; record device, brightness/network conditions, battery delta, registrations, memory and API request counts. Avoid claiming a universal battery percentage from one device.
- NFR-05.4: Stress repeated start/stop/navigation and a 60-minute synthetic session; investigate growth and ANRs. Proposed UI budget: a received state update renders within 500 ms on the reference device, excluding provider/network wait.

Acceptance: AT-Q05A, AT-Q05B. No unbounded queues/point growth, post-stop acquisition or ANR in the recorded trials; measured results and tuned parameters are documented.

### NFR-06 Traceability, regression and release evidence

Owner M1; reviewer M2; every iteration, I5 final; M recurring.

- NFR-06.1: Maintain requirement-to-PR-to-test-to-evidence links; keep implementation status distinct from verification status.
- NFR-06.2: Use deterministic fixtures for domain/repository tests, Android tests for lifecycle/storage/navigation, and physical trials for actual GPS/motion/compass behavior.
- NFR-06.3: Execute the end-to-end and failure matrix in the checklist on the release candidate; retain sanitized evidence and known limitations.
- NFR-06.4: Rehearse the demo, update setup/architecture/decisions, disclose assistance as required, and verify D-03 against the supplied rubric before submission claims.

Acceptance: AT-Q06A, AT-Q06B. Every required row has human-reviewed evidence; a missing physical device, service access or rubric stays a visible limitation, never a fabricated pass.

## 9. Iteration plan and dependency-aware work order

Use relative iterations until D-01 supplies real dates. The original draft's two-week stages are a starting cadence, not a new deadline commitment. I0 corresponds to its foundation stage and should be short because the code skeleton already exists.

| Iteration | Deliverable and work order | Work that can proceed in parallel after contracts | Exit gate |
|---|---|---|---|
| I0: readiness | SET-01; start SET-02; NFR-01/02 baseline; NFR-03 wireframes; NFR-04 policies | M2 wireframes, M3 sensor fixtures, M4 schema design, M5 provider spike | Baseline runs; contracts and first issues ready; open decisions visible |
| I1: first usable slice | REQ-14 -> REQ-15 initial schema; REQ-01 -> REQ-02 -> REQ-03 -> REQ-04 -> REQ-05 | M4 storage, M1 location/session, M2 UI against agreed fakes | Explicit Start -> real fix -> nearby/details -> Stop; local content survives relaunch; denial safe |
| I2: exploration and sensing | Finish REQ-15; REQ-06/07; REQ-08 -> REQ-09; REQ-10 -> REQ-11 | M2 dashboard, M1 route, M3 sensors, M4 durability tests; M5 finalize service setup | Start/walk/direction/Stop/checkpoint works; real sensor evidence; no background acquisition |
| I3: connectivity and context | REQ-17; REQ-18/19 -> REQ-20; REQ-17/19 context -> REQ-21 | M4 saves, M5 network, M3 ranking-test support, M2 weather UI/failure UX | Real attraction/weather retrieval; warm offline flow; rain fixture changes ranking |
| I4: complete user loop | REQ-12 -> REQ-13 -> REQ-16; REQ-22; reverify REQ-06/07 with all context | M1 arrival, M2 story/context UI, M4 history, M3 field trials, M5 cache/ranking review | Arrive once -> story -> Stop -> history; save/unsave and interruption recovery pass |
| I5: stabilization | NFR-01 through NFR-06 final verification; all E2E cases; regression fixes only | Owners fix their requirement defects; reviewers execute independent acceptance | Release candidate evidence complete; no unresolved blocking privacy/data-loss/core-flow defects |

REQ-17 is brought forward from the original history stage so saved preferences can feed ranking in I3. Session control/basic persistence starts earlier to make all sensor work user-controlled. These are dependency refinements, not extra features.

Cross-iteration integrations must remain explicit: REQ-06 gains weather in I3; REQ-07 gains motion context in I2; REQ-05 gains Save in I3; REQ-21/22 react to newly recorded visits in I4. A placeholder does not satisfy the final acceptance of the corresponding requirement.

Recommended first PR after SET-01: REQ-14.1 schema export plus its migration-test setup, or REQ-01.1 permission-entry presentation against the agreed fake. Keep each independently runnable; do not ask an assistant to implement I1-I5 in one change.

## 10. Development, review and iteration protocol

### Definition of Ready

- Requirement/subtask IDs, owner and independent reviewer assigned.
- Dependencies implemented or an explicitly agreed fake/contract available.
- Relevant D-xx decisions resolved; real provider endpoints and data licenses known before real integration.
- Acceptance cases and test data understood; target files and out-of-scope boundaries recorded.
- Branch based on the team's integration state containing the foundation commit; no unrelated working-tree edits overwritten.

### Definition of Done for a requirement

- All numbered subtasks implemented and integrated, not only mocked.
- Positive, boundary and failure paths pass the linked acceptance cases.
- Tests assert behavior rather than merely exercise methods; new regression cases accompany defect fixes.
- Debug build/JVM/Lint run; relevant Android/device checks have actual evidence or status remains NOT_VERIFIED.
- Lifecycle, threading, offline, privacy and accessibility impacts reviewed.
- No credentials, precise personal routes or unrelated changes in the diff.
- Reviewer reproduced the essential behavior; ledger row links PR, commit, tests, evidence and remaining limitations.

Workflow: select one ready issue -> agree tests -> implement smallest slice -> run checks -> independent review -> integrate -> rerun the affected user scenario -> update the ledger. If acceptance fails, record a defect linked to its requirement, implement a regression test and repeat. Do not start more surface features to hide a broken vertical slice.

At each iteration review, demonstrate the exit gate, review the failure matrix, reassess capacity and resolve decisions. Track completion by accepted requirements, not generated code, raw commit count or a subjective percentage. Use two short integration/review sessions per week where practical, while the actual schedule remains a team decision.

### Change control

Changes to scope, session semantics, thresholds, schema, provider or privacy policy require a short decision note: motivation, options, chosen behavior, affected requirement/test IDs, reviewer and date. Do not silently remove a required feature because it is difficult. Stretch work starts only after all core acceptance gates pass and the team explicitly prioritizes it.

This plan creates no GitHub issues, purchases, deployments, commits or pushes by itself. The team and Claude Code should use the handoff guide for bounded implementation requests.
