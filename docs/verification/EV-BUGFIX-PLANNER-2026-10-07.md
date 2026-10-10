# Planner start and action visibility fixes

- Date: 2026-10-07
- Branch: `bugfix`, based on `main` at `841517d`
- Issues: [#31](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/31),
  [#32](https://github.com/XIAO-LIN-1228/COMP90018-StoryStep/issues/32)

## Changes

Starting a planned walk now checks location permission and device location services.
Requestable denial offers an explanation and a permission request; settings-required
denial offers app settings. Cancellation keeps the plan. Returning from settings
requires another explicit Start. A permission result never automatically launches
another permission request.

The planner waits for the session record to be created before navigating. A failed
write leaves a retryable message on the planner, and duplicate starts are ignored.
The pending result lives in a ViewModel across Activity recreation. An existing
active or paused walk is opened without replacing it.

An exploration page without a session returns on its Back control without offering
to finish or discard a nonexistent journey. Existing session finish/discard handlers
remain in place.

Add a place and Start walking now sit outside the sliding sheet, with clearance
above the bottom navigation. Empty-state text starts at the top of its area. Button
height can grow with text, and the middle sheet position is calculated between the
collapsed and expanded positions after accounting for the fixed footer.

## Verification

Executed on this branch:

```text
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lint --console=plain
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.comp90018.storystep.ui.PlanPresentationTest \
  --console=plain
./gradlew testDebugUnitTest lint --console=plain
```

- PASS: 854 JVM tests, no failures, errors or skips, including 8 new planner-start tests.
- PASS: debug application and instrumented-test APK builds.
- PASS: Lint, 0 errors and 113 warnings.
- PASS: all 4 planner presentation tests on Pixel_8, API 36, 1080 x 2400 / 420 dpi,
  at both 100% and 200% font scale; no failures or skips. Font scale restored to 100%.
- PASS: whitespace validation with `git diff --check`.

The JVM cases cover denied/not-requested/settings-required access, no record or GPS
registration on denied starts, delayed and failed writes, retry, double taps,
approximate access, access loss during a write, background start rejection,
consumed navigation results and preserving an existing paused walk.

The presentation cases cover visible empty guidance and Add, disabled empty Start,
five-stop plans at all three distinct sheet positions, rotation, navigation-bar
clearance, no-session Back, and denied-start recovery without creating a journey.
Two layout cases were also run directly with `adb shell am instrument` to retain
their screenshots for visual inspection. The 200% screenshots confirm that the
planning controls and empty guidance remain visible.

## Limits

API 37, physical GPS walks, system permission-dialog interaction and TalkBack were
not rerun here. This is focused regression evidence, not complete release acceptance.
Visual inspection at 200% also shows clipping of the existing bottom-navigation
labels; this separate tab-bar issue is not changed by these planner fixes.
Issues #33 (weather investigation) and #34 (onboarding affordance) remain outside
this patch.
