# Opening-status recommendation evidence — 2026-09-27

Branch: `feature/opening-status`

## Scope

- Conservatively evaluate common OpenStreetMap opening-hours rules against the device's local
  day and minute.
- Show `Open now`, `Closed now`, or `Current status uncertain` above the readable source rule in
  attraction details.
- Adjust Recommended ordering by +0.05 for open and -0.05 for closed, while unknown remains
  neutral. Closed attractions remain visible and Nearby ordering remains distance-only.
- No database migration or network-provider change: the existing `opening_hours` field is reused.

## Safety boundary

The evaluator accepts `24/7`, weekday lists and ranges, simple and split time ranges, overnight
ranges, and `24:00` as an end time. It returns unknown for missing, malformed, holiday, seasonal,
conditional, commented or partly parsed input. It parses the complete expression before making an
open or closed claim, so a rule such as `Mo-Fr 09:00-17:00; PH off` is uncertain rather than
partially evaluated.

Opening is inclusive and closing is exclusive. Overnight intervals carry into the next day. The
device local zone is used consistently with the existing local-time recommendation preference;
the app does not claim knowledge of a remote attraction's time zone.

## Automated results

Executed from the repository root on 2026-09-27:

```text
./gradlew test assembleDebug assembleDebugAndroidTest lint
BUILD SUCCESSFUL
JVM tests: 838, failures: 0, errors: 0, skipped: 0
Android Lint: 0 errors, 112 warnings
Debug APK: built
Android test APK: built
```

New JVM coverage includes exact opening and closing boundaries, weekday and wrapping ranges,
split hours, overnight carry, `24:00`, invalid moments, complete-rule fallback, bounded scoring,
open/closed reasons, ViewModel clock integration, and proof that Nearby ordering ignores opening
status.

## Still to run on a device

- Compare the displayed status with a known place before, during and after its recorded hours.
- Confirm complex hours show `Current status uncertain` together with the unmodified readable
  schedule.
- Verify the two recommendation modes and reason text at normal and 200% font size.
- Check TalkBack reading order and status pronunciation.
- Confirm a recommendation refresh across an opening or closing boundary while returning to the
  screen.

These physical-device and accessibility checks are `NOT_RUN`; JVM/build/Lint results do not
establish their outcome or guarantee that third-party opening-hours data is current.
