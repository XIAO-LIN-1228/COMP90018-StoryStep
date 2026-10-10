# Hourly forecast and departure-window advice

Date: 2026-10-10. Branch: `feature/departure-window-advice`.
Status: design final after three-way review (code impact, method verification, feasibility);
**not implemented, not tested**.

Nothing in this document is evidence of forecast accuracy, user benefit or device acceptance.
It records the agreed behavior so implementation and the report can cite one contract.

## 1. Goal

Two related flows, both for walking (the planner routes with Valhalla `costing = "pedestrian"`;
there is no travel-mode choice):

1. **Hourly forecast page.** From the home screen, see the chance of rain hour by hour for the
   next 24 hours, without planning anything.
2. **Departure-window advice.** For a planned multi-stop walk, compare departure times over the
   next 24 hours and show, for each, how likely rain is at some point during the whole walk, so
   the walker can choose when to leave.

Results describe relative chance of rain. They are not safety advice or a weather guarantee.
Heat, wind and air quality are out of scope.

## 2. Constraints

- Minimal impact on existing features and the overall UI layout; every change to an existing
  file is listed in §5.2 and is additive.
- No Room schema change; no new persisted state.
- Existing recommendation weights, the home weather card, `WeatherApiService`,
  `CachedWeatherRepository` and session state stay unchanged.
- No background work, notifications or automatic plan changes; these pages collect no location.
- Calculation lives in framework-free `domain` code and is JVM-testable.
- Android only; no backend.

## 3. Hourly forecast page

| Item | Decision |
|---|---|
| Entry | A compact button "Rain forecast" with an umbrella icon right of the home weather reading (`weather_hourly_link` in a new `weather_row` wrapping `weather_card`; the reading takes the remaining width). Style `Widget.StoryStep.Button.Compact`: the secondary white pill at chip size, 48 dp touch target. The card itself still refreshes weather on tap. The row hides with the card in list mode. |
| Location | Exactly what the weather card uses: its current origin and area label. If the card shows the reference area (no live fix), the page shows the same "reference area" label. No permission prompt or separate GPS handling. |
| Horizon | Next 24 hours as one list; no date switching. |
| Fields per hour | Time interval, condition icon, `precipitation_probability` (%) and a bar of that length (`item_rain_row`, shared with advice). The condition comes from the existing `RemoteWeatherMapper.toCondition`; icons are Material Symbols (Apache 2.0: sunny, cloud, rainy, weather_snowy, question_mark), recorded in `docs/licenses/icons/`. A missing value shows "–". The request also asks for `is_day`; clear and cloudy hours at night use `bedtime` and `partly_cloudy_night`, because `weather_code` is the same by day and night (found on the emulator, 2026-10-10). |
| Source | Open-Meteo standard forecast (`/v1/forecast`, `hourly=`), separate from the current-weather request. |
| Failure | Existing retry policy, then an error with Retry. No stale fallback on this page. |
| Time display | Phone's current timezone; each row is labelled by its interval, e.g. "14:00–15:00". |

This page's percentages are Open-Meteo's own PoP (its multi-model ensemble). The advice uses
the average of three models' member counts over a whole walk, so the two pages can show different
numbers; the advice page states its source (§4.5).

## 4. Departure-window advice

### 4.1 Entry, origin and duration

- Entry: a compact button "Best time to leave" with a clock icon under the plan summary line
  in `PlanFragment` ("3 stops · about 2.1 km · around 40 min"), same style as "Rain forecast";
  hidden when the plan has no stops.
- Origin: the plan's own origin (`AppContainer.getReferenceOrigin()`, the point the planner
  routes and measures from). The page says which area the forecast is for. No permission prompt.
- Stay time: one value for every stop, chosen from 5 / 15 / 30 / 60 minutes, default 5
  (matching `WalkPlan.DWELL_MILLIS_PER_STOP`). It affects this calculation only.
- Walking time: `PlannedRoute.getDurationMillis()` when the router has answered; otherwise the
  plan's straight-leg distance ÷ `WalkPlan.WALKING_SPEED_MPS`. (`WalkPlan.durationMillis()`
  already includes a 5-minute stay per stop, so it must not be used here; a new
  `WalkPlan.walkingMillis(origin)` returns walking time only.)
- Trip duration D = walking time + stay time × number of stops.
- If D exceeds **8 hours**, a dialog explains the walk is too long for advice and no request is
  made. Changing the stay time re-checks.

