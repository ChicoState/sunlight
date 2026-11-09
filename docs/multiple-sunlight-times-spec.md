# Multiple Sunlight Times

## Objective

Allow a user to save multiple daily Sunlight Times. Home shows only the next
upcoming occurrence, while Alarms shows the complete daily schedule and provides
the controls for adding or editing times. Each occurrence starts a fresh sunlight
check, including when a previous sunlight countdown is still running.

This remains a foreground prototype. Times do not register Android alarms, wake a
closed app, block other apps, or run camera work while the activity is stopped.

## Commands

- Format: `./gradlew --no-daemon ktfmtFormat`
- Format check: `./gradlew --no-daemon ktfmtCheck`
- Verify: `./gradlew --no-daemon verify`
- Instrumented UI tests: `./gradlew --no-daemon :app:connectedDebugAndroidTest`
- Infrastructure smoke test: `./scripts/infrastructure-smoke.sh`

## Project Structure

- `app/src/main/java/edu/sunlight/screentime/` contains schedule logic, DataStore
  integration, routine state, and Compose UI.
- `app/src/test/` contains deterministic schedule and routine unit tests.
- `app/src/androidTest/` contains Compose and Android picker/persistence tests.
- `docs/` records the user flow and prototype limitations.

## Code Style

Use immutable, normalized `List<LocalTime>` values at the UI and scheduling
boundaries. Times are unique and sorted chronologically before they are stored or
displayed.

```kotlin
val times = normalizeSunlightTimes(listOf(LocalTime.of(13, 0), LocalTime.of(8, 0)))
// 8:00 AM, 1:00 PM
```

## Testing Strategy

- Unit-test next-occurrence selection, day wrapping, corrupt persisted values, and
  resetting an active countdown for a later scheduled check.
- Compose-test Home navigation and the complete Alarms schedule.
- Instrumentation-test the native time picker and persistence where an emulator is
  available.

## Boundaries

- Always preserve the existing saved wake-up time through a one-time migration.
- Always keep camera permission and camera startup delayed until a scheduled time.
- Ask before adding Android background alarms, device/app restrictions, or new
  dependencies.
- Never claim that this foreground schedule rings alarms or enforces a device lock.

## Success Criteria

- Users can add multiple unique daily Sunlight Times with the existing native time
  picker and edit or remove a time from the Alarms screen.
- Home shows only the next upcoming Sunlight Time and provides an Add Sunlight Time
  button that navigates to Alarms.
- Alarms shows every saved time in chronological order.
- Saved times survive activity recreation and the old single wake-up time migrates
  without data loss.
- Every scheduled occurrence clears any partial light check or countdown and
  requires sustained bright light again before starting a fresh timer.
- Existing duration, camera-permission, debug, and early-exit behavior remains
  intact.

## Assumptions

- Sunlight Times repeat every local calendar day.
- When the final time for today has passed, the next occurrence is tomorrow's first
  time.
- Editing a time to match another saved time deduplicates the schedule.
- Schedule editing is disabled while the foreground routine is actively waiting,
  checking light, or counting down.
