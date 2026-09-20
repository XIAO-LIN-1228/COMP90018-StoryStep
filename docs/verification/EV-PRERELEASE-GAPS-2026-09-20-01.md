# Pre-release gap round: saved-copy fallback, D-06 retention and code-side accessibility

Date: 2026-09-20. Branch: `feature/prerelease-gap-closure`. Starting commit: `3b7c735`.

Nothing was committed, pushed, or opened as a pull request. The work exists only in the working
tree of that branch. Related: [development plan](../development_plan.md),
[verification ledger](../verification_checklist.md), [D-06](../decisions/D-06-data-retention.md),
[the previous review](EV-TEAM-A-REVIEW-2026-09-20.md).

## Decision

This round closes the requirement gaps that did **not** need a device, and corrects ledger rows
that had fallen out of step with the code. It claims nothing about device behavior.

Specifically, it does **not** claim: any AT-xxB case, NFR-05, TalkBack reading order, rendering at
200% font scale, landscape layout as actually drawn, platform backup or transfer behavior, or API
26 / 31 compatibility. No emulator or physical device was started. `AccessibilityChecksTest` was
written but has never been executed.

Android Lint reports **no accessibility findings on either `main` or this branch**, so Lint is not
evidence for the accessibility work in this round. The claims below rest on inspection of the code
and resources, and on the JVM tests named.

## Requirement review

| Scope | Implementation and inspected paths | Evidence and limit |
|---|---|---|
| REQ-17 saved-copy fallback | `SavedAttractionRepository.loadSavedById` (new) over the DAO's existing `findById`; `RoomSavedAttractionRepository` implements it without touching the shared identifier cache; `AttractionDetailViewModel.fallBackToSavedCopy` replaces the bare `error()`; `AttractionDetailUiState.savedSnapshot` carries a `fromSavedSnapshot` flag that `withSaved` preserves; `AttractionDetailFragment` appends `detail_saved_snapshot` to the attribution line | 4 new JVM cases in `AttractionDetailViewModelTest`. No schema or DI change. Device walkthrough of AT-17B against the live provider still NOT_RUN |
| NFR-04.4 retention | [D-06](../decisions/D-06-data-retention.md) written and marked pending approval; `domain.privacy.RetentionPolicy` (pure, 30 days, clamped cutoff); `deleteFetchedBefore` added to the attraction, area and weather DAOs; `data.local.CacheRetentionSweeper` runs all three in one transaction; `AppContainer` assembles it and `StoryStepApplication` runs it once per start on the I/O executor with the failure swallowed | `RetentionPolicyTest` (5 cases, both boundaries) and `CacheRetentionSweeperTest` (5 cases, including that the seed set and a saved snapshot survive). No migration: all three tables already carried `fetched_at_millis`. Never executed against real SQLite |
| NFR-04.4 backup | No change needed — `backup_rules.xml` and `data_extraction_rules.xml` already exclude the `database` domain and `journeys/` from cloud backup and device transfer | Inspection only. The ledger previously said this was open; that was wrong. Device behavior is still uncertified |
| NFR-03 announcements | `android:accessibilityLiveRegion="polite"` on nine asynchronous status texts across nearby, exploration, journeys and saved. The compass line is deliberately **excluded**: `CardinalAnnouncer` emits one announcement per change of cardinal, and one when guidance is lost or regained | `CardinalAnnouncerTest` (5 cases). NFR-03.3 forbids continuous compass announcements, which is why this one path is throttled rather than made a live region. Reading order and actual TalkBack output NOT_RUN |
| NFR-03 labels | Three progress indicators labelled; `JourneyPhotoPager` given a count description, per-page descriptions and a once-per-page announcement, with the page dots marked non-accessible; three decorative images that carried misleading descriptions changed to `importantForAccessibility="no"`; `StoryTabBar` items no longer duplicate their visible label and now report a tab role, selected state and position | Inspection. The unused `privacy_lock_icon_description` string was removed |
| NFR-03 font scale | `Widget.StoryStep.Pill`, both attraction pills, the two onboarding rows, the session-recovery row and every button that had a fixed `layout_height` (17 sites, plus 3 individually) now use `wrap_content` with `minHeight`. Text sizes were already all in `sp` | Inspection. Rendering at 200% is NOT_RUN |
| NFR-03 landscape | `fragment_recovery_state` wrapped in a `NestedScrollView`; `fragment_onboarding`'s text column wrapped, keeping the poster image full-bleed; `fragment_exploration`'s guidance card bounded by the header (`layout_constrainedHeight`) with its content in a `NestedScrollView`, so Stop stays reachable and the map yields space instead | The map is **not** inside a scroll container, so the gesture conflict anticipated in planning does not arise. Actual landscape rendering NOT_RUN |
| NFR-03 touch targets | `ui.TouchTargets` attaches a `TouchDelegate` to widen six sub-48dp icon buttons without changing how they are drawn; `Widget.StoryStep.Chip` raised from 36dp to 48dp because one parent can hold only one delegate and the filter chips share theirs | **Visible change**: the filter chip row is taller. Measured targets on a device NOT_RUN |
| Dead code | Deleted `JourneyApiService`, `JourneyDto`, `MemoryApiService`, `MemoryDto` (no references anywhere in `app/src`), `RouteSketchView` (superseded by `PlaceMapView`), and `StoryMapView` together with its only use — an id-less 210dp tag on the location-rationale screen that drew an invented street grid | `assembleDebug` and `lint` both clean afterwards. `domain.geo.RouteProjection` is now unused by production code but was **kept**: it is a tested, framework-free domain utility and removing it was outside this round's scope |

