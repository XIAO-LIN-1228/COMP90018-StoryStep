# StoryStep Project Plan

## COMP90018 Mobile Computing Systems Programming

**Group number:** [Insert group number]

| Group member | Student number | University email |
|---|---|---|
| [Member 1 full name] | [Student number] | [Email] |
| [Member 2 full name] | [Student number] | [Email] |
| [Member 3 full name] | [Student number] | [Email] |
| [Member 4 full name] | [Student number] | [Email] |
| [Member 5 full name] | [Student number] | [Email] |

## 1. Project overview

StoryStep is a context-aware Android tourism companion that helps people discover nearby attractions and learn about them while exploring. Rather than attempting full navigation, the application combines location, motion, device orientation, weather and online attraction data to offer nearby recommendations, approximate distance and direction, and location-triggered stories. A user deliberately starts and stops each exploration session. During that foreground session, the application can record duration, approximate distance travelled, movement state, weather and visited attractions. Users can later review their visit history and save attractions.

The project will be implemented for Android using Java application code and XML layouts. The planned architecture separates Activities and Fragments from ViewModels, repositories, local and remote data sources, and sensor/location components. Room will provide local persistence, while Retrofit and OkHttp will support weather and attraction services. This separation is important because distance, bearing, movement classification and recommendation ranking can then be tested as ordinary JVM code rather than being coupled to an Android screen.

The minimum viable product includes nearby attraction discovery, a user-controlled foreground exploration session, movement-state estimation, compass-based directional assistance, arrival-triggered stories, current weather, visit history and meaningful network or cloud use. Turn-by-turn navigation, continuous background tracking, booking, payments, real-time chat, augmented-reality navigation, emergency services and iOS support are outside scope. Keeping these boundaries explicit makes the proposal achievable within one semester and keeps the work focused on mobile sensing and context-aware behaviour.

## 2. Potential contribution breakdown

*This section is designed to occupy one page after normal report formatting. Replace the member placeholders with the final names.*

### [Member 1] — Coordination, architecture and location

- Maintain the delivery board, milestone checklist, meeting decisions and risk register.
- Lead the package-name correction and establish the shared application architecture.
- Implement runtime location permission handling and the foreground exploration location source.
- Implement and test geographic distance, route-distance accumulation and arrival detection.
- Integrate the exploration screen with the relevant ViewModel and repository interfaces.
- Review Member 4's session-persistence work and coordinate release integration.

### [Member 2] — User experience and attraction discovery

- Produce user flows, low-fidelity wireframes and accessible XML screen designs.
- Implement nearby-attraction lists, attraction details, loading, empty and error states.
- Implement attraction selection, saved-state feedback and clear session controls.
- Apply Material components, system-bar inset handling and consistent visual resources.
- Conduct usability checks and test layouts on different screen sizes and font scales.
- Review Member 5's recommendation presentation and remote-data error handling.

### [Member 3] — Motion sensing and directional guidance

- Implement accelerometer and magnetometer data sources with lifecycle-safe registration.
- Develop independently testable movement-state and heading calculations.
- Implement bearing-to-attraction, relative direction and compass display behaviour.
- Add smoothing and accuracy handling to reduce unstable direction updates.
- Provide fallbacks for missing sensors, unreliable readings and unavailable orientation.
- Review Member 1's distance and arrival calculations and support physical-device testing.

### [Member 4] — Local data, sessions and history

- Design Room entities, data-access objects and migrations for attractions, saves and sessions.
- Implement start/stop session coordination and store duration, route summary and visits.
- Implement history and saved-attraction screens, including empty and deletion states.
- Add repository-level tests for local persistence and session-state transitions.
- Document local data handling and ensure precise routes are not exposed publicly.
- Review Member 3's lifecycle behaviour and assist with integration/regression testing.

### [Member 5] — Remote data and contextual recommendations

- Evaluate and document suitable attraction and weather data services.
- Implement Retrofit/OkHttp clients, response models and repository integration.
- Implement a deterministic recommendation score using distance, weather, time, venue type and preferences.
- Add caching and graceful handling for offline, slow, invalid and failed responses.
- Prepare test fixtures for recommendation ranking and remote-data mapping.
- Review Member 2's discovery flow and coordinate final technical documentation.

### Shared responsibility

All members will review pull requests, write tests for their own work, participate in physical-device test sessions, update documentation and contribute to the demonstration. Primary ownership identifies who drives a task; it does not prevent pair work or peer review. Each major component has a named reviewer so knowledge is shared and integration risk is reduced. Work will be rebalanced at the end of each milestone using completed deliverables and remaining estimates rather than commit counts alone.