Spatial resolution: the ensembles are ~25 km and the weather area is a 0.05° cell, while a planned
walk spans a few kilometres, so one forecast point for the whole walk is appropriate. This is a
statement about model resolution, not about precision.

### 4.2 Candidate departures

- "Now", then every following full hour up to now + 24 hours (about 25 candidates). Full hours
  align with Open-Meteo's hourly buckets in Melbourne, so a 1-hour walk from 14:00 covers
  exactly one bucket. (Half-hour time zones are not a target; they would straddle buckets.)
- A candidate is kept only if its whole trip ends inside the fetched data. The advice request
  fetches 34 hours (§6.1), which keeps every candidate for D ≤ 8 h.

### 4.3 Chance of rain during the trip: three-model ensemble member counting

Source: Open-Meteo Ensemble API, one request for three global ensembles,
`models=icon_seamless,ecmwf_ifs025,gfs025`, `hourly=precipitation,is_day`:

| Model | Response suffix | Members | Native step |
|---|---|---|---|
| ICON-EPS (DWD) | `_icon_seamless_eps` | 40 | 1 h |
| ECMWF IFS ENS 0.25° | `_ecmwf_ifs025_ensemble` | 51 | 3 h |
| NCEP GEFS 0.25° | `_ncep_gefs025` | 31 | 3 h |

Within each model, for each candidate:

```
wet(member, unit)   = unit total >= 0.2 mm       unit = hour (ICON) or 3-hour block (ECMWF, GEFS)
share(model)        = members wet in any unit the trip touches / members in the model
P(rain during trip) = mean of share(model) over the models used      (equal weight)
```

- **Why three models.** On 2026-10-10 ICON-EPS gave systematically lower wet shares than the
  other two at every wet location sampled (single-hour maxima: Singapore 30% vs ECMWF 96% / GEFS
  100%; Wellington 40% vs 90% / 55%; Perth 10% vs 39% / 39%; Sydney 8% vs 35% / 3%). One
  snapshot, not verified against observations, so it shows disagreement rather than which model
  is right. Single-model ensembles are known to be over-confident; averaging models is the
  standard remedy. Equal weight per model keeps ECMWF's 51 members from outvoting the others.
- **Three-hour models.** Open-Meteo spreads a 3-hour total evenly over its hours (verified:
  identical values in the rows ending 01:00, 02:00, 03:00 UTC). Such a model is judged per block:
  the block total (mean of its rows present × 3, which also handles a block cut by the start of
  the data) must reach 0.2 mm, and a trip touching any part of the block counts the whole block.
  This makes those two models coarser in time; ICON keeps hourly timing.
- Values are reported at 0.1 mm resolution, so ≥ 0.2 mm is the same as "> 0.1 mm". 0.2 mm is
  the Bureau of Meteorology's "any rain" amount and close to the NWS measurable-precipitation
  threshold (0.01 in ≈ 0.25 mm); it also avoids counting models' trace drizzle.
- An hour (ICON) overlaps if any part of it intersects the trip; it then counts fully. Only
  "Now" starts mid-hour.
- Members are discovered by column name (`precipitation` / `precipitation_memberNN` plus the
  model suffix), not a fixed count. A model with fewer than 10 members, or a missing value in an
  hour any departure needs, is left out; the rest still advise. If no model remains: error.
- No independence or correlation assumption is needed: each member is one consistent scenario
  across time. Deriving event probability from ensemble relative frequency is standard practice
  and related to the approach of the NOAA National Blend of Models (which additionally
  calibrates; this design does not).
- Displayed as "about X% chance of rain during the walk", rounded to a whole percent.

Limitations stated on the page's info text and in the report: raw ensembles are uncalibrated;
three-hour models blur timing within a block; a 0.1 mm-rounded hourly share of a 3-hour total
slightly favours "wet" at the threshold.

There is **no fallback method**. If the ensemble cannot be used (§8), the page shows an error
with Retry.

### 4.4 Daylight

- `is_day` comes from the same ensemble request (`is_day_icon_seamless_eps`; any model's column
  if that one is absent). An hour is treated as daylight when `is_day` at its end timestamp is 1
  (an hourly approximation of sunrise/sunset).
- A candidate whose trip overlaps any night bucket is still listed, labelled "After dark".
- The best candidate is chosen among daylight-only candidates; only if there are none is it
  chosen among all candidates, with a note.

### 4.5 Ranking and presentation

