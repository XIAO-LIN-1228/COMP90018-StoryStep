# StoryStep: Requirement Verification and Evidence Ledger

Prepared: 2026-08-31. Last updated: 2026-09-05 (weather round). Requirements: [development plan](development_plan.md).

This is the single tracking ledger for requirement acceptance. All cases below begin as NOT_RUN. Existing skeleton code or historical build reports do not imply these end-to-end cases have passed. Do not tick a requirement merely because Claude Code implemented it or generated a test class.

Frontend integration snapshot: `feature/frontend-ui` includes the frontend implementation at
`142c26f` and the session-lifecycle work merged to `main` at `204708f`, assessed in
[EV-FRONTEND-2026-08-31-01](verification/EV-FRONTEND-2026-08-31-01.md). Screen presence is counted
as `FOUNDATION_ONLY` unless the required data, lifecycle and acceptance behavior is connected.

Branch snapshot, 2026-09-04. The two rounds recorded here on 2026-09-03 are now merged to `main`:

- `feature/exploration-dashboard` at `b54b3f6`, merged as `35659ef`: destination selection, the
  session-bound exploration dashboard and in-memory route accumulation
  ([EV-REQ05-REQ07-2026-09-03-01](verification/EV-REQ05-REQ07-2026-09-03-01.md)). The commit
  `04b4794` referenced in the previous snapshot is no longer in this history; `b54b3f6` is the
  same work.
- `feature/motion-and-heading` at `23b61a0`, merged as `a9ff994`: accelerometer acquisition,
  movement estimation, magnetometer heading and directional guidance
  ([EV-REQ08-REQ11-2026-09-03-01](verification/EV-REQ08-REQ11-2026-09-03-01.md)).

`feature/durable-persistence` has since merged to `main` as `728b7fc`, so REQ-14 and REQ-15 are
recorded as `MERGED` below.

One round is in progress on an unmerged branch and is recorded below as `IN_REVIEW`:

- `feature/weather-context`, from `main@5800a99`: weather integration and the cache/failure policy
  around it ([EV-REQ19-2026-09-05-01](verification/EV-REQ19-2026-09-05-01.md)). **Uncommitted at
  the time of writing**; the commit column must be filled in once a commit exists. This is the
  first round in which the application has issued a network request at all — before it,
  `AndroidManifest.xml` carried no `INTERNET` permission and `data.remote` was never called. Both
  the JVM suite (488 tests) and the instrumented suite (36 tests on a Pixel_8 AVD at API 36) pass,
  and the whole online/offline/stale/retry walkthrough was driven end to end on that emulator
  against the live Open-Meteo service. Every physical (`P`) case remains NOT_RUN.

Merged since:

- `feature/arrival-and-story`, from `main@728b7fc`: arrival detection and the exactly-once visit
  that reveals a story
  ([EV-REQ12-REQ13-2026-09-05-01](verification/EV-REQ12-REQ13-2026-09-05-01.md)). **Uncommitted at
  the time of writing**; the commit column must be filled in once a commit exists. This round ran
  `connectedAndroidTest` for the first time in the project's history — 22 instrumented tests pass
  on a Pixel_8 AVD at API 36 — and drove the arrival-to-story flow end to end on that emulator.
  Every physical (`P`) case remains NOT_RUN: an emulator has no receiver drift, and no run has
  been done on API 26 or API 31+.

Superseded, now merged:

- `feature/durable-persistence`, from `main@a9ff994`: Room wired into the running application,
  schema version 2 with sessions, route points and visits, a durable session store and
  restoration of an interrupted session at launch
  ([EV-REQ14-REQ15-2026-09-04-01](verification/EV-REQ14-REQ15-2026-09-04-01.md)). **Uncommitted at
  the time of writing**; the commit column must be filled in once a commit exists.

`./gradlew connectedAndroidTest` was executed for the first time during the arrival round, on
2026-09-05: 22 instrumented tests pass on a Pixel_8 AVD at API 36, so the shipped migration has
now touched real SQLite and the DAO contracts have been exercised against Room rather than fakes.
That is the `A` level only. Every physical (`P`) acceptance case for REQ-05 through REQ-13 remains
NOT_RUN — no test has run on a physical handset, on API 26 or on API 31+ — and the sensing
thresholds under D-07 remain unvalidated, because an emulator supplies clean fixes with no
receiver drift, no multipath and perfectly reported accuracy.

## 1. Status rules and requirement ledger

Development status: `NOT_STARTED`, `FOUNDATION_ONLY`, `IN_PROGRESS`, `IN_REVIEW`, `MERGED`.

Verification status: `NOT_RUN`, `PARTIAL`, `FAILED`, `BLOCKED`, `VERIFIED`.

Use VERIFIED only after all applicable case IDs, final integrations and quality checks pass on the referenced commit with reviewer evidence. A unit test cannot replace a required physical-device observation. BLOCKED includes a reason, owner and next action. Only the assigned owner/reviewer updates their row; avoid rewriting other members' tracking data.