## 3. Implementation plan and rationale

### 3.1 Clear problem, target users and value

The team will define the primary user as a visitor who wants to discover points of interest while walking through a compact area such as the University of Melbourne and its surroundings. The central problem is not route finding; it is timely discovery and interpretation of nearby places without requiring repeated manual searches. StoryStep addresses this problem by combining sensing and contextual information into a simple exploration flow.

The report and final demonstration will use two representative scenarios. In the first, a visitor starts an exploration session on a clear afternoon, receives a nearby outdoor recommendation, follows an approximate direction indicator and unlocks a story on arrival. In the second, rain changes the ranking toward an indoor attraction, while a network failure causes cached content to be shown. These scenarios make the value of each context input visible and provide concrete end-to-end acceptance tests.

### 3.2 Mobile sensing and context awareness

Location is the main sensing capability. During a user-initiated foreground session, the application will request periodic updates, calculate proximity to attractions, accumulate an approximate route distance and detect arrival using a configurable radius. To avoid false triggers, arrival will require more than a single noisy observation, for example consecutive readings within the radius where location accuracy is acceptable. Tracking stops when the user ends the session or when the relevant lifecycle state requires it.

The accelerometer will provide a lightweight movement-state estimate, such as stationary or moving, using a filtered acceleration magnitude over a short time window. The magnetometer, combined with the accelerometer where available, will provide an orientation estimate. The application will compare the user's heading with the calculated bearing to the selected attraction and present an approximate relative direction. Sensor sampling will be chosen to support responsive feedback without unnecessary battery use.

These capabilities are not included merely to increase the sensor count. Location answers which attractions are relevant and whether the user has arrived; movement state provides session context and helps avoid interpreting stationary noise as travel; orientation turns a geographic bearing into guidance the user can understand. Evidence will include unit tests for calculations, logs or screenshots from controlled trials, and physical-device test results. If a sensor is absent or unreliable, the interface will retain distance and text-based cardinal direction where possible and explain which enhancement is unavailable.

### 3.3 Context-aware recommendation design

Recommendations will use a transparent weighted score rather than an opaque or overly ambitious predictive model. Candidate attractions will first be filtered by basic validity and practical distance. The score will then combine normalized distance, weather suitability, time-of-day suitability, indoor/outdoor classification, saved preferences and prior visits. A rainy condition, for example, will improve the relative rank of indoor attractions rather than hiding every outdoor option.

The scoring function will remain in a plain Java class with documented inputs and deterministic output. Unit tests will cover ties, missing context, extreme distances, previously visited attractions and changes in weather. This approach is feasible with a small team and limited training data, while still demonstrating genuine adaptation: the same nearby candidates can be ranked differently when relevant context changes. The attraction screen will give a brief reason such as “nearby and suitable for rainy weather” so the behaviour is understandable.

### 3.4 Meaningful Internet or cloud connectivity

The remote data layer will retrieve current weather and attraction content such as descriptions or images. Before implementation, Member 5 will compare candidate services for coverage, rate limits, licensing, authentication, response stability and suitability for a student project. Service-specific response models will be mapped into application-owned domain models so that the rest of the application does not depend directly on one provider's schema.

Connectivity is meaningful because fresh weather changes recommendations and remote attraction data expands what can be discovered beyond a hard-coded demonstration. At the same time, the application's basic screens must not collapse when the network is unavailable. Room will retain saved attractions, visit history and selected cached content. Repositories will return explicit loading, success and error states. Timeouts, malformed responses, rate limits and offline launch will be tested with controlled fixtures or network interception. Credentials will remain in local configuration and will not be committed to Git.

### 3.5 Location-triggered stories and exploration sessions

The main interaction will be a deliberate sequence: discover an attraction, start a session, select a destination, view approximate distance and direction, and receive a story when the arrival rule is met. Story unlocking will be idempotent within a session so GPS fluctuations do not repeatedly display the same event. A session record will contain start and end times, approximate distance, visits, movement summary and weather context. The system will store only the detail needed for the feature and will avoid publicly exposing precise personal routes.

This design provides an integrated experience rather than a collection of disconnected demonstrations. The same location stream supports discovery, distance, session statistics and arrival; sensor context enriches the session; remote data supplies weather and content; Room preserves the result. The team will verify the flow first with injected test coordinates and then outdoors on at least two physical Android devices.

