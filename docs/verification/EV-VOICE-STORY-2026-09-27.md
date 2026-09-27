# Voice story and arrival feedback evidence — 2026-09-27

Branch: `feature/voice-story`

## Scope

- User-initiated Android TextToSpeech on the existing story screen.
- Listen, pause and continue controls. Continue repeats the interrupted sentence because Android
  TTS does not expose a reliable word position.
- 0.75x, 1x and 1.25x narration rates.
- Full written-story fallback when initialization, language selection or speech fails.
- One 35 ms vibration when a newly persisted arrival prompt is first presented.
- No automatic narration, background playback, recorded audio or network voice dependency.

## Implementation evidence

- `platform.speech.SpeechEngine` is the narrow platform seam; `AndroidTextToSpeechEngine` owns the
  Android engine and returns callbacks on the main thread.
- `ui.story.StorySpeechController` is a framework-free sentence state machine. It ignores stale
  completion callbacks after pause or story replacement and releases the engine with the view.
- Story controls remain hidden without real story text. Unavailable speech disables playback and
  explicitly leaves the text readable.
- `ArrivalFeedbackGate` keys feedback by session and attraction. It lives in the exploration
  ViewModel, so repeated LiveData renders and view recreation do not vibrate again.
- Haptic feedback is requested only after the coordinator exposes a successfully stored visit;
  save failure never produces a success cue. Devices without a vibrator silently continue.

## Automated results

Executed from the repository root on 2026-09-27:

```text
./gradlew test assembleDebug lint
BUILD SUCCESSFUL
JVM tests: 827, failures: 0, errors: 0, skipped: 0
Android Lint: 0 errors, 112 warnings
```

New JVM coverage includes sentence sequencing, pause/continue, rate changes, stale callbacks,
engine unavailability, rejected speech, idempotent shutdown and one feedback claim per visit.
The existing exploration arrival tests also pass.

## Still to run on a device

- Confirm an installed English voice starts only after tapping Listen.
- Pause mid-sentence, continue and confirm that sentence restarts without skipping later text.
- Check all three rates and verify leaving the screen stops speech.
- Disable or remove compatible voice data and confirm the readable fallback remains usable.
- Trigger one real arrival and confirm one light pulse, with no repeat after rotation or rerender.
- Run TalkBack reading-order and 200% font-size checks over the new controls.

These physical and accessibility checks are `NOT_RUN`; the JVM/build/Lint results do not establish
their outcome.