| Requirement | Iteration | Owner / reviewer | Development | Verification | PR / commit | Evidence / blocker |
|---|---|---|---|---|---|---|
| REQ-01 | I1 | M1 / M2 | MERGED | PARTIAL | `a828d0f`, `204708f` | [EV-REQ01-REQ03-2026-08-31-01](verification/EV-REQ01-REQ03-2026-08-31-01.md); [frontend assessment](verification/EV-FRONTEND-2026-08-31-01.md); U/build passed, A/P NOT_RUN |
| REQ-02 | I1 | M1 / M4 | MERGED | PARTIAL | `8ca81aa`, `204708f` | [EV-REQ02-2026-08-31-01](verification/EV-REQ02-2026-08-31-01.md); coordinator/state machine and foreground pause-resume are integrated; recovery UI uses the process session, but durable restoration and A/P evidence remain |
| REQ-03 | I1 | M1 / M3 | MERGED | PARTIAL | `a828d0f`, `204708f` | [EV-REQ01-REQ03-2026-08-31-01](verification/EV-REQ01-REQ03-2026-08-31-01.md); U/build passed, physical fix matrix NOT_RUN |
| REQ-04 | I1 | M2 / M5 | IN_REVIEW | PARTIAL | `a828d0f`, `142c26f` | Live/reference origins and state-preserving list are integrated; Android/physical acceptance remains NOT_RUN |
| REQ-05 | I1 | M2 / M1 | MERGED | PARTIAL | `b54b3f6`, `35659ef` | [EV-REQ05-REQ07-2026-09-03-01](verification/EV-REQ05-REQ07-2026-09-03-01.md); REQ-05.2 destination selection and gated session start are implemented and unit-tested; AT-05A device half and AT-05B NOT_RUN; durable Save still depends on REQ-17 |
| REQ-06 | I2, final wiring I3 | M2 / M1 | MERGED | PARTIAL | `b54b3f6`, `35659ef` | Same evidence; dashboard is bound to the coordinator with state, duration, distances and Stop/Resume/Discard/retry. AT-06A U part only. AT-06B's weather dependency is now met — REQ-19 adds an optional weather section that reads "unavailable" rather than zero when there is nothing to show, verified offline on an emulator — so the remaining AT-06B gap is font-scale/landscape/TalkBack, still NOT_RUN |
| REQ-07 | I2 | M1 / M3 | MERGED | PARTIAL | `b54b3f6`, `35659ef` | Same evidence; segment-aware accumulation, 5 s point sampling and bounded checkpoints pass AT-07A on synthetic traces. AT-07B NOT_RUN — no measured outdoor walk. Route lives only in memory; durable storage is REQ-15 |
| REQ-08 | I2 | M3 / M1 | MERGED | PARTIAL | `23b61a0`, `a9ff994` | [EV-REQ08-REQ11-2026-09-03-01](verification/EV-REQ08-REQ11-2026-09-03-01.md); one shared accelerometer registration serving motion and heading, session-scoped lifecycle. AT-08A/B asserted through fakes only — `AndroidSensorHub` has never executed on a device and has no automated coverage |
| REQ-09 | I2 | M3 / M1 | MERGED | PARTIAL | `23b61a0`, `a9ff994` | Same evidence; AT-09A passes against the new labelled fixtures, including rotation-without-walking. AT-09B NOT_RUN — no recorded trials, so the 0.35/0.7 m/s^2 thresholds are unvalidated and the physical targets are unsupported |
| REQ-10 | I2 | M3 / M1 | MERGED | PARTIAL | `23b61a0`, `a9ff994` | Same evidence; AT-10A passes (350+15 = true 5; 359/1 stays near north). **Contract change**: `HeadingSource` now reports magnetic heading plus accuracy, with declination applied in `domain.guidance`. AT-10B NOT_RUN — display remapping and real magnetometer accuracy unobserved |
| REQ-11 | I2 | M2 / M3 | MERGED | PARTIAL | `23b61a0`, `a9ff994` | Same evidence; AT-11A passes, and guidance degrades arrow → cardinal → unavailable as heading then position are removed. AT-11B device half NOT_RUN — arrow/text agreement unverified on hardware |
| REQ-12 | I4 | M1 / M3 | IN_REVIEW | PARTIAL | `feature/arrival-and-story` (uncommitted) | [EV-REQ12-REQ13-2026-09-05-01](verification/EV-REQ12-REQ13-2026-09-05-01.md); `domain.arrival` implements the section 4.5 rules as `ArrivalPolicy` + `ArrivalDetector`, fed the raw fix before route filtering so a stationary user at the target still qualifies. AT-12A passes and AT-12B's U half passes. **AT-12B physical half NOT_RUN** — no real-GNSS approach walked, so the 50/25 m and 3-fix/10 s thresholds stay unvalidated. The prototype arrival screen and its debug-only entry point are deleted |
| REQ-13 | I4 | M2 / M4 | IN_REVIEW | PARTIAL | `feature/arrival-and-story` (uncommitted) | Same evidence; visits are written through the existing unique `(session_id, attraction_id)` index with **no schema change**, a duplicate is treated as already-recorded rather than as an error, and the arrival prompt is a dashboard banner consumed once per process while `unread` survives relaunch. AT-13A passes including its emulator half. AT-13B partly passes — injected write failures are JVM-only, and the invented 1879 story strings are deleted per REQ-13.4 |
| REQ-14 | I1 | M4 / M1 | MERGED | PARTIAL | `740221a`, `728b7fc` | [EV-REQ14-REQ15-2026-09-04-01](verification/EV-REQ14-REQ15-2026-09-04-01.md); schema export on with `1.json` and `2.json` committed; `RoomAttractionRepository` is the runtime source and seeds idempotently with provenance. AT-14A and AT-14B both now pass on an emulator (API 36) — see [EV-REQ12-REQ13-2026-09-05-01](verification/EV-REQ12-REQ13-2026-09-05-01.md) for that run; only API 26 / 31+ coverage is outstanding. Unknown-indoor classification deferred to REQ-18 |
| REQ-15 | I1-I2 | M4 / M1 | MERGED | PARTIAL | `8ca81aa`, `204708f`, `740221a`, `728b7fc` | [EV-REQ14-REQ15-2026-09-04-01](verification/EV-REQ14-REQ15-2026-09-04-01.md); `RoomSessionStore` implements the whole contract, route points are appended incrementally and an interrupted session is restored paused at launch. **Contract change**: `findUnfinished` yields `RestoredSession`, and `appendRoute`/`saveDestination` are new. AT-15A/B U parts pass, and the migration half now passes on an emulator. The process-kill path was exercised end to end during the arrival round (force-stop and relaunch restored the session as paused). The visits table is no longer unused — REQ-13 writes to it |
| REQ-16 | I4 | M4 / M2 | FOUNDATION_ONLY | NOT_RUN | `f30c538`, `142c26f` | Journey list/detail/summary UI exists on fixture data; deletion/recovery/offline behavior is missing |
| REQ-17 | I3 | M4 / M2 | IN_REVIEW | PARTIAL | `feature/saved-attractions` (uncommitted) | [EV-REQ17-2026-09-05-01](verification/EV-REQ17-2026-09-05-01.md); schema version 3 adds `saved_attractions`, keyed on the attraction id and holding a content snapshot with **no foreign key** onto `attractions`, so a cache refresh or eviction cannot unsave a place. A process-scoped `SavedAttractionRepository` publishes the saved-id set, which keeps the detail screen, the nearby rows and the saved list in agreement; rapid toggles are serialised per attraction and a failed write reverts and explains. The privacy screen's Clear saved places is wired; Clear journey history stays a prototype for REQ-16. AT-17A passes on an emulator (API 36) end to end, including force-stop and offline relaunch. **AT-17B is partial** — cache-clearing is proved not to unsave, but there is no remote cache to refresh until REQ-18. Physical device NOT_RUN. `loadSavedIds` is exposed for REQ-21 but nothing consumes it |
| REQ-18 | I3 | M5 / M4 | FOUNDATION_ONLY | NOT_RUN | - | - |
| REQ-19 | I3 | M5 / M2 | IN_REVIEW | PARTIAL | `feature/weather-context` (uncommitted) | [EV-REQ19-2026-09-05-01](verification/EV-REQ19-2026-09-05-01.md); **the project's first real network traffic**. Open-Meteo resolves the weather half of D-02 — HTTPS, free, no API key, so nothing has to be kept out of Git. Schema version 4 adds `weather_cache`; `CachedWeatherRepository` is cache-first with per-area coalescing and stale fallback; one `WeatherViewModel` serves the nearby card and a new dashboard section. AT-19A/AT-19B pass on an emulator (API 36) against the live provider. **REQ-19.3 is half done**: weather is displayed but not copied into the session record — deferred to REQ-16, which needs a schema change anyway and is the only thing that would read it. Physical NOT_RUN |
| REQ-20 | I3 | M5 / M4 | IN_PROGRESS | PARTIAL | `feature/weather-context` (uncommitted) | Same evidence. REQ-20.1 and REQ-20.3 are implemented **for the weather path only**: freshness policy, cache-as-fallback labelled stale, explicit provenance, `AndroidConnectivityMonitor` as a hint rather than a gate, per-area request coalescing, atomic replace so a malformed response cannot overwrite good data, and typed reasons separating no-network, timeout, HTTP error, rate limit, malformed and no-cache. AT-20A and AT-20B pass on an emulator. **REQ-20.2 is not implemented** — there is no automatic retry or backoff at all, only a user tap. Nothing applies to the attraction path, which has no remote source until REQ-18 |
| REQ-21 | I3 | M5 / M3 | NOT_STARTED | NOT_RUN | - | - |
| REQ-22 | I4 | M2 / M5 | NOT_STARTED | NOT_RUN | - | - |
| NFR-01 | All | M1 / M4 | IN_REVIEW | PARTIAL | `a9ff994` + branch | AT-Q01A holds on 2026-09-05: `test assembleDebug lint connectedDebugAndroidTest` all succeed — 488 JVM tests and 36 instrumented tests with 0 failures, Lint 0 errors and 70 warnings, unchanged from the baseline. AT-Q01B (API 26/31+/target matrix) NOT_RUN; only API 36 has ever been exercised |
| NFR-02 | All | M1 / M3 | IN_REVIEW | PARTIAL | `a9ff994` + branch | One coordinator owns location, motion and heading; `domain.*` is free of Android imports. Room work introduced the first real off-main-thread repositories: `RoomAttractionRepository` and `RoomSessionStore` query on `AppContainer.getIoExecutor()` and answer on the main executor, and `Transactions` keeps the store itself free of `RoomDatabase`. AT-Q02B and all Android lifecycle/navigation evidence remain NOT_RUN |
| NFR-03 | All | M2 / M5 | IN_REVIEW | NOT_RUN | `142c26f` + integration | Screen system/design tokens, state-backed Session Recovery and Privacy & Data pages exist; TalkBack, 200% font, landscape and task usability NOT_RUN |
| NFR-04 | Before real data, I5 | M4 / M5 | IN_PROGRESS | NOT_RUN | `a828d0f`, `204708f` + branch | Foreground-only policy and privacy/data presentation exist. Route points and visits now cascade from their session, so discarding one removes exactly its own records; the Privacy & Data clear actions are still presentation only, and backup exclusion and retention remain open. **NFR-04.3 is now closed**: `ApiClientFactory` used OkHttp logging at `BASIC` on the false premise that omitting headers protected the key — `BASIC` logs the whole query string, and the weather request carries coordinates. It is replaced by an interceptor logging method, host, path and status only, the `logging-interceptor` dependency is removed, and a full logcat scan across the REQ-19 walkthrough matched no coordinate or query parameter |
| NFR-05 | I2-I5 | M3 / M1 | NOT_STARTED | NOT_RUN | - | Now in scope: sensors are registered from `04b4794` onwards, so AT-Q05A (no source left registered after Stop; battery and memory over a 20-minute trial) needs a device run before I5 |
| NFR-06 | All | M1 / M2 | IN_PROGRESS | NOT_RUN | `142c26f`, `204708f` | Traceability documents and repository guidance exist; official rubric/reviewer evidence remain missing |

