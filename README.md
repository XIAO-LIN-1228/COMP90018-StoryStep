# WalkWise

WalkWise is an Android mobile application developed for the University of Melbourne subject **COMP90018 – Mobile Computing Systems Programming**.

The application aims to help pedestrians understand the walking conditions of campus routes. Instead of providing conventional turn-by-turn navigation, WalkWise collects mobile sensor data and contextual information to estimate factors such as surface roughness, ambient brightness, and overall walking comfort.

> Project status: Initial planning and prototype development.

## Project Overview

Existing map applications generally focus on distance and travel time. They provide limited information about the physical experience of walking along a route.

WalkWise explores a different question:

> What is the walking experience of this route likely to be?

The application will use multiple smartphone sensors, location information, online contextual data, and community-contributed observations to generate a simple walkability assessment.

The initial project scope focuses on the University of Melbourne campus and nearby pedestrian areas.

## Target Users

WalkWise is intended for:

- University students and staff walking around campus
- People unfamiliar with local walking conditions
- Pedestrians who prefer brighter or smoother routes
- Users who want to record and review their walking experiences

WalkWise is not intended to replace emergency services, professional accessibility assessments, or certified navigation systems.

## Planned Core Features

### 1. Walking Session Recording

Users will be able to start and stop a walking session.

During a session, the application is expected to record:

- Session start and end time
- Approximate route and distance
- Walking duration
- Motion and surface-related sensor readings
- Ambient brightness readings
- Contextual weather information, where available

### 2. Surface Roughness Estimation

The application will use accelerometer measurements to estimate relative changes in movement and surface roughness.

The calculated result will be presented as a simplified indicator rather than a scientifically certified measurement.

Possible classifications include:

- Smooth
- Moderate
- Rough

### 3. Ambient Brightness Detection

The device light sensor will be used to measure ambient light levels during a walking session.

This information may contribute to a route brightness indicator. Light readings depend on device position, sensor availability, weather, and whether the sensor is obstructed.

### 4. Location and Route Tracking

Location services will be used to associate observations with approximate geographic positions.

The MVP will focus on foreground tracking while the user is actively recording a walk. Continuous background tracking is outside the initial project scope.

### 5. Walkability Score

WalkWise will combine several available measurements into an understandable walkability result.

Potential factors include:

- Surface smoothness
- Ambient brightness
- Route duration and distance
- Weather conditions
- Community observations

The exact scoring model will be documented and refined during implementation and testing. The score will represent an application-level estimate, not an official safety or accessibility rating.

### 6. Session History

Users will be able to review previously recorded walking sessions, including their basic statistics and calculated indicators.

### 7. Community Data

Subject to implementation feasibility, anonymised route observations may be uploaded to a cloud service and aggregated with observations submitted by other users.

Personal identifying information and precise location data will not be made publicly visible by default.

### 8. Online Contextual Data

The application is expected to retrieve relevant online information, such as current weather conditions, to provide context for each walking session.

## Sensors and Device Capabilities

The project plans to use the following Android capabilities:

| Capability | Planned purpose |
|---|---|
| Accelerometer | Estimate motion intensity and relative surface roughness |
| Light sensor | Measure ambient brightness |
| Location/GPS | Associate observations with approximate routes and positions |
| Gyroscope, if required | Improve motion interpretation and reduce false readings |
| Internet connection | Retrieve contextual data and synchronise cloud observations |

Sensor availability varies between physical Android devices. The application should detect unavailable sensors and degrade gracefully instead of crashing.

The Android Emulator may be used for interface and basic integration testing. Sensor-dependent behaviour will also require testing on physical Android devices.

## Technology Stack

### Current project configuration

- Platform: Android
- Application language: Java
- User interface: XML layouts
- Build configuration: Kotlin DSL
- Build system: Gradle
- Minimum SDK: API 26 — Android 8.0
- Compile SDK: API 37
- Target SDK: API 37
- Android Gradle Plugin: 9.3.1
- Gradle: 9.5.0
- Java compatibility: Java 11
- IDE: Android Studio
- Version control: Git and GitHub

### Planned Android components

- Android SensorManager
- Android location APIs or Google Play Services Location
- AndroidX AppCompat
- Material Components
- ViewModel and LiveData
- Room for local persistence
- Retrofit and OkHttp for web requests
- WorkManager for suitable deferred synchronisation tasks

### Planned cloud services

A final cloud implementation has not yet been confirmed. Firebase Authentication and Cloud Firestore are being considered for user identity and shared observation storage.

API keys, service credentials, and other secrets must not be committed directly to this repository.

## Proposed Architecture

The application is expected to follow a layered architecture:

```text
User Interface
      |
ViewModel / Presentation Logic
      |
Repository Layer
   /        \
Local Data   Remote Data
(Room)       (Web API / Cloud)
      |
Sensor and Location Data Sources
```

The final structure may be adjusted as the requirements and prototype evolve.

## Project Scope

### Included in the MVP

- Native Android application
- Java application code and XML layouts
- Start and stop a walking session
- Foreground sensor collection
- Accelerometer-based roughness estimation
- Ambient light measurements
- Approximate location tracking
- Local session history
- Walkability result display
- At least one online data source or cloud capability
- Sensor availability and permission handling

