# Sunlight UI

## Intended experience

Use several chosen times each day to prompt a sunlight check, spend a chosen
amount of time in sunlight while only user-allowed apps remain available, then
regain app access automatically.
A hold-to-unlock emergency action should allow earlier access. Regular sunlight
checks during the routine remain planned; their cadence and interruption behavior
still need definition. Fifteen minutes is the default, not a fixed requirement.

## Current interface

- One morning panel groups the next upcoming Sunlight Time and sunlight duration.
  Home shows only that next occurrence. Its Add Sunlight Time action opens Alarms,
  where all unique daily times appear chronologically and can be added, edited, or
  removed with an accessible red trash action.
- Keep the primary routine action directly below that panel. Show status inside
  the panel and a large time-remaining display only while the timer runs.
- Use “Sunlight Time,” “sunlight duration,” and “morning routine,” rather than
  “Focus.”
- Duration uses a native NumberPicker with 1–120 minutes, Save, and Cancel.
- Save times to local DataStore and migrate the previous single wake-up time into
  the daily schedule. Freeze edits while active.
- The normal routine waits until the next Sunlight Time before requesting camera
  permission or opening the camera. Each later time clears an active countdown or
  partial reading and starts a fresh check. If permission is denied, keep an Allow camera action.
  Camera frames stay in memory; no images are saved or transmitted.
- Debug builds show a secondary “Debug: simulate sunlight” action. This starts
  a marked test immediately without camera access or changing the saved schedule.
  Simulated readings use the same brightness detector as camera frames.
  Release builds do not expose the action, and the handler rejects it too.
- During an active debug run, show a native Time speed slider from 1×–100×.
  Apply every drag update immediately, with an accessible current-speed label.
  A monotonic simulated clock preserves elapsed progress when speed changes;
  light detection, countdown display, and completion share that clock. Reset to
  1× on each new run. Normal routines keep real-time behavior.
- During a normal active routine, request the user-managed draw-over-other-apps
  permission and show a semi-transparent red/amber overlay through a foreground
  service only while Sunlight is in the background. Returning to Sunlight removes
  the overlay. This is an app overlay, not Android's global Night Light setting;
  it is unavailable when the user denies the permission and is disabled for debug
  runs.
- At the start of a normal routine, ask the user whether to open Android's Night
  Display settings. The app cannot enable or configure this system setting itself;
  users may enable it and choose its intensity there.
- Retain bottom navigation outside scrolling content: Home, Blocked apps, Alarms,
  Settings. Settings stays farthest right. Alarms manages Sunlight Times; Blocked
  apps and Settings remain title-only placeholders. Each icon has a text label and
  tab selection semantics.
- Keep the hold-to-exit action fixed above navigation on every page during a
  routine. A tap does not exit; a named accessibility long-press action does.
- Preserve the charcoal/amber palette, dim red active display, system insets,
  wrapping text, scrollable Home, and minimum 48 dp navigation/56 dp action targets.

## Design guidance

[Material's card guidance](https://github.com/material-components/material-components-android/blob/master/docs/components/Card.md)
groups related content and actions in a common surface. This informs the unified
morning panel instead of visually independent time and routine cards.
[Material lists](https://m3.material.io/components/lists/overview) inform the repeated
label/value/action structure. [Android's permission guidance](https://developer.android.com/training/permissions/requesting)
recommends requesting permission in context and allowing graceful denial; this
informs the delayed camera request and explicit retry action. These are layout
and permission choices, not claims of implementing alarm or blocking behavior.

## Remaining preview limitations

The foreground prototype waits for each selected daily time, checks brightness,
stops the camera, then runs the chosen timer. A later scheduled time resets that
timer and requires a new check. Bright artificial light can pass the check. No
alarm rings, apps are not blocked, and sunlight is not checked continuously
between scheduled times. The UI describes this as a preview. See
[ADR-002](decisions/ADR-002-routine-light-input.md) for shared light-input behavior
and [ADR-003](decisions/ADR-003-daily-sunlight-times.md) for daily scheduling.
An active routine survives rotation and process recreation; a partially detected
light check restarts. [ADR-003](decisions/ADR-003-routine-state-ownership.md) records
that `SunlightViewModel` owns this state and the Activity only hosts the UI.

## Verification

Run `./gradlew --no-daemon verify` for logic, compilation, and static checks;
`./gradlew --no-daemon :app:connectedDebugAndroidTest` for picker persistence,
navigation, custom duration, debug input, waiting behavior, and hold-to-exit.
Visually inspect Home, the duration picker, active timer, small screens, and
enlarged text. A physical device is still needed to validate the light heuristic.