## 2. Test levels, commands and environments

Test levels:

- U: JVM tests with fake clocks, immutable fixtures and controlled callbacks.
- R: repository/adapter contract tests, fake DAOs or a local mock HTTP service. These do not prove real provider access.
- A: Android instrumented/emulator tests for Room, navigation, permissions and lifecycle.
- P: physical-device/manual evidence for actual location, motion, heading and resource behavior.

Run from the repository root:

```bash
./gradlew test assembleDebug lint
./gradlew testDebugUnitTest --tests "com.comp90018.storystep.domain.geo.GeoCalculatorTest"
./gradlew connectedAndroidTest
```

The third command requires a configured emulator or device. Use a proposed class name only after it actually exists. If Gradle configuration cache causes a reproducible configuration problem, retry the affected command with `--no-configuration-cache` and record both results; do not erase user data to make tests pass.

Automated evidence is normally available under `app/build/reports/tests/`, `app/build/test-results/`, `app/build/reports/androidTests/` and `app/build/reports/lint-results-debug.html`. These outputs may be regenerated/ignored; attach a sanitized copy or stable CI artifact to the issue rather than claiming an ephemeral path is permanent evidence.

Minimum environment coverage:

| Environment | Required purpose |
|---|---|
| API 26 emulator/device | Minimum-SDK launch, navigation and compatible API paths |
| API 31+ emulator/device | Approximate/precise, one-time permissions and settings downgrade |
| API 37 emulator if available | Configured target behavior, edge-to-edge and regression |
| Two physical Android devices | Outdoor location, accelerometer, magnetometer, rotation and lifecycle; record actual model/API/sensor availability |
| Controlled missing-capability sources | No accelerometer/magnetometer, stale fix and unreliable heading even if both phones have sensors |
| Controlled network | Success, timeout, throttling, malformed response, warm offline and empty offline |

