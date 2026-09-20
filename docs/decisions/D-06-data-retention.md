# D-06 data retention and deletion

Date: 2026-09-20. Scope: NFR-04.4. Status: **proposed, pending team approval.**

NFR-04.4 requires a retention and deletion policy. The backup and transfer exclusions half of
that requirement is already implemented in `app/src/main/res/xml/backup_rules.xml` and
`data_extraction_rules.xml`. This note settles the retention half, which had no policy and no
implementation.

## Motivation

The app accumulates two kinds of data in one database file. Journeys, route points, visits,
saved places and journey photographs are the user's own record of where they walked. Attraction
records, fetched-area bookkeeping and weather observations are copies of provider data held so
the app works offline. Keeping provider copies forever means a device carries an ever-growing
trace of every area the user has ever opened the app in, long after it is useful. Deleting the
user's own record on a timer would destroy the thing the app exists to keep.

## Options considered

1. **No retention at all.** Simple, but leaves stale provider copies indefinitely and gives the
   ledger nothing to point at for NFR-04.4.
2. **Age out everything, including journeys.** Bounds the data but silently destroys user
   history. Rejected: the user owns that record and only the user may remove it.
3. **Age out provider caches only, on a fixed window.** Chosen.

## Chosen behavior

- **User-authored data is never removed automatically.** Sessions, route points, visits, saved
  places and journey photographs are removed only by an explicit user action — the Privacy & Data
  clear controls, per-journey delete, or unsaving a place.
- **Provider caches are kept for 30 days from fetch**, then removed:
  - `attractions` rows whose provenance is not the bundled seed
  - `attraction_areas` bookkeeping rows
  - `weather_cache` rows
- **The bundled seed set is never removed.** It is shipped content, not fetched data, and it is
  what the app falls back to with no network. The existing `provenance != :seedProvenance`
  predicate in `AttractionDao` expresses this.
- **Saved snapshots are unaffected.** `saved_attractions` holds its own copy with no foreign key
  onto `attractions`, so ageing out a cached record never removes a place the user kept. This is
  the same property REQ-17's refresh tests already protect, and the sweep is covered by its own
  test for it.
- **The sweep runs once per process start**, off the main thread, and its failure is swallowed.
  A cleanup that could not run is not a reason to fail a launch.

30 days is a proposal, not a team decision. It is a single constant in
`domain.privacy.RetentionPolicy` and changing it changes nothing else.

## Affected requirements and tests

- NFR-04.4 — retention half. The backup/transfer half was already closed.
- AT-Q04A / AT-Q04B remain **NOT_RUN**: they require device evidence, and platform backup
  behavior is explicitly not certified without it.
- JVM coverage: `RetentionPolicyTest` (cutoff boundaries), `CacheRetentionSweeperTest` (seed rows
  and saved snapshots survive; expired provider rows do not).

## Out of scope

Retention of logs (there are none beyond method/host/path/status, see NFR-04.3), any server-side
retention (there is no server), and export or portability of user data.