### 3.6 User interface and interaction quality

The planned screen set is deliberately small: permission/onboarding, nearby discovery, attraction details, active exploration, unlocked story, saved attractions and visit history. Navigation will follow predictable Android patterns. The active exploration screen will give start/stop status, destination, approximate distance, direction, sensor availability and a visible way to end the session. Loading, empty, denied-permission, offline and unavailable-sensor states will be designed alongside successful states rather than added at the end.

XML layouts will use Material components, readable contrast, sufficient touch targets, meaningful labels and scalable text. Screens will account for system-bar insets and will be checked at multiple screen sizes, orientations and font scales. The team will run short task-based usability sessions with people outside the development group. Findings will be recorded, prioritized and addressed where they affect comprehension, control or accessibility.

### 3.7 Architecture and implementation quality

Activities and Fragments will be responsible for displaying state and forwarding user actions. ViewModels will hold screen state, repositories will coordinate local and remote data, and dedicated sources will own location and sensor lifecycles. Pure Java utility or domain classes will implement distance, bearing, heading normalization, movement classification and ranking. This arrangement limits lifecycle errors, makes failures easier to isolate and supports fast JVM tests.

Before adding feature classes, the team will resolve the current case mismatch between `com.comp90018.StoryStep` and `com.comp90018.storystep`. The preferred Java convention is the lowercase package, but the change will be recorded as a team decision and applied consistently to the namespace, application ID, source directories, package declarations and imports. Dependencies will be managed through the version catalogue, repositories will remain centrally configured, and the project will retain its required Java 11 and Android SDK settings.

Development will occur on small feature or fix branches. Every substantial change will be merged through a pull request with a second member's review. A pull request will be considered ready only when it builds, relevant tests pass, failure states are handled and supporting documentation is updated. This process creates visible evidence of collaboration and prevents integration from being postponed until the end.

### 3.8 Reliability, privacy and graceful degradation

The application will request location access when the user begins a feature that needs it, explain the purpose, and remain usable in a reduced form if permission is denied. Tracking will run only during an explicit foreground exploration session. Location and movement data will be minimized, and API credentials will be excluded from version control.

The team will maintain a failure-state matrix covering denied permission, permanently denied permission, disabled location services, absent accelerometer, absent magnetometer, low compass accuracy, stale location, offline state, server timeout, invalid response and empty local data. Each state will have an expected interface response and a test owner. The goal is not to claim perfect sensing; it is to avoid crashes, misleading precision and dead-end screens when real mobile conditions are imperfect.

### 3.9 Testing and evaluation

Testing will be layered. JVM unit tests will cover Haversine distance, bearing, angle normalization, route accumulation, arrival rules, movement thresholds and recommendation ordering. Repository tests will cover mapping, cache decisions and error propagation. Android tests will cover key navigation, Room behaviour and permission-dependent presentation where practical. Manual tests will cover real sensors, compass calibration, outdoor location variation, lifecycle changes and loss of connectivity.

Each core requirement will have an acceptance test with a measurable result. Examples include ranking nearby candidates in ascending contextual score, changing the preferred category under rainy conditions, showing a stable approximate direction while the device rotates, recording exactly one visit when an arrival boundary is crossed, restoring history after relaunch and retaining a meaningful offline screen. The team will keep a test record containing device/API level, setup, expected result, actual result and evidence. `assembleDebug`, JVM tests and Android Lint will be run before milestone builds; physical-device checks will be required before the final release candidate.

### 3.10 Scope control and feasibility

The project begins from a generated Android skeleton, so the schedule prioritizes one complete vertical slice before broadening the feature set. The first slice will connect a small attraction dataset, foreground location, distance calculation, one discovery screen and local storage. Direction, arrival stories, weather-aware ranking and richer history will be added only after that path works. This order tests the architecture early and provides a demonstrable application even if a lower-priority enhancement must be reduced.

The minimum viable product is protected by explicit exclusions. Turn-by-turn routes, background tracking, social networking, booking, payments and augmented reality will not enter the schedule. Cloud synchronization and community content will be treated as stretch work unless all core acceptance tests pass. The team will review scope at every milestone using completed evidence, outstanding defects and remaining effort.

## 4. Delivery stages and milestones

