# ADR-003: ViewModel owns routine state; the Activity stays a thin host

## Status

Accepted. Refines how ADR-002's behavior is implemented. The one product change is that an
in-progress routine now survives rotation and process death.

## Date

2026-11-02

## Context

`SunlightActivity` held ten Compose state fields, the morning routine, the debug clock,
the tick loop, DataStore reads and writes, both picker dialogs, the camera permission
flow, and window display control (20 functions, with detekt `TooManyFunctions` suppressed).
Because the session lived in Activity fields and the manifest does not handle configuration
changes, rotating the phone or losing the process discarded an in-progress routine.
`MorningRoutine.phase` was hand-copied into Compose state in four places, and a one-shot
asynchronous settings read in `onCreate` could overwrite a wake-up time saved before it
finished.

## Decision

- `SunlightViewModel` owns the current `RoutineSession` and publishes one
  `StateFlow<SunlightScreenState>`. Phase is derived from the routine; `SETUP` means “no
  session.” After every change the session is mirrored into `SavedStateHandle`
  (wake-up instant, duration, countdown end, camera-request flags, debug-clock anchor and
  speed, status text), so it survives configuration changes and process death. Phase is
  not stored: a restored `MorningRoutine` with a countdown end resumes in the countdown
  and completes from the same instant.
- `RoutineSession` binds a routine to one `RoutineClock` chosen at start: wall-clock time,
  or `DebugRoutineClock` over the existing monotonic `DebugClock`. Debug sessions feed
  simulated brightness into the shared detector and ignore camera frames.
- `SettingsRepository` wraps the existing DataStore keys and validation and exposes a
  `Flow`. The ViewModel collects it for its lifetime, so the latest stored value always
  wins; saves also update state immediately.
- The tick loop is `SunlightViewModel.runClock()`, a suspend function that the Activity runs
  inside `repeatOnLifecycle(RESUMED)`. Time-driven transitions therefore still advance
  only while the preview is resumed, as before, without the ViewModel observing lifecycle.
- The Activity keeps Android-only adapters: `setContent`, the permission launcher, native
  TimePicker/NumberPicker dialogs (`NativePickers.kt`), the window display mode driven by
  `phase.isActive()` (`FocusDisplay.kt`), and camera binding. It reports camera conditions
  through `CameraEvent`; the ViewModel decides when to prompt (once per session).
- Camera luminance is computed on a background executor and delivered on the main thread.
- No new dependencies: `activity-compose` already provides `viewModels()`,
  `SavedStateHandle`, and `collectAsStateWithLifecycle`. The ViewModel takes its coroutine
  scope as a constructor argument so JVM tests run without a Main dispatcher.

## Alternatives considered

- **Handle configuration changes in the manifest.** Fixes rotation only, not process death,
  and leaves the god object in place. Rejected.
- **Persist the session in DataStore.** Would survive app restarts, which is a product
  decision (an interrupted preview resuming the next day) outside this refactor. Rejected
  for now; `SavedStateHandle` matches the lifetime of the task the user left.
- **Tick loop in `viewModelScope` gated by a lifecycle signal.** Adds a resumed flag and
  job bookkeeping to the ViewModel for the same behavior. Rejected.

## Consequences

- An in-progress routine, including a debug test's speed and simulated time, continues
  after rotation and after Android recreates the process. A partially detected light
  check still restarts, as it already did whenever the camera stopped.
- Clearing app data or force-stopping the app still ends the routine.
- The routine flow is covered by JVM tests of the ViewModel, settings repository, and
  restore path; emulator tests keep covering the native pickers and screen flow.
