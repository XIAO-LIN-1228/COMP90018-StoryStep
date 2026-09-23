# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

StoryStep is a context-aware Android tourism companion for COMP90018 (University of Melbourne, Mobile Computing Systems Programming). It helps users discover nearby attractions and reveals location-triggered stories as they explore an area, using location, motion, orientation, weather, and cloud data — not turn-by-turn navigation.

Current state: the Java/XML app has real nearby discovery and map tiles, foreground location,
movement and compass guidance, durable start/pause/resume/finish/discard, quality-aware arrival,
story snapshots, saved places and journey history. Room is at schema version 10, including
photographs and named finished journeys, and the steps and metres climbed a walk counted. OpenStreetMap Overpass supplies attractions; Wikidata
and Wikipedia can enrich images/descriptions. Open-Meteo supplies cached weather. Both network
repositories support area caches, request coalescing and labelled stale fallback.

On the teammate A branch, `domain.recommendation.RecommendationEngine` implements the initial
REQ-21 weights and deterministic reason codes. Nearby exposes Recommended/Nearby, preserves
search/filter state and selection, and reacts to weather, local time, saves and visits. Invalid,
wrong-area and stale weather is neutral. Profile shows real local counts and capability checks;
the obsolete Memory editor is removed and photographs remain in finished-journey details. See
`docs/decisions/D-07-recommendation-baseline.md` and the 2026-09-20 evidence for exact policy and
validation limits. REQ-20.2 retry/backoff, saved-detail cache fallback and backup exclusions remain
teammate B work. Physical-device, accessibility and multi-version acceptance remain outstanding;
implementation and JVM tests do not establish those results.

`README.md` is the requirements/scope document and is the source of truth for
what to build; `docs/development_plan.md` and `docs/verification_checklist.md` track the current
delivery and evidence status.

For requirement-by-requirement implementation, use `docs/development_plan.md` for task IDs,
dependencies and acceptance behavior and `docs/verification_checklist.md` for verification status
and evidence. These are execution documents, not completed-feature claims or permission to expand
the README scope. Read the selected requirement and its shared contracts before coding; keep
unexecuted tests and unresolved decisions explicit.

The on-disk directory and Git repository are named `COMP90018-StoryStep`, matching the current
product identity.

## Confirmed scope

Core features:

- Nearby attraction discovery
- Context-aware attraction recommendations
- User-initiated exploration sessions
- Approximate distance and directional guidance
- Location-triggered attraction stories
- Visit history and saved attractions
- Weather integration
- Meaningful Internet or cloud connectivity

Primary sensing capabilities:

1. **Location/GPS** — nearby attractions, distance calculation, approximate route recording, arrival detection
2. **Accelerometer** — movement-state estimation, device-orientation assistance
3. **Magnetometer** — compass heading, direction to an attraction

Optional capabilities, used when the device has them and silently absent otherwise — a missing one
omits its figure rather than showing a zero:

4. **Step counter** — steps taken during a walk (`ACTIVITY_RECOGNITION` from API 29)
5. **Barometer** — metres climbed during a walk
6. **Camera** — a photograph taken on the walk, written through a `FileProvider` into the walk's
   own photo directory and published with it

Explicitly outside the MVP — do not build these, and flag requests that drift into them:

- Turn-by-turn navigation
- Continuous background location tracking
- Hotel or flight booking
- Online payment
- Real-time chat
- Augmented-reality navigation
- Emergency-service integration
- iOS support

## Commands

```bash
./gradlew assembleDebug           # build debug APK
./gradlew test                    # JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.comp90018.storystep.domain.geo.GeoCalculatorTest"   # single unit test class
./gradlew connectedAndroidTest    # instrumented tests (needs emulator/device)
./gradlew lint                    # Android Lint; report at app/build/reports/lint-results-debug.html
./gradlew clean
```

Gradle's configuration cache is enabled (`gradle.properties`), so build-script changes trigger a full reconfigure; a stale-cache failure is fixed with `--no-configuration-cache` or `clean`. The build resolves its own JDK 25 toolchain via foojay (`gradle/gradle-daemon-jvm.properties`), so no local `JAVA_HOME` is required.

## Package identity

The package root is **`com.comp90018.storystep`** (all lowercase). `namespace`, `applicationId`, every source directory and every `package` declaration agree on it, and `ApplicationIdentityTest` (instrumented) asserts that the applicationId and `ui.MainActivity` still resolve under it. Keep new code under this root; do not reintroduce the capitalised `com.comp90018.StoryStep` form anywhere.

Themes follow the same name: `Theme.StoryStep` / `Base.Theme.StoryStep`.

