# Sunlight UI

## Intended experience

Wake up to an alarm, spend a chosen amount of time in sunlight while only
user-allowed apps remain available, then regain app access automatically.
A hold-to-unlock emergency action should allow earlier access. Regular sunlight
checks during the routine remain planned; their cadence and interruption behavior
still need definition. Fifteen minutes is the default, not a fixed requirement.

## Current interface

- One morning panel groups wake-up time and sunlight duration as related editable
  rows. Use a shared surface, one subtle divider, consistent Edit actions, and
  shorter text. Remove numbered stages and the numbered product walkthrough.
- Keep the primary routine action directly below that panel. Show status inside
  the panel and a large time-remaining display only while the timer runs.
- Use “wake-up time,” “sunlight duration,” and “morning routine,” rather than “Focus.”
- Duration uses a native NumberPicker with 1–120 minutes, Save, and Cancel.
  Save to local DataStore; retain existing wake-up keys. Freeze edits while active.
- The normal routine waits until wake-up time before requesting camera permission
  or opening the camera. If permission is denied, keep an Allow camera action.
  Camera frames stay in memory; no images are saved or transmitted.
- Debug builds show a secondary “Debug: simulate sunlight” action. This starts
  a marked test immediately without camera access or changing the saved wake-up
  time. Simulated readings use the same brightness detector as camera frames.
  Release builds do not expose the action, and the handler rejects it too.
- During an active debug run, show a native Time speed slider from 1×–100×.
  Apply every drag update immediately, with an accessible current-speed label.
  A monotonic simulated clock preserves elapsed progress when speed changes;
  light detection, countdown display, and completion share that clock. Reset to
  1× on each new run. Normal routines keep real-time behavior.
- Retain bottom navigation outside scrolling content: Home, Blocked apps, Alarms,
  Settings. Settings stays farthest right. The latter three pages remain title-only
  placeholders. Each icon has a text label and tab selection semantics.
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

The foreground prototype waits for the selected time, checks brightness once,
stops the camera, then runs the chosen timer. Bright artificial light can pass
the check. No alarm rings, apps are not blocked, and sunlight is not checked
throughout the timer. The UI describes this as a preview. See
[ADR-002](decisions/ADR-002-routine-light-input.md) for shared light-input behavior.

## Verification

Run `./gradlew --no-daemon verify` for logic, compilation, and static checks;
`./gradlew --no-daemon :app:connectedDebugAndroidTest` for picker persistence,
navigation, custom duration, debug input, waiting behavior, and hold-to-exit.
Visually inspect Home, the duration picker, active timer, small screens, and
enlarged text. A physical device is still needed to validate the light heuristic.
