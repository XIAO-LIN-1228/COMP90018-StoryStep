# StoryStep frontend prototype

This stage implements the approved StoryStep visual direction as a native Android prototype using
Java, XML layouts, Material Components, Fragments, ViewModels, LiveData and Navigation Component.

## Implemented flow

1. First-run welcome and location rationale
2. Explore home, mock map, continuation card and nearby-list mode
3. Attraction detail and exploration confirmation
4. Foreground exploration with straight-line approximate direction and distance
5. Arrival confirmation and unlocked landmark story
6. Optional local memory editor and journey summary
7. Journey history, journey detail, saved attractions and local activity/settings
8. Permission, GPS and offline recovery-state component

The exploration view intentionally does not draw a road route or turn instructions. The dotted line
is an approximate origin-to-destination direction indicator only.

## Design tokens

Shared spacing, corner, button and touch-target dimensions are in
`app/src/main/res/values/dimens.xml`. Brand colours and reusable component styles are in
`colors.xml`, `styles.xml` and `themes.xml`.

Key values include 16dp page margins, 16dp card corners, 54dp primary buttons, 12dp card spacing,
24dp section spacing and a 48dp minimum touch target. Body copy is 15sp and supporting copy is 12sp.

## Data boundaries

- Attractions: `AttractionRepository` with `InMemoryAttractionRepository` for the prototype and
  `AttractionApiService` reserved for Retrofit.
- Journeys: `JourneyRepository` with `InMemoryJourneyRepository` for the prototype and
  `JourneyApiService` reserved for Retrofit.
- Saved places: `UserLibraryApiService` declares the future backend contract.
- Memories: `MemoryApiService` declares the future backend contract.

Replacing fake data should happen in `AppContainer`; Fragments must not construct HTTP, Room,
location or sensor dependencies directly.

## Prototype-only behaviour

- `StoryMapView` is deterministic artwork, not a map SDK.
- `Preview arrival` replaces the future arrival-detection event.
- Location and compass values are presentation data, not live sensor readings.
- The photo button displays a placeholder message; Android Photo Picker is not connected yet.
- Notes and saved states are not persisted in this stage.

## Verification

```bash
./gradlew assembleDebug
./gradlew test
./gradlew lintDebug
```

To preview onboarding again, clear the app data from the Android emulator/device settings before
relaunching the app.
