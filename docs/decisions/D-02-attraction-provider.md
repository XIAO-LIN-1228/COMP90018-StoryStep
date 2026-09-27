# D-02 (attraction half) — Attraction data provider

Status: **Resolved 2026-09-07**. Selected: **OpenStreetMap Overpass API**.

This note closes SET-02 for the attraction half of D-02. The weather half was resolved on
2026-09-05 in favour of Open-Meteo; this note applies the same selection criteria to attractions so
that REQ-18 can claim an approved provider rather than an assumed one.

## SET-02.1 Candidate comparison

| Criterion | **OSM Overpass** (selected) | Google Places | Foursquare Places | Wikipedia GeoSearch |
|---|---|---|---|---|
| Local coverage (Melbourne CBD / Parkville) | Dense. Museums, galleries, historic buildings, parks and public art are all tagged | Dense | Moderate; retail-weighted | Dense, but article-shaped rather than attraction-shaped |
| HTTPS | Yes | Yes | Yes | Yes |
| Authentication | **None** | API key + enabled billing account | API key | None |
| Billing | Free | Pay-per-request after a free tier; requires a card on file | Free tier then paid | Free |
| Quotas | No published hard quota; a fair-use policy, no SLA, HTTP 429/504 under load | Per-project quota | Per-key quota | Fair use |
| Schema | Overpass QL in, `{elements:[{type,id,lat/lon or center,tags{}}]}` out | Documented JSON | Documented JSON | Documented JSON |
| Stable IDs | `node/way/relation` + numeric id — stable and citable | `place_id` | `fsq_id` | `pageid` |
| Attribution | **Required**: "© OpenStreetMap contributors" (ODbL) | Required Google logo/attribution | Required | Required (CC BY-SA) |
| Cache / redistribution rights | ODbL permits caching and redistribution with attribution and share-alike on derived databases | Caching restricted by terms; place data may not be cached beyond limited periods | Restricted | Permitted with attribution |
| Expected outages | Public instance is best-effort; overload returns 504, or 200 carrying an HTML error page | High availability | High availability | High availability |

## SET-02.4 Decision and rationale

**Selected: OpenStreetMap Overpass API**, `https://overpass-api.de/api/interpreter`.

The deciding criterion is the one that already decided the weather half: no API key and no account.
A reviewer or teammate can clone the repository and run the app with no setup, and there is no
credential that has to be kept out of Git. Overpass additionally permits caching, which REQ-20's
offline behaviour depends on — the app keeps fetched attractions in `storystep.db` and serves them
when the network is gone, and it must be allowed to.

**Rejected: Google Places API.** Best data quality of the four, but it requires an API key bound to
a billing account. D-02 states that creating accounts or buying plans requires separate team
authorization, and the project convention forbids committing credentials. A key held by one team
member is not a configuration the rest of the team or a marker could reproduce.

**Rejected: Foursquare Places.** Same key problem as Google, with weaker coverage of the historic
and cultural categories this app is about.

**Rejected as the primary source: Wikipedia GeoSearch.** It is keyless and would supply real prose,
but its results are articles near a point, not attractions: streets, tram stops and suburbs come
back alongside landmarks, and there is no category or indoor/outdoor field to map. It is recorded
here as the intended **enrichment** follow-up rather than the primary source — see the limitation
below.

## SET-02.2 Adapter mapping

Request (POST, form-encoded body field `data`):

```
[out:json][timeout:25];
(
  nwr["tourism"~"^(museum|gallery|artwork|attraction|viewpoint|aquarium|zoo|theme_park)$"](around:3800,-37.7950,144.9650);
  nwr["historic"](around:3800,-37.7950,144.9650);
  nwr["leisure"~"^(park|garden|nature_reserve)$"](around:3800,-37.7950,144.9650);
  nwr["amenity"~"^(theatre|arts_centre|library|music_venue)$"](around:3800,-37.7950,144.9650);
);
out center 60;
```

The centre is the centre of a 0.01-degree grid cell, never the user's exact position, and the
request is a POST so the coordinates travel in the body rather than in a URL that could be logged.

Sanitized sample response (trimmed; coordinates are the published locations of public landmarks):

```json
{
  "version": 0.6,
  "generator": "Overpass API 0.7.62",
  "elements": [
    { "type": "node", "id": 2412481861,
      "lat": -37.8032, "lon": 144.9713,
      "tags": { "name": "Melbourne Museum", "tourism": "museum", "building": "yes",
                "wikidata": "Q1747074" } },
    { "type": "way", "id": 26621235,
      "center": { "lat": -37.8046, "lon": 144.9718 },
      "tags": { "name": "Royal Exhibition Building", "historic": "building",
                "tourism": "attraction", "wikipedia": "en:Royal Exhibition Building" } },
    { "type": "way", "id": 25987443,
      "center": { "lat": -37.7955, "lon": 144.9601 },
      "tags": { "name": "System Garden", "leisure": "garden" } }
  ]
}
```

Mapping into the app-owned `Attraction`:

| App field | Source |
|---|---|
| `id` | `"osm:" + type + "/" + id` — e.g. `osm:way/26621235` |
| `name` | `tags.name`; a record without one is rejected |
| `location` | `lat`/`lon`, falling back to `center` for ways and relations |
| `name` | `name`, then the provider-supplied `name:en`, `official_name`, or `short_name`; reject only when all are absent |
| `category` | `tourism=museum\|gallery`→MUSEUM; `tourism=artwork`→PUBLIC_ART; `historic=*`→HISTORICAL_BUILDING; `leisure=park\|garden\|nature_reserve`, `tourism=viewpoint`→PARK; otherwise CULTURAL_SITE |
| `indoor` | `indoor=yes`, `building=yes`, museum/gallery/theatre→INDOOR; park/garden/viewpoint/memorial→OUTDOOR; otherwise UNKNOWN |
| `description` | `tags.description`, else `tags.inscription`, else empty. No text is synthesised |
| `provenance` | `OPENSTREETMAP` |
| `sourceRef` | `tags.wikidata`, else `tags.wikipedia` |

`UNIVERSITY_LANDMARK` is deliberately not inferred from any tag. OSM has no dependable signal for
it, and guessing would put an invented classification in front of the user.

## SET-02.3 Demo dataset

The five bundled seed records (`old-arts`, `potter-museum`, `system-garden`,
`royal-exhibition-building`, `melbourne-museum`) stay in place with `provenance = seed`. They are
never presented as live API results: every row carries its provenance, and the nearby and detail
screens label fetched content with its source. A cache refresh overwrites only rows whose
provenance is a provider, so the seed set survives and remains available offline.

## Configuration and rate-limit strategy

- No credential. No `local.properties` entry, no `BuildConfig` field, nothing to omit from Git.
- The Overpass usage policy asks for a descriptive `User-Agent`; the client sends the application
  name only, with no user or device identifier.
- Fetched attractions are cached for 24 hours per grid cell, so ordinary use of the app makes at
  most one request per area per day. At most one request per area is in flight at a time.
- HTTP 429 and 504 are typed as rate limiting and surfaced as such. This round adds no automatic
  retry or backoff — that is REQ-20.2, deliberately deferred — so a rate limit results in cached
  content plus a user-initiated retry, never a request loop.

## Known limitation carried forward

OSM's `description` tag is rare. Most fetched attractions will have no story text, and the story
screen will be honestly empty for them rather than showing invented prose. The `sourceRef` field
carries `wikidata`/`wikipedia` tags precisely so a later enrichment step can fetch real article
extracts without another schema migration.