An unavailable device/API image is a recorded test gap. Missing hardware on one physical phone is a valid fallback test, but another device must demonstrate the sensing capability itself. Do not substitute simulator events for all physical tests.

### Initial physical-evaluation targets

These are proposed team acceptance targets for D-07, not guaranteed sensor accuracy or official marking criteria. Approve or replace them before running final trials; record failures and improve the implementation rather than silently relaxing targets after seeing the results. Use both physical devices and record environmental limitations.

| Capability | Repeatable procedure | Proposed target |
|---|---|---|
| Motion | Three 60 s stationary trials and three 60 s walking trials per device; separate phone-rotation-only trial | After the initial window, >= 80% correctly labeled seconds in each stationary/walking trial; state settles within 5 s of transition; <= 10 s false MOVING in rotation-only trial |
| Heading | Known independently marked bearings, eight directions, three slow rotations outdoors away from interference | For samples reported reliable: median absolute circular error <= 25 degrees and settles within 3 s; unavailable/unreliable samples reported separately, not counted as accurate |
| Route distance | Repeat an independently measured 200-500 m route and a 2-minute stationary trial | Walking total within 25% of reference; stationary inflation <= 20 m; no distance counted across paused segments |
| Arrival | Five qualifying approaches and five outside/insufficient-dwell controls per device using approved thresholds | At least 4/5 qualifying approaches detected; 0/5 control false triggers; no duplicate visit within any session |

State fixture expectations separately from physical targets. An indoor signal-loss run is a fallback test, not evidence that outdoor distance/arrival targets passed.

## 3. Shared deterministic fixtures

Create these during SET-01 or the first owning requirement. Fixture names are specifications, not existing files. Keep them in test/debug sources, clearly separated from real data, and include no personal routes or credentials.

| Fixture | Definition | Main use |
|---|---|---|
| FX-NEARBY | Valid places at 100 m, 500 m, exactly 3,000 m and just outside radius, with a stable-ID tie | Radius, distance sort and ordering |
| FX-LOCATION | Valid sample; NaN/infinite lat/lon; negative/non-finite accuracy; duplicate/out-of-order timestamp; age just below/at/above cutoff | Validation and freshness |
| FX-ROUTE | Two known straight segments separated by pause; tiny stationary drift; an impossible jump; coarse-only segment | Accumulation and missing-path behavior |
| FX-MOTION | Timestamped stationary, walking, phone rotation without walking, noisy and insufficient-window signals; documented expected labels | Filtering/classification |
| FX-HEADING | Magnetic 350 + declination 15; 359/1 wraparound; unreliable/missing sensor; all screen rotations | True heading and arrow direction |
| FX-ARRIVAL | Target 8 m away, accuracy 5 m, three distinct samples at t=0/5/10 s; plus lone inside fix, outside/poor fix and >15 s gap | Arrival dwell and resets |
| FX-CONTEXT | Two attractions at equal distance: indoor and outdoor, neither saved/visited; rain versus neutral dry weather; then saved/visited toggles | Ranking and reasons |
| FX-HTTP | Valid, empty, mixed-validity, all-invalid and malformed JSON; 401/403, 429, 500; timeout; slow old response | Mapping/cache/error handling |
| FX-RECOVERY | Finished session; unfinished checkpoint; duplicate visit; failing checkpoint/finish; old-schema DB with history/saves | Durability/migrations |
| FX-SECRETS | Clearly fake key marker and synthetic location marker in URL/header/logging paths | Logging/privacy checks |

Distances in fixtures must be independently computed or checked against known simple cases; avoid asserting a calculator against itself. Clock-controlled tests must not rely on `sleep()` to simulate dwell or expiry.

What exists as of 2026-09-04, and what is still only a specification:

