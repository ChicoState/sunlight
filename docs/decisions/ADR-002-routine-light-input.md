# ADR-002: Share light input between camera and debug tests

## Status

Accepted. Supersedes the fixed-duration and permission-timing parts of ADR-001.

## Context

The user requested an adjustable sunlight duration, a debug button that exercises
the same input as the camera, and no camera access before the selected wake-up time.

## Decision

- Store `sunlight_minutes` in the existing local DataStore. Default to 15 minutes;
  use a native NumberPicker bounded to 1–120 minutes, with Save and Cancel.
  Validate persisted values and snapshot duration at routine start.
- Keep wake-up time and duration in one panel. Remove the numbered stage strips
  and numbered product walkthrough; use a short status and a timer when running.
- Move sustained-light detection from CameraX into a pure Kotlin MorningRoutine.
  Both real and simulated mean-luminance values enter `acceptLight`. Preserve the
  existing brightness threshold and three-second initial check.
- The normal routine waits for its selected date/time before entering the light
  check. Camera permission and binding require this phase and a resumed activity.
  Stop camera work when the activity stops; invalidate asynchronous starts and
  reset partial light detection. A denied permission has an explicit retry action.
- In debuggable builds only, show an immediate simulation test that supplies bright
  readings using a simulated clock. It does not request camera access or alter
  saved wake-up time. UI visibility and the Activity handler both enforce this guard.
- Active debug tests expose a 1×–100× slider. Anchor simulated time to Android's
  monotonic elapsed clock; commit the time reached at the old rate before applying
  each new rate. Light detection, displayed time, and completion use that same
  clock, with 50 ms foreground updates. Apply changes during dragging; each run
  starts at 1×. Normal routines continue to use real time.

## Consequences

Unit tests can exercise wake-up boundaries, interrupted detection, custom duration,
duplicate readings, and automatic completion without Android or a physical camera.
Emulator tests cover the actual picker, persistence, debug action, and waiting UI.

This remains a foreground prototype. The camera stops after the initial successful
check; regular checks during the timer, alarm ringing, and app restrictions require
separate implementation. Completion does not claim to unlock the device.