## Build configuration notes

This project is on AGP 9.3.1 / Gradle 9.5.0, which changed DSL that older Android examples still use — don't "fix" these back to the old forms:

- `compileSdk { version = release(37) }` — block form, not `compileSdk = 37`.
- Release build type uses `optimization { enable = false }`, not `isMinifyEnabled`.
- R8 keep rules live in `app/src/main/keepRules/rules.keep` (AGP merges everything in that directory); there is no `proguard-rules.pro`.
- Room schemas are exported to `app/schemas/` via the `room.schemaLocation` annotation-processor argument in `defaultConfig.javaCompileOptions`, and that directory is also an `androidTest` assets source so `MigrationTestHelper` can read it. Commit every exported JSON; a schema change means a new version plus a `Migration` in `StoryStepMigrations.all()`, never `fallbackToDestructiveMigration`.
- All dependencies and plugin versions go through the version catalog at `gradle/libs.versions.toml` — add a `[versions]`/`[libraries]` entry and reference it as `libs.foo`, never a hardcoded coordinate string in `app/build.gradle.kts`.
- `settings.gradle.kts` uses `FAIL_ON_PROJECT_REPOS`; repositories are declared centrally there, not per-module.

## Technology constraints

Fixed for this project — treat as non-negotiable:

- Android only
- Java for application code
- XML for layouts (not Compose; `activity-ktx` is present only for `EdgeToEdge`)
- Kotlin DSL for Gradle configuration
- minSdk 26, compile/target SDK 37
- Java 11 source and target compatibility

## Intended architecture

Layered: UI (Activities/Fragments + XML) → ViewModel/LiveData → Repository → local (Room) and remote (Retrofit/OkHttp, cloud) data sources, with location and sensor data sources beneath.

The package layout under `com.comp90018.storystep` realises that:

| Package | Holds | Android dependency |
|---|---|---|
| `domain.model` | Attraction, journey and immutable location models | none |
| `domain.geo` | Distance/bearing maths and location-quality policies | none |
| `domain.arrival` | Arrival thresholds and the sustained-stay detector | none |
| `domain.weather` / `domain.attraction` | Area grids, freshness policies and the result types for each cached network path | none |
| `domain.repository` | Attraction/journey/session contracts and `RepositoryCallback` | none |
| `data.local` | Room entities, DAOs, database, migrations, mappers, `Transactions` | Room |
| `data.remote` | Retrofit services, DTOs, mappers, `ApiClientFactory`, and the two narrow `WeatherSource`/`AttractionSource` seams that keep caching policy testable without a socket | Retrofit/OkHttp |
| `data.repository` | Room attraction and session stores; in-memory equivalents kept as test fakes | none |
| `platform.location` | Location contracts, access policy and foreground Android adapter | Android in adapter only |
| `platform.sensor` / `platform.connectivity` | Device-capability interfaces plus their Android adapters | interfaces are framework-free |
| `di` | `AppContainer` — manual wiring, no DI framework | Context only |
| `ui` | `MainActivity`, `ViewModelFactory`, formatting helpers | full |
| `ui.*` feature packages | Fragment + ViewModel/UI-state where behavior is connected; prototype Fragments elsewhere | full |

`domain` must stay free of Android types — that is what keeps it unit-testable. New computation belongs there, not in `ui` or `data`.

Three constraints shape the code:

- **Keep sensor, distance, bearing, and recommendation logic outside Activity classes.** Activities wire up and display; the computation lives in plain classes.
- **Core calculations must be independently JVM-testable** as unit tests under `app/src/test`, with no Android framework dependency. Distance, bearing/heading, movement-state classification, and recommendation ranking are the primary unit-test targets.
- **Degrade gracefully** on missing sensors (any of the six absent), denied permissions, unavailable location, offline state, and network failure. Sensor availability varies per device; the app must show a sensible fallback rather than crash. Location is collected only during a user-initiated foreground exploration session — continuous background tracking is out of scope.

`ui.MainActivity` applies `EdgeToEdge.enable()` and pads the root view by `systemBars` insets; it hosts a `NavHostFragment` driven by `res/navigation/nav_graph.xml`. New screens are Fragments added to that graph and should follow the same inset handling. Safe Args is deliberately not used — arguments are passed as a plain `Bundle`.

## Conventions

- Feature work happens on branches (`feature/`, `fix/`, `docs/`, `test/`, `refactor/`) merged via PR — not directly on `main`.
- Do not commit API keys or service credentials (weather API, cloud/Firebase). `local.properties` is gitignored and is the place for local SDK paths and keys.
- All project documentation and code comments in English.
