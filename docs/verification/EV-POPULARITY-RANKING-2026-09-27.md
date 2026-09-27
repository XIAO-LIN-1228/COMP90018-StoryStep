# Popularity-aware recommendation evidence — 2026-09-27

Branch: `feature/popularity-ranking`

## Scope

- Reuse the Wikidata sitelink count already stored on each attraction as a recognition proxy.
- Keep missing popularity (`0`) neutral rather than treating it as unpopular.
- Apply a logarithmic, bounded adjustment of at most +0.05 in Recommended mode.
- Explain a positive contribution with the `Widely known` recommendation reason.
- Preserve distance-only ordering and empty reason lists in Nearby mode.
- No database migration, new provider request or additional user data.

## Policy

For a positive sitelink count, the adjustment is:

```text
0.05 * log1p(min(sitelinks, 100)) / log1p(100)
```

The logarithm prevents a globally famous place from overwhelming distance, weather, local time,
saves, visits or opening status. Counts above 100 receive no extra advantage. This is explicitly a
Wikidata-coverage proxy, not a user rating, review score or live visitor count.

## Automated results

Executed from the repository root on 2026-09-27:

```text
./gradlew test assembleDebug assembleDebugAndroidTest lint
BUILD SUCCESSFUL
JVM tests: 841, failures: 0, errors: 0, skipped: 0
Android Lint: 0 errors, 112 warnings
Debug APK: built
Android test APK: built
```

New tests cover missing and negative inputs, monotonic log scaling, the 100-sitelink cap, bounded
score contribution, reason generation, ViewModel integration, and proof that Nearby mode remains
unaffected.

## Manual checks still to run

- Compare Recommended and Nearby using cached attractions with different Wikidata coverage.
- Confirm `Widely known` appears only in Recommended explanations with positive coverage.
- Check the additional reason at normal and 200% font size and with TalkBack.

These presentation and accessibility checks are `NOT_RUN`. Automated tests establish the policy
and integration behavior, not the real-world quality of Wikidata coverage as a popularity proxy.