- Ranking for "best": daylight first, then lowest probability (unrounded), then earlier
  departure.
- If the daylight candidates differ by less than 5 percentage points, the page says there is no
  meaningful difference, and the earliest daylight candidate is highlighted.
- A summary card above the list: "Best: leave at … · about X% chance of rain" (or "leave now")
  plus at most one note (no daylight > all likely > no meaningful difference).
- All candidates are listed in time order; the best row has a tinted background and the list
  scrolls to it. Rows reuse the forecast page's row layout; the icon slot shows a moon only for
  walks that run after dark, and the label reads "<band> · After dark". No numeric score.
- The stay time is a chip row (5 / 15 / 30 / 60 min) at the top of the page with "Your walk:
  about …" beneath. Changing it recomputes from the ensemble the page already holds while that
  is within `ForecastRepository.LIFETIME_MILLIS` (30 min); otherwise it asks again.
- Each row shows the percentage and a label, aligned to US NWS terms:

| Trip chance | Label |
|---|---|
| 0–29% | Low chance of rain |
| 30–59% | Rain possible — consider an umbrella |
| 60–79% | Rain likely |
| 80–100% | Rain expected |

- "Not advisable" threshold: 60%. If every daylight candidate is ≥ 60%, the relatively best
  one is still highlighted with a note that rain is likely throughout and an umbrella is advised.
- The page footnote names the source and the models actually used: "Average of ICON, ECMWF and
  GFS ensembles via Open-Meteo: chance of ≥ 0.2 mm during your walk".

NWS defines these terms for a 12-hour period and BoM's wording for a day (BoM calls 40–60%
"medium"); applying them to a walk of a few hours is a product choice. It is acceptable for
ranking because every candidate shares the same duration. No empirical cut-off for carrying an
umbrella or cancelling a walk was found; HKO's advice to consider postponing outdoor plans at
"Medium" (≥ 45%) refers to ≥ 10 mm per day, a much heavier event.

## 5. Architecture

### 5.1 New code (names indicative)

| Layer | Element |
|---|---|
| `domain.forecast` | `HourlyForecast`, `EnsemblePrecipitation`, `DepartureAdvisor`, `DepartureAdvice`, `Candidate`, `RainBand`. Pure Java. |
| `domain.repository` | `ForecastRepository`, `ForecastUnavailableException` (next to `WeatherUnavailableException`). |
| `data.remote` | `ForecastApiService` (hourly), `EnsembleApiService` (new base URL), DTO + mapper for the hourly response; the ensemble `hourly` object is parsed as a `JsonObject` because member keys are dynamic. One generic `ForecastSource<T>` seam and one generic retrying wrapper reusing `NetworkRetryPolicy` and `Sleeper`. |
| `data.repository` | `CachedForecastRepository` with one `TimedAreaCache<T>` per forecast kind: in-memory, keyed by weather area, 30-minute lifetime; a value stamped in the future (clock moved back) counts as expired. A separate class is justified because `CachedWeatherRepository` is tied to Room and stale fallback. No coalescing (one request per page open is enough). |
| `di` | A dedicated single-thread executor for forecast work, so retry sleeps cannot stall the shared 2-thread I/O pool used by Room, attractions, weather and routing. |
| `ui` | Forecast page Fragment + ViewModel; advice Fragment + ViewModel; shared row layout/adapter. Bundle values are passed to ViewModels through an `init(...)` call, matching `ViewModelFactory`'s no-argument construction. |

### 5.2 Changes to existing files

All small and additive:

- `di/AppContainer` — wiring and the forecast executor.
- `ui/ViewModelFactory` — two branches.
- `data/remote/ApiClientFactory` — factory methods for the two services; Javadoc.
- `res/navigation/nav_graph.xml` — two destinations and actions; arguments travel as a plain
  `Bundle` with no `<argument>` declarations (latitude/longitude are doubles, which typed
  navigation arguments would reject as float).
- `fragment_nearby_attractions.xml`, `NearbyAttractionsFragment` — a horizontal row around
  `weather_card` for the button; navigation. The header becomes slightly taller.
- `ui/weather/WeatherViewModel` — getters for the current origin and area label.
- `fragment_plan.xml`, `PlanFragment` — a horizontal row around `plan_subtitle` for the button,
  enabled state, passing walking millis and stop count.
- `domain/plan/WalkPlan` — `walkingMillis(origin)`.
- `ui/WeatherText` — widen `conditionLabel` visibility for reuse.
- `res/values/strings.xml`.

