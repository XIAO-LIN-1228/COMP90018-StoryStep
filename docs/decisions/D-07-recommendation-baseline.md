# REQ-21 recommendation baseline

Date: 2026-09-20. Scope: teammate A, REQ-21/22.

The team authorized the development plan's initial weights for this implementation. They remain
an initial product policy, not evidence of recommendation quality or physical-device acceptance.

- Score: 0.45 distance + 0.25 weather + 0.10 local time + 0.10 saved + 0.10 unvisited.
- Distance: 1 - distance/radius. Non-finite, negative and out-of-radius distances are excluded.
- Rain/snow: indoor 1, outdoor 0, unknown 0.5. Dry or unavailable weather is neutral (0.5).
- Local time: 08:00 inclusive to 18:00 exclusive prefers outdoor (1); otherwise indoor (1).
  Other venue/time combinations are neutral (0.5). These are preferences, never opening hours.
- Known saved: 1; known unsaved: 0; unavailable saved data: 0.5.
- Known unvisited: 1; known visited: 0; unavailable visit data: 0.5.
- A fully parsed current opening status applies a bounded post-score adjustment: open +0.05,
  closed -0.05, and unknown 0. The result remains clamped to 0–1. Closed places are demoted,
  never hidden. The initial weighted baseline above is otherwise unchanged.
- Wikidata sitelink count is a bounded recognition proxy, not a rating or live visitor count.
  Zero is unavailable and neutral. Positive counts use `log1p(min(count, 100)) / log1p(100)`
  multiplied by 0.05, so extreme global recognition cannot dominate local walking context.
- The opening-hours evaluator intentionally supports only `24/7`, weekday lists/ranges, simple
  time ranges, split hours, `24:00` endings and overnight ranges. Missing, malformed, holiday,
  seasonal, conditional or partly understood rules are unknown rather than assumed closed.
- Fresh weather must match the origin's weather area, have neither timestamp in the future,
  and have both fetch and observation age at most 30 minutes. A repository stale result is
  always neutral. This conservative observation-age check avoids scoring with old provider data.
- Score ties break by distance, then stable attraction ID. Nearby mode uses distance then ID.
- Search/venue/unvisited filters apply to the entire loaded candidate set before ranking.
  The top 30 limit applies after ranking. Nearby mode remains distance-only.
- Positive reason codes come from the same calculation as the score; missing context never
  generates a positive weather, saved or unvisited claim.
- Opening and closed reason codes come from the same conservative status evaluation. Nearby mode
  remains distance then ID and presents no recommendation reasons.

Implementation is pure Java in `domain.recommendation`. Tests use controlled weather and clocks;
no live rain or device sensor is required to reproduce the ranking fixture (0.76 indoor,
0.51 outdoor, and 0.635 for neutral weather at the documented equal-distance inputs).

## REQ-22 integration contract

Presentation reuses existing data sources. Location moves of 10 m trigger a local rerank; the
existing 250 m attraction requery threshold is unchanged. A visible-screen 30-second timer ages
weather and recomputes local time without HTTP. Area changes or an explicit weather refresh may
request weather through the existing cache. Mode and filters survive Activity recreation through
the destination ViewModel. Selection stays in the existing exploration coordinator.

Field tuning, full accessibility/device acceptance and network retry/backoff remain separate work.
