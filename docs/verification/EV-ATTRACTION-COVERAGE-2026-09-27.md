# Attraction coverage evidence — 2026-09-27

Branch: `feature/attraction-coverage`

## Scope

- Keep real OpenStreetMap records that have `name:en`, `official_name`, or `short_name` when the
  primary `name` tag is absent.
- Discover named aquariums, zoos, theme parks, nature reserves, libraries, and music venues using
  the existing Overpass request and existing application categories.
- Classify the new types conservatively: nature reserves as parks; the other new types as cultural
  sites unless an existing more specific tag applies.
- Classify aquariums and libraries as indoor, zoos, theme parks and nature reserves as outdoor,
  while continuing to leave unrecorded cases unknown.
- Deduplicate records with the same explicit Wikidata or Wikipedia identity even when localized
  names differ.
- No generated names, coordinates, facts, database migration, or additional provider request.

## Safety behavior

Unknown category, indoor status, opening status, description, image, or popularity never excludes
a place. A record is rejected only when it lacks a stable provider identifier, usable coordinate,
and every trusted display-name field. Existing cached records are not deleted by this change.

Source-identity deduplication requires an exact non-empty reference match. Name-only deduplication
retains the existing 40-metre proximity requirement, so similarly named places in different areas
remain separate.

## Automated results

Executed from the repository root on 2026-09-27:

```text
./gradlew test assembleDebug assembleDebugAndroidTest lint
BUILD SUCCESSFUL
JVM tests: 846, failures: 0, errors: 0, skipped: 0
Android Lint: 0 errors, 112 warnings
Debug APK: built
Android test APK: built
```

New tests cover fallback-name precedence, each added provider type, indoor/outdoor classification,
same-source deduplication across localized names, and preservation of distinct source identities.

## Still to run with live data

- Refresh around Parkville and compare usable result count and category mix with the previous
  provider query.
- Confirm fallback-named places display a readable provider-supplied label.
- Confirm no visible duplicate appears for a node and area sharing one source identity.
- Repeat offline to confirm cached and bundled places remain available.

Live-provider and physical-device checks are `NOT_RUN`; changing provider data means automated
fixtures cannot establish the current real-world result count.
