# ADR-003: Store a recurring daily Sunlight Time schedule

## Status

Accepted. Supersedes ADR-002's single wake-up-time scheduling decision.

## Date

2026-11-06

## Context

The foreground prototype previously supported one saved wake-up time and one
sunlight check. Users need several user-selected times throughout each day. Home
should stay focused on the next occurrence, while Alarms should manage the full
schedule. Existing installations must keep their saved wake-up time.

## Decision

- Store unique times as minute-of-day strings in the `sunlight_times` DataStore
  string set. Normalize them into a sorted `List<LocalTime>` at the application
  boundary.
- If the new key is absent, migrate valid `release_hour` and `release_minute`
  values into the schedule and remove the legacy keys.
- Resolve the next occurrence in the device's current time zone. Once today's
  final time has passed, choose tomorrow's first time.
- Keep the current foreground lifecycle model. Starting the routine opts into the
  recurring schedule while the activity remains available. Every due occurrence
  clears the countdown and partial light state before requesting sunlight again.
- Show only the next occurrence on Home. Use Alarms for the chronological list,
  native picker actions that add or edit times, and an accessible trash action
  that removes a time.

## Alternatives Considered

### Android alarm scheduling

Rejected for this increment because it introduces background execution, exact
alarm permission behavior, notifications, and process-restoration requirements.
The UI must continue to describe the feature as a foreground preview.

### One DataStore key per time

Rejected because indexed keys complicate insertion, deletion, and migration. A
bounded set of minute-of-day values naturally deduplicates this small schedule.

## Consequences

- Existing users retain their one saved time without manual re-entry.
- A time that occurs during an active countdown starts a fresh sunlight check.
- Time edits are disabled during active waiting, checking, and countdown phases.
- Closing the app or stopping the activity still prevents scheduled checks; a
  future Android alarm/background design is required for reliable prompting.
