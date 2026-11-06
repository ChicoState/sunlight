# ADR-001: Use foreground CameraX brightness analysis for Focus completion

## Status

Superseded by [ADR-002](ADR-002-routine-light-input.md) for duration, camera timing, and debug input. Foreground camera privacy constraints remain in effect.

## Context

Sunlight Focus must help a user avoid leaving the app overnight, begin checking after an editable local time, and finish only after the user moves the phone into bright sunlight. The app must remain self-contained and must not store camera media.

## Decision

Use a foreground activity with `FLAG_KEEP_SCREEN_ON`, a dim red Compose interface, and CameraX `ImageAnalysis` bound to the activity lifecycle. Analyze front-camera luminance in memory only, then stop the camera and run a fixed 15-minute final countdown once sustained brightness is detected. Store only the release-time preference in DataStore.

## Consequences

Android navigation cannot be permanently disabled, and screen-on behavior ends when the app leaves the foreground. The brightness heuristic can accept artificial light and requires the user to move the phone into direct sunlight. No camera preview, photo, video, location, account, or network data is collected.