| Fixture | State |
|---|---|
| FX-MOTION | Partly real. `app/src/test/.../domain/motion/MotionFixtures.java` provides seeded still, walking, rotation-without-walking and borderline traces with documented expected labels. The insufficient-window case is asserted in `MovementClassifierTest` rather than generated as a trace. Synthetic only: it proves the classifier's specified behaviour, never anything about a real device. |
| FX-ROUTE | Not a shared module. `RouteAccumulatorTest` builds its own traces from known latitude steps, with expected distances derived from the geometry rather than from `GeoCalculator`. `RouteAccumulatorRestoreTest` adds the reload-after-interruption case. The coarse-only segment case is not covered. |
| FX-HEADING | Not a shared module. The 350+15 and 359/1 cases live inline in `TrueHeadingTest` and `CircularSmootherTest`; screen-rotation variants are not covered anywhere, because they need the Android sensor stack. |
| FX-LOCATION | Partly real, through `LocationSampleFilterTest` and `GeoPointTest`. |
| FX-RECOVERY | Partly real, JVM only. `RoomSessionStoreTest` and `ExplorationCoordinatorDurabilityTest` cover a finished session, an unfinished checkpoint, a failing write and restart-after-interruption using fake DAOs. The duplicate visit and the old-schema database exist only in `SessionStorageDaoTest` and `StoryStepMigrationTest`, which have never been executed. |
| Everything else | Specification only. |

Initial scoring fixture under section 4.5/REQ-21: for two equally distant attractions at distance component 0.8, unavailable time context (neutral 0.5), known unsaved 0 and unvisited 1, rain gives indoor score 0.76 and outdoor score 0.51. Neutral weather gives each 0.635 before tie-breaks. Assert these values within a small numeric tolerance when those weights are approved; test the known-time preference separately and revise the documented fixture if D-07 changes it.

## 4. Functional acceptance cases

Each case records its actual result in an evidence entry. The following table specifies the required setup/actions and observable oracle, not a result already obtained.

