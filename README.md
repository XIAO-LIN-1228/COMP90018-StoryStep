# StoryStep

**StoryStep** is a context-aware Android tourism companion developed for **COMP90018 – Mobile Computing Systems Programming** at the University of Melbourne.

> **Every step reveals a story.**

StoryStep helps tourists discover nearby attractions using location, motion, orientation, weather, and cloud data. As users explore an area, the app provides approximate directional guidance and reveals information about nearby landmarks.

**Project status:** Core flows implemented; recommendation integration and final acceptance in progress.

The app uses real OpenStreetMap attractions, map tiles and Open-Meteo weather, with Room persistence
(schema v9), foreground exploration, arrival stories, saved places and journey history. Contextual
ranking and Recommended/Nearby controls are implemented in the teammate A branches using
the [initial scoring policy](docs/decisions/D-07-recommendation-baseline.md). Profile now reads local
counts and device capabilities; the obsolete memory editor is removed. These implementation claims
are not release acceptance: network retry/backoff, saved-detail cache fallback, backup exclusions,
and physical-device/accessibility/compatibility verification still require completion.

See the [teammate A evidence](docs/verification/EV-REQ21-REQ22-2026-09-20-01.md) for commands,
results and unexecuted checks. Older plan/ledger entries describe earlier commits and must not be
used to infer that an already implemented feature needs rebuilding.

[View UI implementation screenshots](docs/design/implementation/)

## Core Features

### Nearby Attractions

StoryStep will use the user's location and online attraction data to show nearby:

- Historical buildings
- Museums
- Parks
- Public art
- Cultural locations
- University landmarks

### Context-Aware Recommendations

Attraction recommendations may consider:

- Current location
- Distance
- Weather
- Time of day
- Indoor or outdoor classification
- Previous visits and saved preferences

For example, the app may recommend indoor attractions during rainy weather.

### Exploration Sessions

Users will be able to start and stop an exploration session. A session may record:

- Duration
- Distance travelled
- Approximate route
- Attractions visited
- Movement state
- Weather conditions

### Directional Guidance

StoryStep will show the approximate distance and direction to a selected attraction using location and orientation data.

```text
Old Arts Building
Distance: 320 m
Direction: Northeast
           ↗
```

StoryStep provides lightweight directional assistance, not complete turn-by-turn navigation.

### Location-Triggered Stories

When the user approaches an attraction, the app may automatically reveal its information.

```text
You have arrived at the Old Arts Building.
Discover the story of this landmark.
```

### Visit History

Users will be able to review previous exploration sessions and save attractions for later.

## Sensors

StoryStep plans to use three primary sensing capabilities:

| Sensor or capability | Purpose |
|---|---|
| Location / GPS | Find attractions, calculate distance, record routes, and detect arrival |
| Accelerometer | Estimate movement state and assist with device orientation |
| Magnetometer | Calculate compass heading and show attraction direction |

Sensor availability will be checked at runtime. The app must continue functioning gracefully when a sensor is unavailable.

## Internet and Cloud Features

Internet or cloud connectivity may be used to:

- Retrieve attraction information
- Retrieve current weather
- Load attraction images
- Store saved attractions
- Synchronise visit history
- Retrieve community ratings or tips

The app will handle offline states, slow connections, invalid responses, and cloud failures without crashing.

## Technology Stack

### Current configuration

- Android
- Java application code
- XML layouts
- Gradle with Kotlin DSL
- Minimum SDK: API 26
- Compile SDK: API 37
- Target SDK: API 37
- Java compatibility: Java 11
- Android Studio
- Git and GitHub

### Planned components

- Android SensorManager
- Android location APIs
- AndroidX AppCompat
- Material Components
- ViewModel and LiveData
- Room
- Retrofit and OkHttp
- Firebase or another suitable cloud service

The final libraries and cloud services will be confirmed during implementation.

## Proposed Architecture

```text
User Interface
      |
ViewModel
      |
Repository
   /        \
Local Data   Remote Data
      |
Sensor and Location Sources
```

Sensor calculations, distance calculations, and recommendation logic should remain separate from Activity classes so they can be tested independently.

## MVP Scope

The initial version will focus on:

- Nearby attraction discovery
- User-initiated exploration sessions
- Foreground location tracking
- Movement-state detection
- Compass-based attraction direction
- Distance and bearing calculation
- Location-triggered attraction information
- Weather integration
- Local visit history
- Meaningful Internet or cloud connectivity

## Out of Scope

The initial version will not include:

- Turn-by-turn navigation
- Background location tracking
- Hotel or flight booking
- Online payments
- Real-time chat
- Augmented-reality navigation
- Emergency-service integration
- Large-scale social networking
- iOS support

## Privacy

StoryStep may process sensitive location and movement information.

The application will:

- Request permissions only when required
- Start tracking only after user confirmation
- Stop tracking when the session ends
- Avoid exposing precise personal routes publicly
- Handle permission denial safely
- Keep API keys and credentials out of Git
- Avoid making guarantees about safety or route accuracy

## Getting Started

### Clone the repository

```bash
git clone https://github.com/XIAO-LIN-1228/COMP90018-StoryStep.git
cd COMP90018-StoryStep
```

This is a private repository. Access must be granted by the repository owner.

### Open and run

1. Open the project in Android Studio.
2. Allow Gradle synchronisation to complete.
3. Select an emulator or connected Android device.
4. Run the `app` configuration.

A physical Android device is recommended for testing real location, accelerometer, magnetometer, and compass behaviour.

### Build and test

```bash
./gradlew assembleDebug
./gradlew test
```

## Development Workflow

### Requirement-driven implementation

- [Detailed development plan](docs/development_plan.md): functional requirements, implementation subtasks, dependencies, ownership and iteration gates.
- [Verification checklist](docs/verification_checklist.md): acceptance cases, requirement status and evidence records.

These documents refine the scope above; they do not indicate that the planned features are complete. Follow `CLAUDE.md` for the repository's engineering constraints.

### Branches and review

Create a separate branch for each task:

```bash
git checkout main
git pull origin main
git checkout -b feature/nearby-attractions
```

After completing and testing the work:

```bash
git add .
git commit -m "Implement nearby attraction discovery"
git push -u origin feature/nearby-attractions
```

Substantial changes should be reviewed through a Pull Request before being merged into `main`.