## Defects found and repaired

**B-BUG-01 — the test fake dropped two columns the real table stores (P3).**
`FakeSavedAttractionDao.copy(...)` omitted `imageUrl` and `popularity`, which the real Room DAO
copies. Any test asserting that a saved copy still renders its photograph would have failed
against a defect that does not exist, and a regression that really dropped those fields would
have passed. Repaired by copying both. Found while writing the fallback tests, not by a failure.

A read-failure switch (`failSingleReads`) was added to the same fake, kept separate from
`failWrites`, so a test can make the saved-copy lookup fail while writes still succeed. That is
the case behind `aFailingSavedLookupLeavesTheErrorStateRatherThanBlankContent`.

## Additional coverage

- `AttractionDetailViewModelTest`: +4 (fallback shown and labelled; unsaved place still errors;
  a failing snapshot read still errors rather than showing blank content; unsaving while reading
  the saved copy clears the bookmark without removing the content).
- `RetentionPolicyTest`: 5 new.
- `CacheRetentionSweeperTest`: 5 new.
- `CardinalAnnouncerTest`: 5 new.
- `AccessibilityChecksTest` (instrumented): 5 cases over nearby, journeys, saved, profile and
  privacy. **Never executed** — counted as device work.

## Executed commands and results

macOS 27.0 (darwin), Gradle 9.5.0, launcher JVM 22.0.2, daemon toolchain Java 25 via foojay, AGP
9.3.1. No emulator or device attached at any point.

```bash
./gradlew test assembleDebug lint
```

| Check | Actual result |
|---|---|
| `test` | 746 tests, 0 failures, 0 errors, 0 skipped (baseline before this round: 721) |
| `assembleDebug` | BUILD SUCCESSFUL |
| `lint` | 0 errors, 81 warnings |
| `assembleDebugAndroidTest` | BUILD SUCCESSFUL — instrumented sources compile; none were run |

Reports: `app/build/test-results/testDebugUnitTest/`,
`app/build/reports/lint-results-debug.html`.

Lint was also run on `main` in a throwaway worktree for a same-day comparison, because the 63
warnings quoted in earlier evidence predate the current dependency set. On `main` today: **80
warnings, 0 errors**. On this branch: **81 warnings, 0 errors**. The single added warning is
`GradleDependency` against the new `espresso-accessibility` entry, which shares the pinned
`espressoCore = 3.5.1` with `espresso-core` and inherits its "newer version available" advisory.
Four `map_*` colours orphaned by deleting `StoryMapView` were removed from both the default and
night palettes, which is why the deletion did not raise `UnusedResources`.

## Acceptance mapping and remaining work

| Case | Status |
|---|---|
| AT-17A / AT-17B | Unchanged — **NOT_RUN** on a device. The fallback they depend on now exists and is JVM-covered |
| AT-Q03A / AT-Q03B | **NOT_RUN**. The code-side work is done; the walkthrough is not |
| AT-Q04A / AT-Q04B | **NOT_RUN**. Retention and exclusions are implemented; neither has been observed on a device |
| AT-Q05A (NFR-05) | **NOT_STARTED**, untouched by this round |
| Every AT-xxB | **NOT_RUN**, untouched by this round |

Before release: run the instrumented suite including `AccessibilityChecksTest`, perform the manual
TalkBack, 200%-font and landscape walkthroughs, exercise backup and transfer on a device, run the
API 26 and 31 migration matrix, and complete NFR-05. The D-06 window of 30 days needs team
approval; it is one constant in `RetentionPolicy` and nothing else depends on its value.