| Case | Level | Setup and actions | Expected result |
|---|---|---|---|
| AT-01A | A | Fresh install/test profile; open nearby/details/history; then tap Start and grant precise while-in-use permission | No permission prompt or registration before Start; one contextual request; session begins only after permission and successful session creation |
| AT-01B | U+A | Deny; deny without further prompt; grant approximate/one-time; revoke/downgrade in Settings; return and explicitly resume | No prompt loop/crash; cached browsing works; coarse mode is labeled and does not auto-arrive; expired/revoked access stops collection and offers a safe action |
| AT-02A | U+A | Start twice rapidly; navigate/rotate; Stop twice; feed a queued location callback | One session ID and subscription; rotation retains state; Stop is idempotent; queued data changes no stopped totals |
| AT-02B | U+A+P | Active session -> Home -> return; repeat with screen lock and permission/provider loss | Sources unregister on loss of allowed foreground state; active duration freezes; return shows Resume; no samples or route bridge across pause |
| AT-03A | U | Feed every FX-LOCATION variant with a controlled clock | Valid in-order samples accepted; NaN/infinity/bad accuracy rejected; duplicate/old/stale samples do not update live route/arrival; no fabricated zero coordinate |
| AT-03B | A+P | Start outdoors; receive fix; disable location provider, wait beyond cutoff, restore and resume | Position drives the UI; freshness/unavailable state appears; recovery is user-controlled; record device accuracy and timestamps |
| AT-04A | U+R | Query FX-NEARBY; change origin while old request is pending; finish responses out of order | Inclusive-radius filtering and stable nearest-first order; new origin distances remain; old response cannot overwrite current state |
| AT-04B | A | Browse seed/cached content before Start; then no-fix, empty, refresh-error and rotation paths | Reference/demo/age labels are accurate; useful content persists during errors; retry/back available; no fake user-relative distance |
| AT-05A | U+A | Open a valid detail; select destination while idle; start; rotate; switch target during active session | Correct ID/content displayed; idle selection does not bypass permission; selected target survives rotation; session ID stays stable after target change |
| AT-05B | U+A | Open missing ID; make detail retrieval fail; retry; Back to nearby | Readable error and safe retry/back; no crash or automatic sensor start; no stale target details under a different ID |
| AT-06A | U+A | Fake clock: active 20 s, paused 30 s, resumed 10 s; inspect dashboard; stop | Active duration is 30 s, not 60 s; labels/actions match state; no duplicate timers/session; Stop immediately stops collection |
| AT-06B | A | Remove heading/motion/weather independently; deny a final save; use 200% font scale where supported | Unavailable sections explicit, not fake zeros; core controls reachable; save failure can retry while tracking remains stopped; real weather integration checked after I3 |
| AT-07A | U | Feed FX-ROUTE accepted segments and known distances; pause/resume between them | Sum matches independent expected segment lengths within 1 m for the synthetic fixture; pause gap contributes zero and starts a new segment |
| AT-07B | U+A+P | Stationary drift, impossible jump, stale/coarse fixes and unknown motion; then controlled outdoor walk | Rejected samples add zero; missing motion permits GPS-only fallback; physical measured distance/error and any tuning are recorded, not inferred from unit tests |
| AT-08A | U+A | Start/stop repeatedly; enable motion and heading consumers together; inject queued callbacks | Registration bounded to one per shared source; values copied safely; callbacks after stop ignored; buffers remain bounded |
| AT-08B | U+P | Run without accelerometer via controlled adapter; then observe a physical available sensor | Missing capability shows UNAVAILABLE with usable location flow; physical sample timestamps/units recorded; sensor stops when session pauses |
| AT-09A | U | Replay FX-MOTION with fixed configuration; reset between recordings | Documented stationary/walking labels and dwell/hysteresis expectations pass; duration weighting uses timestamps; startup/gap is UNKNOWN |
| AT-09B | P | Record held-still, walking and phone-rotation trials on both phones; inspect summary | Meet the approved physical-evaluation targets; report classifications and false positives/negatives against labeled intervals; rotation alone is not silently advertised as confirmed walking |
| AT-10A | U | Feed FX-HEADING and declination fixtures | True 5 degrees for 350+15; 359/1 smoothing stays near north; normalized range correct; invalid inputs never create a reliable heading |
| AT-10B | A+P | Turn device through cardinal headings in portrait/landscape; disable sensor or force unreliable state | Arrow basis respects screen rotation; missing/low-accuracy feedback appears; actual error/settling meets approved physical targets away from interference |
| AT-11A | U+A | Target true bearing 90, device true heading 0; then heading 180; test wraparound and identical points | First relative turn is clockwise/right, second counterclockwise/left; text and arrow agree; undefined near-zero direction not presented as precise navigation |
| AT-11B | A+P | Remove compass, then location, during guidance | Usable location retains distance/cardinal bearing without compass; stale/no location suppresses misleading live arrow; limitation text remains visible |
| AT-12A | U | Replay FX-ARRIVAL inside sequence t=0/5/10; test exact distance+accuracy threshold and just outside | Qualifies only after at least 3 distinct accepted fixes spanning 10 s; equality handled as specified; outside fails |
| AT-12B | U+P | Lone inside fix, poor fix, coarse permission, repeated timestamp, >15 s gap, target switch, leave/re-enter | Candidate resets/does not qualify as specified; no arrival from noisy one-off input; physical approach observations recorded separately |
| AT-13A | U+R+A | Emit the same arrival repeatedly; reopen/rotate story; leave/re-enter same target in same session | Exactly one persisted visit pair; consumed arrival alert not replayed on rotation; content can be intentionally reopened |
| AT-13B | A | Fail visit write; retry; interrupt after commit but before display; relaunch offline | Failure does not claim a saved visit; retry creates no duplicate; unread story recoverable without reinsertion; attribution/empty-content fallback clear |
| AT-14A | R+A | Seed twice; read nearby/detail; close/reopen DB/app offline | No duplicate seed rows; persisted attributed content readable; no main-thread DB exception; callbacks follow the agreed thread contract |
| AT-14B | A | Build databases from every exported shipped schema; migrate with representative cache and user data | Schema validated and field values preserved; no destructive fallback; refresh/upsert does not cascade-delete independent user records |
| AT-15A | U+R+A | Create/checkpoint/finish; double finish; inject DB failures; relaunch | One durable session; totals stable; finish retry does not duplicate; errors visible; already saved history survives |
| AT-15B | A | Interrupt process after checkpoint; relaunch; separately reboot/change wall clock; Resume/Finish explicitly | Restored paused/interrupted record; zero automatic registrations; accumulated duration preserved without cross-boot monotonic subtraction; normal-operation loss bounded by checkpoint policy |
| AT-16A | A | Create two sessions, visits and multiple route segments; open history offline after relaunch | Correct date order/totals/visit count and interrupted labels; route gaps not bridged; story/details available from preserved content |
| AT-16B | R+A | Cancel deletion, confirm one-session deletion, then explicit clear-all history; relaunch | Cancel changes nothing; only selected session's owned points/visits removed; saves unaffected; no orphan route rows or returning deleted history |
| AT-17A | R+A | Save same attraction repeatedly; rapid save/unsave; open saved/details; relaunch offline | One record per ID; controls reflect persisted final choice across screens; offline content available; errors revert/explain UI state |
| AT-17B | R+A | Refresh/evict the saved attraction's remote cache entry; remove its save; relaunch | Refresh never unsaves it or loses required offline content; explicit unsave removes only save state, not history |
| AT-18A | R+A | Approved provider/config; perform real nearby request and open results; preserve sanitized provenance evidence | Non-fixture data reaches normal repository/UI/cache; attribution and stable IDs present; no secret/route exposed in evidence |
| AT-18B | U+R | Replay FX-HTTP valid, mixed-invalid, all-invalid, malformed and paginated results | Bad records cannot enter domain; required coordinate validation works; valid empty distinguished from corrupt response; bounded pagination/deduplication/cancellation correct |
| AT-19A | U+R+A | Test known units/condition mapping, then fetch real approved weather for the allowed area | Values/units/times correctly mapped; weather shown with source and observation age; no route-history upload |
| AT-19B | U+R+A | Expire cache via fake clock; change area; fail request; simulate missing fields | Old/wrong-area data never labeled current; stale or unknown weather becomes neutral context; session remains usable and retry is available |
| AT-20A | R+A | Populate caches/saves/history; disable network; relaunch and navigate | Labeled cached/stale content, saved places and history usable; no endless spinner; refresh failure does not erase existing content |
| AT-20B | U+R+A | Empty cache offline; 401/403, 429, 500, timeout, slow old request and corrupt refresh | Actionable distinct errors; bounded retries; rate limits honored; no stale overwrite or valid-cache wipe; no silent fixture-as-live substitution |
| AT-21A | U | FX-CONTEXT equal-distance indoor/outdoor pair; alternate rain/neutral weather with fixed clock | Scores match documented approved values; indoor ranks higher in rain; same input gives same output and stable tie-break |
| AT-21B | U | Missing/stale weather, unknown venue/time, saved toggles, prior visit, invalid distance and no candidates | Neutral defaults finite; invalid/out-of-radius candidates filtered; preference/visit effects expected; repository nearest-order contract unaffected |
| AT-22A | U+A | Switch Recommended/Nearby modes; change rain/save/visit context without changing target | Correct labeled ordering/reasons; selected target stable; UI reacts without restarting session or making an HTTP call per sample |
| AT-22B | A | Expire weather and go offline while viewing recommendations; reopen after rotation | Honest stale/neutral reasons, useful cached recommendations and stable mode; no fabricated open-now or fresh-weather claims |

## 5. Quality acceptance cases