Unchanged: `WeatherApiService`, `CachedWeatherRepository`, `RemoteWeatherMapper` (its
package-private `toCondition` is called from the same package), recommendation, exploration and
session code, Room, manifest (INTERNET and ACCESS_NETWORK_STATE already present), backup rules.

## 6. API

### 6.1 External requests

Both send the weather-area cell centre, never an exact position, and need no key. Times are
requested as UTC Unix seconds.

**Hourly forecast** (forecast page)

```
GET https://api.open-meteo.com/v1/forecast
    ?latitude={cellLat}&longitude={cellLon}
    &hourly=precipitation_probability,weather_code,is_day
    &forecast_hours=26
    &timezone=GMT&timeformat=unixtime
```

```json
{ "hourly": { "time": [1791583200, 1791586800, ...],
              "precipitation_probability": [0, 10, ...],
              "weather_code": [0, 2, ...] } }
```

**Ensemble precipitation** (advice)

```
GET https://ensemble-api.open-meteo.com/v1/ensemble
    ?latitude={cellLat}&longitude={cellLon}
    &hourly=precipitation,is_day&models=icon_seamless,ecmwf_ifs025,gfs025
    &forecast_hours=34
    &timezone=GMT&timeformat=unixtime
```

```json
{ "hourly": { "time": [1791586800, ...],
              "precipitation_icon_seamless_eps": [0.0, ...],
              "precipitation_member01_icon_seamless_eps": [0.0, ...],  ... member39
              "is_day_icon_seamless_eps": [1, 1, ..., 0, ...],
              "precipitation_ecmwf_ifs025_ensemble": [...], ... member50, "is_day_ecmwf_ifs025_ensemble": [...],
              "precipitation_ncep_gefs025": [...],           ... member30, "is_day_ncep_gefs025": [...] } }
```

Verified on 2026-10-10 for Melbourne:

- ICON 40, ECMWF 51 and GEFS 31 precipitation columns, one `is_day` per model, no nulls
  within 34 hours; about 33 KB uncompressed.
- The first `time` is the start of the current UTC hour. Open-Meteo documents precipitation and
  PoP as referring to the **preceding hour**, so row `t` covers `(t − 3600, t]`. The forecast
  page drops rows whose hour has ended and shows the next 24.
- ICON-EPS returns nulls for all members together beyond its ~7.5-day horizon (from hour 181 of
  a 10-day request).
- An invalid request returns HTTP 400 with `{"error": true, "reason": "..."}`.
- Free tier: non-commercial; 600/min, 5,000/h, 10,000/day calls. Whether 40 member columns
  count as one variable or several is not documented; with a 30-minute cache this is negligible.

### 6.2 Internal contracts

```java
// domain.repository
final class ForecastUnavailableException extends Exception {
    enum Reason { NO_NETWORK, TIMEOUT, HTTP_ERROR, RATE_LIMITED, MALFORMED, UNUSABLE }
}
interface ForecastRepository {
    void hourly(GeoPoint origin, RepositoryCallback<HourlyForecast> callback);
    void ensemble(GeoPoint origin, RepositoryCallback<EnsemblePrecipitation> callback);
}

// data.remote — one blocking seam, no Retrofit types (same pattern as WeatherSource);
// RetrofitHourlyForecastSource and RetrofitEnsembleSource implement it, and
// RetryingForecastSource<T> wraps either with NetworkRetryPolicy.
interface ForecastSource<T> {
    T fetch(GeoPoint cellCentre, long fetchedAtMillis) throws ForecastUnavailableException;
}

// domain.forecast (pure Java)
final class HourlyForecast        { List<Hour> hours; long fetchedAtMillis; }   // Hour: endEpochSecond, Integer probability, WeatherCondition condition
final class EnsemblePrecipitation { long[] endEpochSeconds; List<Model> models; boolean[] isDay; long fetchedAtMillis; }
//   Model: String name; int blockHours (1 or 3); double[][] memberMm /* [member][hour], NaN = missing */

final class DepartureAdvisor {
    static final long MAX_TRIP_MILLIS = 8 * 3_600_000L;
    static final double WET_MM = 0.2;
    static final int MIN_MEMBERS = 10;
    DepartureAdvice advise(long nowMillis, long tripMillis, EnsemblePrecipitation ensemble);
}
final class DepartureAdvice {
    List<Candidate> candidates;   // time order, "Now" first
    List<String> models;          // models actually used
    int bestIndex;
    boolean noMeaningfulDifference;
    boolean allNotAdvisable;      // every daylight candidate >= 60%
    boolean noDaylightCandidate;
}
final class Candidate { long startMillis; boolean now; double probability; boolean daylight; /* percent, band derived */ }
enum RainBand { LOW, POSSIBLE, LIKELY, EXPECTED }   // 0-29, 30-59, 60-79, 80-100
```

