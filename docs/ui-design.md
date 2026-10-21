# Sunlight UI

The handwritten `sunlight ui sketch.jpeg` guides the main screen's clock,
sunlight status, and session progress. This update preserves the implemented
focus flow: choose a local check time, start Focus, check light using the camera,
complete the fixed 15-minute countdown, or hold to end early.

## Design choices

- Group the clock, three session stages, and current instructions in one panel.
  The stage indicator represents session phases, not a measured sunlight level;
  the camera currently reports only whether its brightness check passed.
- Use one prominent Start Focus action and an explicitly labeled time control.
  Keep the hold-to-end escape visible throughout active sessions.
- Use charcoal surfaces, off-white text, and muted amber during setup. Active
  Focus retains a black background, subdued red text, and existing dimming.
  Avoid animation and bright filled controls during active Focus.
- Give controls at least 56 dp height, button semantics, and a named long-press
  action. Announce status changes politely, without announcing each timer tick.
- Allow vertical scrolling, respect system insets, and cap content width on
  larger screens. Text can wrap without fixed-height panels.
- Explain tomorrow scheduling, camera privacy, and the light heuristic in context.
- Display local times in 12-hour format with an explicit AM/PM marker. Use the
  platform's spinner time picker in a dark dialog instead of the analog clock
  face. Native hour/minute/AM-PM controls handle input and accessibility without
  maintaining custom parsing or gesture logic. Cancel keeps the saved time.

These choices apply [Nielsen Norman Group's usability heuristics](https://www.nngroup.com/articles/ten-usability-heuristics/),
especially visible system status, recognition, and user control;
[Android Compose accessibility guidance](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)
for touch targets and interactive semantics; and
[Material color roles](https://m3.material.io/styles/color/roles)
for distinct surfaces, readable text, and restrained accents. Dark colors are a
user preference here, not a claim of a medical benefit.

## Sketch features outside the current implementation

Alarms, application blocking, notification controls, goals, streaks, sleep history,
photo verification, and reward unlocks have no implementation in this repository.
They require separate feature work; this screen does not present inactive controls
or invented data for them. The existing camera does not save verification photos.

## Verification

Compose instrumentation tests cover time selection, starting, hold-to-end safety,
sunlight guidance, countdown display, and restarting after completion. Run
`./gradlew --no-daemon :app:connectedDebugAndroidTest` with a test emulator.
Manual visual checks should include setup, the native dark time picker, active
Focus, a small display, and enlarged system text.