| Case | Level | Setup and actions | Expected result |
|---|---|---|---|
| AT-Q01A | U+build | On PR commit run `test assembleDebug lint`; inspect dependency/build diff | Tests/build succeed; no new unexplained Lint errors; pinned constraints/package preserved; exact command/outcome attached |
| AT-Q01B | A | Launch and navigate on API 26, API 31+ and target emulator when available; run identity test | Compatible app identity and runtime behavior; missing environment explicitly recorded, not counted as passed |
| AT-Q02A | U+review | Check domain imports, repository ownership and bounded executors; return a delayed result after a newer query | No Android dependencies in pure domain; no stale overwrite, leaked Activity reference or main-thread I/O path |
| AT-Q02B | U+A | Repeated rotate/back/forward/start/stop, including callbacks after `onDestroyView` and pause | One logical session; no orphan view updates or duplicate sources; destroyed views released and no collected points after pause/stop |
| AT-Q03A | A+manual | Small screen, landscape, dark/light themes, large fonts and TalkBack | Essential controls reachable; meaningful labels/focus; touch targets adequate; no color-only or continuously chattering heading feedback |
| AT-Q03B | manual | A non-owner attempts Start, select, Stop, save and review history; repeat denied/offline/no-sensor paths | Tasks can be completed or safely exited without explanation from the developer; problems recorded with priority and retest result |
| AT-Q04A | R+review | Use FX-SECRETS through URL/header/error logs; review source, fixtures, diff and evidence | Sentinel secret/coordinate absent from logs and committed artifacts; logging policy enforced; real secrets never used for the test |
| AT-Q04B | A+manual | Inspect both backup-rule generations; verify backup/transfer policy on supported test setup; delete test history | Sensitive storage excluded as approved; delete scope correct; actual restore behavior documented or explicitly unverified |
| AT-Q05A | A+P | 20-minute active trial then stopped trial under recorded conditions; count network requests/subscriptions | No source remains registered after Stop; refresh count bounded by policy; no ANR; measured battery/memory noted without unsupported universal claims |
| AT-Q05B | U+A | 60-minute synthetic session, repeated navigation and delayed-network load | Point/buffer growth within policy; no queue leak; received-state-to-render budget measured on reference device; regressions investigated |
| AT-Q06A | review | Sample each ledger row and follow PR -> commit -> tests -> evidence -> reviewer links | No unsupported VERIFIED row; blocked/unexecuted items visible; requirement behavior matches approved decisions |
| AT-Q06B | A+P+review | Execute E2E-01 through E2E-06 on release candidate; review actual supplied rubric and submission materials | Integrated cases pass, evidence sanitized, limitations disclosed; no claim of rubric compliance while D-03 is unresolved |

## 6. Failure-state matrix

This is a cross-cutting review aid; the case IDs above remain the executable specification.

| Failure | Expected user experience | Data/collection rule | Main case |
|---|---|---|---|
| Location denied | Browse cached/reference-area content; explanation and contextual retry | No collection/session falsely started | AT-01B |
| Permission permanently denied | Settings action plus Back, no repeated dialog loop | No registration until explicit successful continuation | AT-01B |
| Approximate-only | Approximate discovery, clear limitation | No precise route/auto-arrival claim | AT-01B |
| Permission/provider removed mid-session | Paused state with reason and Resume path | Sources stopped; no route gap bridge | AT-02B |
| No/stale/inaccurate fix | Waiting/degraded status; cached content remains | No stale live arrival/route update | AT-03B |
| No accelerometer | Motion unavailable; reduced guidance if heading needs it | GPS-only features remain | AT-08B |
| No magnetometer/low heading accuracy | Distance/cardinal fallback and explanation | No reliable-compass claim | AT-11B |
| GPS drift/jump | Approximate distance remains sensible | Invalid increments rejected | AT-07B |
| One noisy near-target fix | No automatic story yet | No persisted false visit | AT-12B |
| Offline with cache | Cached/stale banner and readable local features | No cache erasure | AT-20A |
| Offline without cache | Empty/offline explanation and retry/reference content clearly labeled | No fake live data | AT-20B |
| Auth/configuration failure | Configuration/service unavailable message | No infinite retry or credential dump | AT-20B |
| Rate limit/timeout/server failure | Bounded refresh plus retry/stale fallback | Preserve last useful cache | AT-20B |
| Malformed/all-invalid payload | Service-data error or partial-data metadata | Never wipe useful cache as a valid empty result | AT-18B |
| Weather unavailable/stale | Unavailable/stale weather; neutral ranking | Session continues | AT-19B |
| Rotation/navigation | Same logical state and selected target | No duplicate source or arrival replay | AT-02A |
| Process interruption | Recoverable interrupted/paused session | No automatic tracking or duplicate visits | AT-15B |
| DB write failure | Explicit retry/error, not success | Stop still stops sensors; preserved prior checkpoint | AT-15A |
| Database version upgrade | Existing records remain readable | Tested migration; no destructive fallback | AT-14B |

## 7. End-to-end acceptance scripts

Each run records commit, device, data origin (FAKE or REAL), network mode and approved parameter version. Run deterministic versions first and physical/real-provider versions where required; never relabel one as the other.

### E2E-01 Normal exploration to history

Requirements: REQ-01 through REQ-16, REQ-18/19 as available, relevant NFRs.

1. Start from idle; verify no active sources. Tap Start and grant foreground access.
2. Receive a real or explicitly injected valid fix; nearby content/distances appear.
3. Open details, select a target, return to active exploration and walk/replay a controlled path.
4. Observe motion, approximate distance and heading. Satisfy the approved arrival dwell rule.
5. Read the story, rotate, and re-enter the boundary. Confirm the visit is still unique.
6. Stop; relaunch; open history and the session's route/visit summary.

Pass: exactly one session, one visit per selected target, plausible stored totals, no duplicate story alert, no post-stop collection and history preserved. Run the sensing portion outdoors on both physical devices.

