# Theme exploration: implementation and verification plan

Scope authorized on 2026-09-21. Local branch: `feature/theme-exploration`, based on `b2ef81d`.
No push, merge, voice playback, background tracking, route optimisation or offline map download.

## Product contract

One curated theme contains three outdoor observation stops: Old Arts, Old Quadrangle and System
Garden. The ordered list is a suggested sequence; a user explicitly chooses each destination.
The existing GPS quality/dwell policy determines arrival (distance + accuracy <= 50 m, accuracy
<= 25 m, at least three fixes over 10 seconds, bounded gaps/freshness). A single inaccurate fix or
a quiz answer cannot unlock a station. Actual on-site GPS suitability remains field acceptance.

An arrived station unlocks its sourced story and optional question. Answers can be changed while
the journey is unfinished; wrong answers do not remove arrival. Skips can be undone. Skips never
count as completed stops. Ending early saves a partial result; only visiting all stations yields
complete results. A finished result is read-only, and another run begins with empty progress.

Opening the page does not collect location. Start/resume uses the existing permission/provider
checks. Pause/background stops collection; restart restores a paused session with the selected
destination. Directions, story and journey details link back to theme progress. Theme data is
available offline independently of provider attraction caches. Source links require a browser and
network; map tiles retain the existing cache limitations. No opening hours are promised.

## Implementation sequence and invariants

1. Pure Java validated theme definition, station and progress models. Package curated content in
   `ThemeCatalog`, with source URLs, validated coordinates and a version.
2. Room 9→10 additive migration. `theme_runs` stores the full definition snapshot keyed by session;
   `theme_stops` stores per-session skipped/answer values. Both foreign keys cascade on deletion.
3. Atomically create the session, initial destination, definition snapshot and station rows.
   Reuse the at-most-one-unfinished-session transaction. An insert failure rolls back everything.
4. Derive arrival from unique `(session_id, attraction_id)` visits. Do not write a separate arrival
   counter. A committed visit wins over a concurrent skip. Unrelated or past sessions do not count.
5. Schedule repository work off the main thread. Guard ViewModel reads against out-of-order
   responses, disable overlapping edits and surface failures without optimistic unlocks.
6. Wire a scrollable XML screen, existing discovery/directions/story/history entry points,
   permission explanations, pause, results and accessible question controls. Persist user state in
   Room, not repeated Android view IDs. Refreshes never replace the chosen destination.
7. Export schema 10, run regression checks, document actual evidence and outstanding field checks.

## Test matrix

| Layer | Required assertions | Evidence |
|---|---|---|
| JVM model | Unique stations, immutable data, invalid content/coordinates, stable snapshot roundtrip, arrival/skip counts, partial vs complete | `ThemeProgressTest` |
| JVM repository | Initial destination, one unfinished run, skip/undo, gated answers, finished read-only, unrelated/duplicate visits, corrupt storage, I/O errors and main-thread callbacks | `RoomThemeRepositoryTest` |
| JVM coordinator | Two arrivals + skip + pause/recovery + finish; all stations completed; denied access; poor accuracy; failed visit retry; duplicate start; new run does not rewrite prior history; no background collection | `ThemeExplorationTest` |
| JVM ViewModel | Preview/history restrictions; stale reads; retry; failed skip preserves target; successful skip clears only matching target; refresh retains target; late callback after clear | `ThemeViewModelTest` |
| SQLite/device | v9→v10 preservation and all previous migrations; partial insert rollback; reopen after cache deletion; unique visits; delete/clear/discard cascades and isolation | `ThemeStorageTest`, `StoryStepMigrationTest` |
| Android UI | Preview without start; restored stories/questions; quiz/skip/undo; rotation preserves answers; finish produces read-only partial results | `ThemePresentationTest` |
| Existing regression | All JVM tests, all instrumented tests, debug APK/test APK, Lint | Verification report |
| Manual/device | Portrait/landscape, large font, dark mode, denied/approximate permission, location services off, airplane mode, process kill/relaunch, real walking and battery | Record actual execution separately |

Windows commands (set `JAVA_HOME` and `ANDROID_HOME` to installed paths):

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --no-configuration-cache --console=plain
.\gradlew.bat connectedDebugAndroidTest --no-configuration-cache --console=plain
```

Do not call a compiled test an executed test. Emulator injection and database fixtures establish
software behavior only; they do not validate GPS accuracy, pedestrian access or physical sensors.

## Curated content and provenance

Original short summaries and observation questions, with source links preserved inside each run:

- [University Archives, Old Arts / key 49](https://library.unimelb.edu.au/asc/collections/archives/resources/research-guides/keys-to-the-past/keys/key-49): 1919–1924 building and tower.
- [University Old Quad history](https://www.unimelb.edu.au/old-quad/about/our-history): nineteenth-century quadrangle.
- [University Sustainable Campus, System Garden](https://sustainablecampus.unimelb.edu.au/the-system-garden): 1856 teaching garden and surviving conservatory tower.

Initial approximate points: Old Arts (-37.7976631, 144.9600878), Old Quad (-37.79806, 144.96073),
System Garden tower (-37.79678, 144.95916). They identify sites, not a guaranteed accessible
entrance. Old Quad corroboration: Victorian planning European Cultural Heritage Database report;
System Garden tower: OSM way 377820306 (Mapcarta mirror); Old Arts: coordinate-directory entry.
The university map embeds a generic default camera location, which was not treated as a site
coordinate. These points still require an on-site approach check before a graded outdoor demo.
Changing the catalog in a later app version does not rewrite a started or finished run's snapshot.
