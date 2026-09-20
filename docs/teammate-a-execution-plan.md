# Teammate A execution and review plan

Date: 2026-09-20. Local branch: `feature/context-recommendations`. Review baseline: `a4b6256`.
This plan covers REQ-21, REQ-22 and the agreed visible prototype cleanup. It is
grounded in README scope, `development_plan.md` and decision D-07. All work stays
local; do not push, create remote branches or open PRs.

## Delivery steps and exit criteria

| Step | Work and dependencies | Exit criteria |
|---|---|---|
| A1: contextual policy | Review the pure Java recommendation engine against D-07: distance, weather, local time, venue classification, saved and visited context. | Finite bounded scores; invalid/out-of-range places excluded; missing/stale context neutral; deterministic score/distance/ID ordering; reasons generated from actual inputs. |
| A2: presentation and updates | Review Recommended/Nearby controls, search/venue/unvisited filters, top-30 boundary and bindings to location/weather/save/visit changes. Depends on A1. | Nearby is distance ordered; filters run before limiting; reasons match current inputs; reranking preserves destination; context-only updates do not fetch attractions. |
| A3: lifecycle and failures | Trace initial loads, overlapping callbacks, weather expiry, navigation, rotation and permission recovery. Depends on A2. | Old callbacks cannot overwrite new context; missing data is not presented as known; visible controls agree with retained state; stale/cache labels survive updates. |
| A4: real profile and cleanup | Review statistics repositories, permission/provider/sensor checks, obsolete Memory navigation removal and story/weather attribution. | Real counts or explicit unknowns; permission/sensor state rechecked on return; no fake save action; finished-journey photo flow remains reachable; provenance is honest. |
| A5: defect repair | Reproduce concrete defects with focused regressions, repair in scope, then run the regression and the complete automated gate. | New regressions fail before the fix and pass after; no scope expansion into teammate B work. |
| A6: evidence and handoff | Execute `teammate-a-test-plan.md`; record commands, counts, failures, fixes, limitations and remaining manual checks. | Each case has PASS/FAIL/NOT_RUN evidence. A successful JVM/build run is not called full device or release acceptance. |

## Scope boundaries

Teammate B owns network retry/backoff/rate limits, saved-detail fallback after cache
eviction, backup exclusions and deletion reliability. These remain open unless a
specific teammate A regression requires a narrow integration repair. Do not change
Room schema, technology stack, main or repository visibility.

Physical GPS/compass/motion calibration and battery behavior require a real device.
Full accessibility, API 26+ compatibility and E2E acceptance require device evidence.
Do not infer these results from mocks, compilation or Lint.

## Deliverables

- Reviewed local implementation and narrowly scoped fixes with regression tests.
- [Test plan](teammate-a-test-plan.md) with reproducible commands and expectations.
- [Review and test report](verification/EV-TEAM-A-REVIEW-2026-09-20.md) recording the
  actual run, known gaps and requirement-level conclusions.