### E2E-02 Context change and offline continuation

Requirements: REQ-17 through REQ-22, REQ-06, REQ-15/16.

1. Populate attraction/weather caches via the real approved services; capture sanitized provenance.
2. Separately run the labeled deterministic rain fixture and compare with neutral weather; verify rank/reasons.
3. Save a place; disable networking and relaunch. Browse saved/content/history.
4. Start/resume exploration with location available; complete and stop while offline.
5. Expire weather through the test clock; confirm stale label and neutral weather scoring; restore network and refresh.

Pass: live retrieval independently demonstrated, deterministic context change proven, offline local functionality intact, no stale weather passed off as current. Natural rain is not required for a repeatable scoring test.

### E2E-03 Permission and capability degradation

Requirements: REQ-01/02/03/08/10/11, NFR-03/04.

1. Deny location, then attempt discovery/details/history; confirm safe limited browsing.
2. Grant approximate access; start; verify coarse labeling and no automatic arrival.
3. Use controlled missing-accelerometer and missing-magnetometer configurations independently.
4. Restore precise/location capability through a user action, then remove it during an active session.

Pass: no crash, no prompt loop, no false sensing precision, predictable paused/fallback states and accessible actions. Record hardware-injected versus actual permission behavior separately.

### E2E-04 Lifecycle and recovery

Requirements: REQ-02/06/07/13/15, NFR-02/04.

1. Start and checkpoint; navigate and rotate repeatedly.
2. Home/lock the app; verify sources stop and duration pauses; return without tapping Resume.
3. Resume explicitly and confirm a new route segment without a distance bridge.
4. Interrupt the process after another checkpoint; relaunch and inspect recovery.
5. Finish twice, then reopen history and unread/read stories.

Pass: no automatic collection on return/relaunch, consistent session identity, checkpointed totals retained, unique completion/visits and no consumed-alert replay. Also test reboot or a wall-clock adjustment on a test device without personal data.

### E2E-05 Persistence, migration and privacy

Requirements: REQ-14 through REQ-17, NFR-04.

1. Create synthetic saves, routes and visits under a previously shipped schema.
2. Upgrade without clearing data; verify every record and cached snapshot.
3. Refresh/evict an attraction, then confirm saves/history survive.
4. Cancel then confirm deletion of one session; verify only its owned records disappear after relaunch.
5. Check the approved backup/transfer exclusions and logging with synthetic sentinel data.

Pass: no data loss on migration/cache refresh, correct deletion ownership and no sensitive leakage in captured evidence. Tests operate on disposable profiles, not a member's personal routes.

### E2E-06 Release demonstration and bounded behavior

Requirements: all NFRs and previously accepted feature requirements.

1. Run build/JVM/Lint and applicable Android tests on the exact candidate commit.
2. Complete the layout/accessibility matrix and non-owner usability tasks.
3. Run resource trials and the long synthetic session; compare with approved limits.
4. Rehearse E2E-01 and E2E-02 without developer-only steps hidden from the audience.
5. Audit the ledger, actual rubric mapping, setup instructions, attributions, AI-assistance disclosure and known limitations.

Pass: all required evidence is reproducible and truthful; no unresolved blocker for privacy, data loss or the central flow. Optional polish cannot compensate for an unverified core requirement.

## 8. Evidence, defect and iteration templates

Store small sanitized Markdown evidence under `docs/verification/` when implementation begins, or attach it to the issue/PR. Do not commit APKs, huge recordings, credentials or precise personal routes. A shared artifact needs the team's approved access settings.

### Evidence entry

```text
Evidence ID: EV-<requirement>-<date>-<sequence>
Requirement/subtask IDs:
Acceptance case IDs:
Commit / branch / PR:
Tester / independent reviewer:
Date / device model / API level / available sensors:
Build variant / provider / FAKE or REAL / network condition:
Approved decision/parameter version:
Preconditions and fixture IDs:
Steps performed:
Expected result:
Actual result (measurements, row counts, relevant states):
Command and exit result, if automated:
Sanitized screenshot/log/report link:
Outcome: PASS / FAIL / BLOCKED / NOT_RUN
Defect IDs or limitations:
Reviewer confirmation:
```

### Defect entry

```text
Defect ID and title:
Affected requirement / acceptance case:
Severity: blocking privacy/data-loss/core-flow / other required behavior / polish
Commit, device and fixture:
Reproduction steps:
Expected versus actual:
Owner / reviewer:
Regression test to add:
Fix PR / retest evidence:
Status: OPEN / FIXED_PENDING_RETEST / CLOSED
```

### Iteration exit record

```text
Iteration / integration commit:
Accepted requirements and evidence:
Merged but not yet verified requirements:
Failed/blocked cases and owners:
Open D-xx decisions:
Build / unit / Android / physical-device results:
Demo scenario and actual outcome:
Scope changes approved (if any):
Next ready issues / capacity adjustment:
Team reviewers and date:
```

## 9. Release gate

- [ ] D-01 through D-07 resolved as required; actual rubric reviewed and mapped, not assumed.
- [ ] Every required ledger row VERIFIED on an appropriate recent commit; regressions after verification rerun.
- [ ] E2E-01 through E2E-06 have human-reviewed results and sanitized evidence.
- [ ] Real attraction/weather calls shown separately from deterministic fixtures.
- [ ] Real location/motion/heading evidence from the planned physical-device matrix.
- [ ] No unresolved blocking privacy, data-loss, background-collection or core-flow defect.
- [ ] Other required-behavior defects resolved or explicitly assessed by the team without falsely marking failing requirements verified.
- [ ] Build, JVM, instrumented and Lint results recorded; missing environments disclosed.
- [ ] Setup instructions, attribution, decisions, architecture and demo script up to date.
- [ ] Team review/signoff recorded; submission/publishing actions remain separately authorized.