| Stage | Target period | Main outcomes | Exit evidence |
|---|---|---|---|
| 1. Foundation and design | Weeks 1–2 | Confirm requirements and rubric mapping; fix package identity; agree domain models, architecture, services, wireframes and test strategy | Approved decisions, build succeeds, backlog and wireframes reviewed |
| 2. First vertical slice | Weeks 3–4 | Nearby attraction list, foreground location, distance calculation, basic repository and Room setup | One attraction can be discovered and stored; core unit tests pass |
| 3. Exploration and sensing | Weeks 5–6 | Session start/stop, route summary, accelerometer movement state, magnetometer heading and directional display | Controlled and physical-device sensor tests recorded |
| 4. Context and connectivity | Weeks 7–8 | Weather and attraction service, caching, contextual ranking, explicit offline/error states | Recommendations change under test contexts; offline flow remains usable |
| 5. Stories and history | Weeks 9–10 | Arrival detection, story unlock, saved attractions, complete visit history and privacy review | End-to-end scenario passes without duplicate arrival events |
| 6. Stabilisation and submission | Weeks 11–12 | Accessibility, usability, regression testing, performance/battery checks, documentation and demonstration rehearsal | Release candidate builds cleanly; traceability checklist and evidence complete |

Work will be tracked as small issues with an owner, reviewer, acceptance condition, estimate and dependency. A weekly group meeting will review the milestone board and risks; a shorter integration session will resolve cross-component issues. Decisions affecting scope, package identity, services or data handling will be recorded so that the final report can explain why the implementation changed, if necessary.

## 5. Risks and responses

| Risk | Likelihood / impact | Early warning | Response and owner |
|---|---|---|---|
| GPS noise causes false arrival or route inflation | High / High | Unstable distance in outdoor trials | Accuracy filtering, consecutive observations, configurable radius and injected-location tests; Member 1 |
| Compass readings are unstable or a sensor is absent | High / Medium | Direction jumps or no sensor event | Smoothing, calibration guidance, accuracy status and distance/cardinal fallback; Member 3 |
| Remote service limits, changes or fails | Medium / High | Quota warnings, mapping errors or timeouts | Evaluate services early, isolate provider models, cache useful content and maintain fixtures; Member 5 |
| Package-name mismatch blocks runtime integration | High / High initially | Launch-time class lookup failure | Make and document a team decision in Stage 1, then align all identifiers and tests; Member 1 |
| Features integrate too late | Medium / High | Long-running branches or repeated merge conflicts | Small pull requests, twice-weekly integration and vertical slices; all members |
| Work becomes uneven | Medium / Medium | Owners repeatedly miss milestone items | Use reviewer pairs, reassess estimates and redistribute bounded tasks at each milestone; Member 1 and team |
| Privacy or credentials are mishandled | Low / High | Precise data appears in logs or secret scanning fails | Data minimization, log review, ignored local configuration and repository secret checks; Members 4 and 5 |
| Testing depends only on emulators | Medium / High | Sensor behaviours remain unverified | Reserve recurring physical-device sessions from Stage 3 onward; Member 3 |

## 6. Project governance and success measures

The team will use a shared delivery board to record each feature's owner, reviewer, dependencies, acceptance conditions and supporting evidence. Progress will be reviewed weekly against the current milestone rather than against the number of commits produced by each member. Decisions that affect scope, package identity, data services or privacy will be recorded, and unresolved risks will be assigned an owner and review date. This gives the group enough visibility to redistribute bounded tasks early when work becomes uneven or a dependency takes longer than expected.

The project will be considered ready for its final demonstration when the application builds and launches reliably, and a user can complete the full exploration flow from discovery to stored visit history. Contextual recommendations must respond predictably to supplied weather and location conditions; arrival must trigger a story only once per session; and saved attractions and session records must remain available after relaunch. Permission denial, missing sensors and loss of connectivity must lead to understandable fallback states rather than crashes or dead ends.

Technical readiness will be supported by repeatable JVM tests for the core calculations, repository tests for data and error handling, Android tests for important interface paths, and physical-device evidence for sensing behaviour. Before a release candidate is accepted, all high-priority defects must be closed or have an agreed workaround, the two representative end-to-end scenarios must pass, and the group must complete a final review of privacy, accessibility and out-of-scope boundaries.

## 7. Use of tools and assistance

The group used OpenAI Codex while preparing this project plan. It was used to help organize the report structure, turn the proposed feature set into staged milestones, suggest a balanced initial division of responsibilities, and identify technical risks and test cases. The group members reviewed the generated suggestions against the subject requirements and the project's README, revised the wording and scope, and remain responsible for the accuracy, feasibility and final submitted work.
