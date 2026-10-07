# Novelty comparison research — 2026-10-07

Status: research and discussion material, NOT APPROVED report prose.

Two user-requested agents separately reviewed official competitor sources and the
current application code. Competitors were not installed or benchmarked; project
tests were not rerun. Absence from reviewed documentation does not establish absence
from a product. This sample cannot establish market-wide uniqueness.

## External comparison

| Product | Documented overlap | Primary sources |
| --- | --- | --- |
| Wanderlog | Place discovery, map/list itinerary, distances/times, Pro single-day stop reordering | [Planning](https://wanderlog.com/plan-a-trip), [Route optimization](https://help.wanderlog.com/hc/en-us/articles/13545624787867-Optimize-route) |
| komoot | Route discovery/planning and Premium route weather, including rain, heat, wind and sunset | [Recommendations](https://www.komoot.com/help/tour-recommendations), [Weather](https://www.komoot.com/premium/weather) |
| VoiceMap | Prepared GPS-triggered audio tours, pause/resume, downloaded maps/audio/navigation | [Product](https://voicemap.me/), [Offline](https://support.voicemap.me/can-i-use-voicemap-offline/) |
| izi.TRAVEL | GPS-triggered stories, quizzes, photos/video, maps and favorites | [App](https://www.izi.travel/en/app), [Creation](https://izi.travel/en/create) |
| Organic Maps (open source) | Offline OSM maps/search, pedestrian navigation, bookmarks/tracks and Wikipedia content | [Official README](https://github.com/organicmaps/organicmaps/blob/master/README.md) |
| OsmAnd (open source) | Offline maps/navigation, POI, intermediate stops, tracks, Wikipedia, Pro hourly weather and Android geolocated media notes | [README](https://github.com/osmandapp/OsmAnd/blob/master/README.md), [Weather](https://www.osmand.net/docs/user/plugins/weather/), [Media notes](https://osmand.net/docs/user/plugins/audio-video-notes/) |

These sources establish feature overlap, not measured quality or algorithmic
equivalence. Do not claim that competitors use inferior arrival detection.

## Current implementation evidence

Paths below are relative to `app/src/main/java/com/comp90018/storystep/`.

| Implemented capability | Code evidence | Claim boundary |
| --- | --- | --- |
| Nearby POI and sourced descriptions/images | `data/remote/OverpassQueryBuilder.java`, `OverpassAttractionSource.java` | Content can be missing; not generated original stories |
| Explainable contextual ranking | `domain/recommendation/RecommendationEngine.java:32` | Fixed weights: distance .45, weather .25, time .10, saved .10, unvisited .10; not learned interests |
| User-selected multi-stop walk | `application/WalkPlanner.java`, `domain/plan/WalkPlan.java:129` | Nearest-neighbour ordering; not a globally optimal route |
| Street walking route with distance/duration | `ui/plan/PlanFragment.java:162`, `data/remote/ValhallaRouteRequest.java` | Public Valhalla pedestrian service; fallback estimates; no turn-by-turn navigation |
| Quality-aware arrival and durable story unlock | `domain/arrival/ArrivalPolicy.java:93`, `ArrivalDetector.java:114`, `application/ExplorationCoordinator.java:951` | Accuracy/time/continuous samples checked; visit saved before story shown; thresholds not established as field-tuned |
| Curated place-based observation | `data/repository/ThemeCatalog.java`, `data/repository/RoomThemeRepository.java` | One Parkville three-stop theme; optional questions require arrival; not automatic themes for arbitrary POI |
| Optional speech and one-shot feedback | `ui/story/StorySpeechController.java`, `ui/exploration/ArrivalFeedbackGate.java` | User-initiated TTS; device acceptance still required |
| Local visits, finished journeys, photographs and sensor metrics | `ui/journey/JourneyDetailViewModel.java`, `application/ExplorationCoordinator.java` | Local history, not social publishing or cloud sync; missing optional sensors omit metrics |
| Cached content and foreground sensing | `data/repository/CachedAttractionRepository.java`, `CachedWeatherRepository.java`, `application/ExplorationCoordinator.java` | Cache fallback is not full offline navigation; route requests contain precise route coordinates |

Implementation status and claim boundaries:

- `WeatherApiService.java:31` requests current temperature/weather code only.
  Departure-window recommendations were not present in the audited code. However,
  the concept already exists in `notes.md`, under "Candidate A: Departure-window
  advice (preferred)". On 2026-10-07 the user confirmed that its implementation will
  proceed alongside report preparation. Include it in the innovation discussion as
  a planned contribution with an existing concept design, not merely distant future
  work. Delivery and validation claims await implementation evidence.
- `WalkPlanner.java:11` explicitly describes a process-scoped, non-persisted plan.
  Saved attractions and finished journeys do not establish saved unstarted plans.
- Interest-led choice is the agreed product objective. Current ranking is not an
  interest-first model; the user retains final destination choice.
- Denied-start/Discard issue #31 and weather refresh issue #33 remain limitations.
  Do not describe the entire journey as accepted, seamless or fault-free.
- Existing test records are historical evidence, not new runs or proof of field
  accuracy, usability, educational benefit or comparative superiority.

## Candidate contributions for discussion

1. **Task-specific integration:** connect unfamiliar visitors' autonomous place
   selection and contextual planning with on-site interpretation and local journey
   recollection. The contribution is a particular interaction design at campus/
   nearby walking scale, not invention of maps or audio guides. Free-choice walks
   and the curated theme are distinct modes; arbitrary selected POI do not acquire
   generated observation questions.
2. **Explainable assistance with user control:** expose rule-based reasons for
   recommendations while retaining users' choices. Stale or wrong-area weather
   does not bias ranking. This supports the product objective without claiming
   learned personalization or better recommendation accuracy.
3. **Arrival-linked observation:** quality checks and persisted arrival connect
   physical presence to stories and optional curated observation questions.
   This is strongest as technical integration and place-based learning design;
   GPS stories and quizzes already exist commercially.

4. **Duration-aware departure advice (concept designed; implementation to progress
   alongside the report):** for a selected place/area and a 30/60/90-minute walk,
   compare continuous forecast windows over the next 6-12 hours. Use precipitation
   probability, apparent temperature and wind to rank relative comfort and explain
   the result, including timezone, missing-data and stale-data handling. The specific
   contribution is converting hourly forecasts into an actionable departure choice
   for the whole intended walk, rather than only displaying current weather.
   komoot already offers route weather and OsmAnd hourly forecasts; this comparison
   does not establish that either lacks equivalent advice or that StoryStep is first.
   A self-authored backend remains an architecture decision, not a completed claim.
   Advice is not a weather/safety guarantee and does not automatically alter plans.

Suggested rubric allocation: candidates 1 and 4 can anchor Novelty, with candidate
4 explicitly carrying its delivery status until implemented. Candidate 3 supports
Surprise/Cross-Disciplinary, while its quality checks support Technical depth and
Sensors. Candidate 2 supports Tech Knowledge and decision support. Reusing evidence
does not establish earned marks or measured impact.

Next step: discuss which contribution should anchor the Novelty paragraph, then
prepare an explicitly unapproved draft. No report subsection is finalized here.
