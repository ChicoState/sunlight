# ADR-004: Lock selected apps nightly until the morning routine completes

## Status

Accepted. Adds app restrictions to the routine described in ADR-002. The camera
privacy constraints of ADR-001 and the light input of ADR-002 are unchanged.

## Context

The user asked to "lock the selected apps at a given time at night. They will be
locked from that time until the sunlight check passes." The user then clarified
what "passes" means: apps stay locked until the sunlight timer **finishes**. In
code, that is the moment `MorningRoutine` reaches `FocusPhase.COMPLETE` after the
final countdown. Reaching `FINAL_COUNTDOWN` (bright light held for three
seconds) does not unlock apps. Neither does ending the routine early with
"Hold to exit early".

The lock must survive the app closing, process death, and reboot. It must never
stop someone using their phone for calls or recovery. There is no root access
and no backend. This is a class project that will never ship to the Play Store,
so Play's accessibility-use policy does not apply.

## Decision

### Detecting the foreground app: AccessibilityService

`AppLockAccessibilityService` subscribes only to `typeWindowStateChanged`, with
`canRetrieveWindowContent="false"`. It reads only the event's package name. If
that package is locked, it starts `AppBlockedActivity`, a full-screen Compose
block screen in its own task. The block screen is an Activity, not an overlay.
It offers **Go to home screen**, **Open Sunlight**, and the emergency unlock.
Back goes home rather than returning to the locked app.

Why this approach. Events arrive as soon as the app changes, so there is no
polling and very little battery cost. It needs no foreground service, no
persistent notification, and no `PACKAGE_USAGE_STATS` grant. The system rebinds
the service after process death and reboot. Accessibility services may also
start activities from the background. UsageStats polling was not chosen: it
needs a foreground service with a `specialUse` type, a notification permission,
and regular wake-ups, and it is less reliable.

### Lock rules (pure, JVM-tested `AppLockPolicy`)

- **Lock start `S`.** This is the most recent instant at or before now whose
  local time equals the lock time. If today's lock time is still ahead, `S` is
  yesterday's. This is why the lock continues across midnight. A lock time
  just after midnight locks the apps for the rest of that day.
- **Locked.** A package is locked when all of these are true: the nightly lock
  is enabled; the package is selected and not protected; `S` is not earlier
  than `armedAt`; and no routine completion or emergency unlock falls within
  `[S, now]`.
- **A completed routine before the lock time does not pre-unlock that night.**
  It falls before the new `S`. A timestamp after `now` (for example, after the
  clock moves back) is ignored.
- **The lock carries over.** With no completed routine, each new lock start
  keeps the apps locked, night after night.
- **Edits take effect from the next lock time.** Each saved edit sets
  `armedAt = now`, so saving settings never locks apps immediately.
- **Edits are frozen while locked.** This applies to the enable switch, lock
  time and app selection, and the repository also enforces it inside the
  DataStore transaction. As a result, the lock can only end with a completed
  routine or an emergency unlock.
- **DST and time zones.** The rules compare `Instant`s and resolve the local
  lock time in the current zone:
  - In a spring-forward gap, `java.time` moves a missing lock time to after
    the gap.
  - In a fall-back overlap, the lock uses the first occurrence.
  - Changing the time zone moves `S` to match the new local time.
- `nextLockAt` reuses `SessionPlanner.nextRelease`.

### Unlock trigger

`SunlightViewModel` records `last_routine_completed_at` only for
a real routine. The stored timestamp is the countdown deadline, so delayed resume or restoring an already completed routine cannot unlock a later night. A saved acknowledgement prevents replay; a repeated write after process death remains idempotent. The debug simulation never unlocks apps. An early
exit and a killed process write nothing.

### Safety

- **Never locked.** Sunlight itself, every home-screen app, `ACTION_DIAL`
  handlers, the default dialer, Settings, and a built-in list are never
  locked. The built-in list covers SystemUI, the phone, Telecom, emergency and
  safety apps, and common launchers. The policy checks this on every
  evaluation, not just when apps are picked.
- **Untrusted package names.** Names from PackageManager, DataStore and
  intent extras are validated against a package-name pattern and a length
  limit. The picker selection is also limited to currently launchable apps.
  The block screen reads the app label from PackageManager, not from the
  intent. The block Activity, both receivers and the alarm `PendingIntent`
  (immutable) are not exported. The service is exported only behind
  `BIND_ACCESSIBILITY_SERVICE`.
- **Emergency unlock.** Long-press (or the named accessibility action) starts a
  10-second countdown that a tap cancels. When the countdown finishes, the
  apps unlock until the next lock time. It is stored separately
  (`last_emergency_unlock_at`, `emergency_unlock_count`) and shown as
  "Emergency unlock used … This is not a completed routine." It is never
  recorded as a routine completion.

### Scheduling and persistence

- **Storage.** Settings live in a separate DataStore file, `app_lock`, apart
  from `focus_settings`. Times are stored as epoch milliseconds, and loaded
  values are validated.
- **Lock-time alarm.** `AppLockScheduler` sets an inexact
  `setAndAllowWhileIdle(RTC_WAKEUP)` alarm for the next lock time. When it
  fires, the service re-checks the app already in the foreground and schedules
  the next night. No exact-alarm permission is needed, because the alarm is
  only a nudge. Every app switch is checked against the stored state, so a
  late alarm only delays blocking an app that was already open. The service
  also re-checks on `USER_PRESENT` and `SCREEN_ON`.
- **Rescheduling.** `AppLockRescheduleReceiver` reschedules after
  `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED` and `MY_PACKAGE_REPLACED`.
  The lock state itself is already stored, so it survives a reboot.

## Consequences

- **Setup.** The user must turn on "Sunlight app lock" in Accessibility
  settings. The Blocked apps page shows a setup row that explains what the
  service can see, with links to Accessibility settings and App info. On
  Android 13 and later, a sideloaded APK may need **Allow restricted
  settings** first. When the service is off, the page says apps are not
  locked.
- **Not a device lock.** Turning off the service, uninstalling Sunlight,
  split-screen, picture-in-picture or notification actions can get around the
  lock. Bright artificial light can still pass the light check (ADR-002).
- **Interrupted routines.** The routine still runs in memory inside the
  Activity. If the process dies mid-countdown, the apps stay locked, and
  starting the routine again waits for the next wake-up time. In practice the
  user's way out that day is the emergency unlock until the routine state
  survives process death.
- **Known gaps.**
  - Right after boot, events that arrive before DataStore loads are not
    blocked. The first settings emission re-checks the foreground app.
  - The foreground app is tracked from window events, so a keyboard or system
    window can briefly hide the real foreground app from the alarm re-check.
- **Permissions and components.**
  - New permission: `RECEIVE_BOOT_COMPLETED`.
  - New manifest components: one accessibility service, two receivers, the
    block Activity, and `<queries>` for launcher, home, dial and Settings
    intents. `QUERY_ALL_PACKAGES` is not used.
  - No new dependencies.
- **Infrastructure plan.** No decision in `infrastructure_plan.md` changes. The
  feature fits "Android device integrations" and DataStore Preferences. Its
  section 2 and section 5 could add a note that the release check includes a
  manual device test of the accessibility-based lock. Emulator UI tests cover
  the screens, not system-level blocking.