Reason names match `WeatherUnavailableException.Reason` and `NetworkRetryPolicy.Failure`, so
the retrying wrapper maps them the same way; `UNUSABLE` (not retried) is new and covers ensemble
data that parses but cannot be used.

## 7. Workflow

### 7.1 Hourly forecast page

1. Home: the forecast button is shown with the weather card. Tapping it passes the card's
   current origin and area label in a `Bundle`.
2. `ForecastViewModel` emits `Loading` and calls `ForecastRepository.hourly(origin)` on the
   forecast executor.
3. The repository maps origin → `WeatherAreaBucket`. A cached entry younger than 30 minutes is
   returned. Otherwise, if `ConnectivityMonitor` reports offline it fails with `NO_NETWORK`
   immediately; else the retrying source fetches, validates and maps, and the result is cached.
4. The ViewModel keeps the next 24 rows whose hour has not ended, converts to the phone's
   timezone and emits `Content(rows, areaLabel)`.
5. On failure: `Error(reason)` with Retry, which repeats step 2.

### 7.2 Departure-window advice

1. Plan page: "When to leave" is enabled when the plan has ≥ 1 stop. Tapping it passes the
   plan origin, walking millis (router or straight-leg, §4.1) and stop count.
2. The advice page shows the stay-time selector (default 5). D = walking + stay × stops.
3. If D > 8 h → too-long dialog; no request.
4. Otherwise `Loading`; `ForecastRepository.ensemble(origin)` (same cache/offline/retry path
   as §7.1 step 3).
5. `DepartureAdvisor.advise(now, D, ensemble)`:
   1. Keep each model with ≥ 10 members and no nulls in the rows the candidates need; if
      none remains, `UNUSABLE`.
   2. Candidates: Now, then each full hour after now up to now + 24 h, kept if the trip ends
      inside the data.
   3. Per model, count wet members per candidate (hour or 3-hour block, §4.3); average the
      models' shares; mark daylight (§4.4); assign bands.
   4. Choose best (§4.5); set the flags.
6. Emit `Content(advice)`: the list with the best highlighted and scrolled to, "After dark"
   labels, the no-difference / all-poor / no-daylight notes, and the source footnote.
7. Changing the stay time recomputes step 5 from the cached ensemble without a new request,
   unless the cache has expired.

### 7.3 Lifecycle

ViewModels survive rotation and keep their results; recomputation happens only on input change,
Retry, or resume after the cache expired. A result arriving after the page is closed only fills
the cache. The memory cache is lost on process death and the page refetches.

Known limitation: tapping Start later reorders stops from the origin (`orderedFrom`), so the
walk that starts may be ordered differently from the plan that was advised; the duration
difference is usually small and is not corrected.

## 8. Error handling

| Case | Behavior |
|---|---|
| Offline (`ConnectivityMonitor`) | No request, no retry. "You're offline. Check your connection and try again." + Retry |
| Timeout, 5xx | Retried by `NetworkRetryPolicy` (3 attempts, 500 ms exponential backoff); then a generic "Forecast unavailable" + Retry |
| 429 | Retried once after the server delay clamped to 1–5 s; then "Forecast service is busy" + Retry |
| Other 4xx, incl. 400 `{"error":true}` | Not retried; generic error + Retry; the `reason` is logged |
| Malformed JSON, missing `time`, length mismatch, unsorted or non-hourly times | `MALFORMED`, not retried; generic error + Retry |
| One model: fewer than 10 members, or any null in needed rows | That model is left out; the footnote lists the models used |
| No model usable | `UNUSABLE`, not retried; "Rain advice is unavailable right now" + Retry |
| Forecast page: null PoP or weather code in a row | That field shows "–"; the row stays |
| Trip > 8 h | Dialog before any request; stay selector stays usable |
| Plan empty | Button disabled |
| Router has not answered | Straight-leg walking estimate is used |

