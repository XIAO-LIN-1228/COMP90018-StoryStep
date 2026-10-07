# Exploring photos, map and compass

Date: 2026-10-07. Branch: `bigfix`.
Scope: the Exploring dashboard, foreground location presentation, approximate guidance
and the existing walk planner. The user requested both planned and recorded routes.

## Findings and changes

- The information card could consume the map's entire available height. The map now
  has reserved space, the dashboard scrolls independently, and Stop remains visible.
  Landscape places the map beside the dashboard. A recenter control returns to the
  accepted position at walking zoom; a distant destination no longer zooms out the
  initial view until the user's trail becomes indistinguishable.
- Take photo and From library are separate, spacious buttons with distinct icons and
  contrasting backgrounds. The existing camera and picker handlers remain in use.
- Exploring previously rendered only recorded points. It now uses the shared ordered
  plan and existing walking-route source for a dashed planned path beneath the solid
  recorded trail. Numbered stops show the plan order. Route requests start after the
  first accepted GPS point, off the UI thread, and do not repeat on every GPS update.
  Without a street route, the legend explicitly labels the straight-line estimate.
  Late responses cannot overwrite an edited plan or publish after the model is cleared.
  Pause/resume gaps stay separate in the recorded trail.
- The previous direction arrow depended on a destination. The dashboard now also shows
  the phone's true-north heading, including during a free walk or at the destination.
  Missing, unreliable or stale readings show an unavailable state. A display tick
  refreshes heading validity even if the sensor stops sending events. Heading and timer
  updates no longer rebuild all route overlays.
- The existing foreground location pipeline already supplies accepted samples. This
  change does not relax its accuracy checks or invent a position before a valid fix.

## Executed verification

- `./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --console=plain`:
  PASS; 862 JVM tests, zero failures/errors/skips; debug and test APKs built;
  Lint zero errors and 113 warnings, unchanged from the previous baseline.
- `ExplorationPresentationTest`, run with AndroidJUnitRunner on Pixel_8, API 36:
  three tests at normal font size and three at 200% font size. Each set checks portrait
  and landscape photo controls, map and Stop visibility, non-overlapping route legend
  and recenter control, distinct planned/recorded overlays, compass rendering, recenter,
  and the waiting-for-GPS state. Screenshots are captured in the test app's external
  files directory under `exploration-test-evidence`.
- JVM regressions cover independent compass heading, heading expiry, map update cadence,
  first-fix route anchoring, offline route fallback and stale asynchronous route responses.
- `git diff --check`: PASS.

The presentation tests inject explicitly synthetic UI fixtures. They verify rendering
and controls, not physical GPS or magnetic sensor accuracy. They do not save journeys.

## Remaining manual verification and limits

- The reporting phone was not connected for this verification. The final build has not
  been installed on it. Outdoors, grant precise location, start a walk and confirm the
  location marker follows movement, the solid trail grows, and the compass responds to
  rotation. Check pause/resume retains separate trail segments.
- Take a photograph and select an image using the phone's actual camera and gallery,
  then confirm both appear in the saved journey. These external-app round trips were
  not exercised by the presentation tests.
- Live routing-service availability, the earlier phone-specific tile/image loading
  report, TalkBack and other Android API levels remain unverified by this change.
- The shared planner is still in memory. After process death, a restored destination
  can produce a single-destination preview; this change does not persist the full ordered
  plan. The preview is approximate guidance, not turn-by-turn navigation or rerouting.