### Outside the initial scope

- iOS support
- Turn-by-turn navigation
- Continuous background location tracking
- Emergency alerts or emergency-service integration
- Medical advice
- Guaranteed personal safety assessments
- Certified accessibility ratings
- Real-time social networking or chat
- Machine-learning models requiring large training datasets
- City-wide production deployment

These boundaries are intended to keep the project technically achievable within the subject timeline.

## Privacy and Responsible Use

WalkWise deals with potentially sensitive sensor and location information.

The project will follow these principles:

- Request permissions only when required
- Explain why each permission is needed
- Collect location data only during user-initiated recording
- Avoid committing credentials or private configuration files
- Minimise personally identifying information
- Provide reasonable behaviour when permissions are denied
- Avoid presenting calculated scores as guarantees of safety
- Document any cloud data collection and retention decisions

## Permissions

Depending on the final implementation, the application may request:

- Precise or approximate location
- Internet access
- Notification permission where applicable

Foreground location permission does not imply unrestricted background location access. Background location is not part of the initial MVP.

## Repository Structure

```text
COMP90018-WalkWise/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/comp90018/walkwise/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── README.md
```

This structure will expand as features are implemented.

## Getting Started

### Prerequisites

Install:

- Android Studio
- Android SDK
- A compatible JDK
- Git

An ARM64 Android emulator is recommended for Apple Silicon Macs. A physical Android phone will be needed for reliable testing of real sensor behaviour.

### Clone the repository

```bash
git clone https://github.com/XIAO-LIN-1228/COMP90018-WalkWise.git
cd COMP90018-WalkWise
```

Because this is a private repository, access must first be granted by the repository owner.

### Open the project

1. Open Android Studio.
2. Select **Open**.
3. Choose the cloned `COMP90018-WalkWise` directory.
4. Allow Gradle synchronisation to complete.
5. Select an Android emulator or connected physical device.
6. Run the `app` configuration.

### Build from the command line

On macOS or Linux:

```bash
./gradlew assembleDebug
```

Run local unit tests with:

```bash
./gradlew test
```

Run connected Android tests with an emulator or device available:

```bash
./gradlew connectedAndroidTest
```

## Development Workflow

The team should avoid implementing substantial features directly on `main`.

Create a branch for each feature or task:

```bash
git checkout main
git pull origin main
git checkout -b feature/sensor-recording
```

After completing and testing the work:

```bash
git add .
git commit -m "Implement walking sensor recording"
git push -u origin feature/sensor-recording
```

The branch should then be reviewed and merged through a GitHub Pull Request.

Suggested branch prefixes include:

- `feature/`
- `fix/`
- `docs/`
- `test/`
- `refactor/`

Commit messages should describe concrete work rather than use vague descriptions such as `update` or `changes`.

## Testing Strategy

Testing is expected to include:

- Unit tests for calculation and scoring logic
- Repository and data-mapping tests where practical
- UI or instrumentation tests for important workflows
- Permission-denial testing
- Sensor-unavailable testing
- Lifecycle testing during screen rotation and activity recreation
- Offline and network-failure testing
- Emulator testing
- Physical-device sensor testing

Important sensor-processing logic should be kept separate from Android UI code where possible so it can be tested independently.

## Development Roadmap

### Phase 1 — Foundation

- Confirm requirements and project boundaries
- Document team roles
- Establish Git workflow
- Define application architecture
- Create initial interface wireframes

### Phase 2 — Local MVP

- Implement walking-session controls
- Integrate accelerometer readings
- Integrate ambient light readings
- Add foreground location collection
- Store session data locally
- Display session results

### Phase 3 — Online Integration

- Integrate weather or other contextual API
- Add cloud data storage
- Implement community observation aggregation
- Handle offline and synchronisation failures

### Phase 4 — Quality and Evaluation

- Refine the scoring model
- Improve usability and accessibility
- Test on multiple devices
- Add automated tests
- Fix lifecycle, permission, and network issues
- Prepare the demonstration video and final report

## Team

| Name | Student ID | Role / Planned contribution |
|---|---|---|
| To be added | To be added | To be confirmed |
| To be added | To be added | To be confirmed |
| To be added | To be added | To be confirmed |

The final contribution record will reflect actual work completed by each team member.

## AI-Assisted Development

AI tools may be used during planning, programming, debugging, documentation, or editing where permitted by the subject rules.

The team remains responsible for:

- Understanding all submitted code
- Reviewing and testing AI-generated output
- Confirming technical correctness
- Protecting private information and credentials
- Recording how AI tools were used
- Acknowledging AI assistance in the assessment report
- Ensuring the final submission is the team’s own authorised work

Examples of AI use and the tools used will be documented for the final assessment submission.

## Academic Context

This repository contains work for a University of Melbourne assessment. All submitted work must comply with the subject requirements and the University’s academic integrity policies.

The repository is private and should not be copied, redistributed, or submitted by anyone outside the authorised project group.

## Licence

No open-source licence has currently been granted.

Unless otherwise stated, this repository is intended only for the authorised COMP90018 project team and teaching staff.