General rules: no exception other than `ForecastUnavailableException` leaves a source; the UI
never shows a raw exception message; failures never alter the plan, the weather card or stored
data.

## 9. Implementation sequence and tests

1. **Domain** (`domain.forecast`) with JVM tests:
   - end-of-hour alignment: row `t` covers `(t − 1h, t]`;
   - "Now" mid-hour counts the current bucket fully;
   - full-hour candidates cover exactly ⌈D⌉ buckets;
   - wet threshold: 0.1 mm is dry, 0.2 mm is wet;
   - member counting on a recorded 2026-10-10 response;
   - **the same recorded snapshot compared with the maximum hourly probability**, showing a
     multi-hour walk's member-count probability can exceed any single hour (the key figure
     for the report);
   - daylight preference, "After dark", no-daylight case;
   - ties on raw counts and the < 2-member no-difference rule; all-poor flag;
   - 8-hour limit; 34-hour data keeps all candidates for D = 8 h;
   - three-hour block alignment and block totals, including a block cut by the data start;
   - equal model weighting; a model left out for nulls or too few members; `UNUSABLE` when
     none remains;
   - timezone/DST conversion for display.
2. **Remote**: services, hourly DTO mapping, ensemble `JsonObject` parsing with recorded JSON,
   retrying wrappers and reason mapping.
3. **Repository**: `TimedAreaCache` expiry and offline short-circuit.
4. **Forecast page** and home entry.
5. **Advice page**, plan entry, stay selector, too-long dialog.
6. Update `docs/development_plan.md` and `docs/verification_checklist.md`; run `./gradlew test`
   and `lint`; manual check on an emulator, then a device.

## 10. Open points

- Free-tier weighting of ~122 member columns across three models (Open-Meteo counts large
  requests as several calls; still negligible with a 30-minute cache).
- Which model is closest to observed rain is not verified; equal weighting is a design choice.
- Ensemble probabilities are uncalibrated; calibration needs historical forecast–observation
  pairs and is out of scope.
- `is_day` at the bucket end approximates sunrise/sunset to within an hour.

## 11. Sources

Checked on 2026-10-10:

- Open-Meteo forecast docs — PoP is ">0.1 mm of the preceding hour" from a 0.25° ensemble;
  precipitation is the sum of the preceding hour; `minutely_15` outside Europe/North America is
  interpolated from hourly: https://open-meteo.com/en/docs
- Open-Meteo Ensemble API — models, member counts, native time steps:
  https://open-meteo.com/en/docs/ensemble-api; limits: https://open-meteo.com/en/pricing
- NWS forecast terms (PoP wording; 0.01 in measurable): https://www.weather.gov/bgm/forecast_terms
- BoM chance-of-rain wording:
  https://media.bom.gov.au/social/blog/440/clearing-up-the-patchy-rain-introducing-a-more-precise-forecast-language
  (BoM's 0.2 mm "any rain" amount is from secondary sources and should be confirmed on bom.gov.au
  before quoting.)
- HKO probability of significant rain (≥ 10 mm/day):
  https://www.hko.gov.hk/en/education/weather/rain/00568-Q-&-A-for-Probability-of-Significant-Rain.html
- Hamill, T. M., et al. (2017). The U.S. National Blend of Models for statistical
  postprocessing of probability of precipitation and deterministic precipitation amount.
  *Monthly Weather Review*, 145(9), 3441–3463. doi:10.1175/MWR-D-16-0331.1.
  https://repository.library.noaa.gov/view/noaa/28094
- Schefzik, R., Thorarinsdottir, T. L., & Gneiting, T. (2013). Uncertainty quantification in
  complex simulation models using ensemble copula coupling. *Statistical Science*, 28(4),
  616–640. doi:10.1214/13-STS443.
- Gigerenzer, G., et al. (2005). "A 30% chance of rain tomorrow": How does the public
  understand probabilistic weather forecasts? *Risk Analysis*, 25(3), 623–629.

From secondary listings only; check the original before quoting:

- Joslyn, S., Nadav-Greenberg, L., & Nichols, R. M. (2009). *BAMS*, 90(2), 185–193.
  doi:10.1175/2008BAMS2509.1.
- Gabriel, K. R., & Neumann, J. (1962). A Markov chain model for daily rainfall occurrence at
  Tel Aviv. *QJRMS*, 88, 90–95.

Not confirmed; do not cite without locating the original: Hughes & Sangster (1979),
"Combining precipitation probabilities".
