# ADR-003: Use a user-approved overlay for the blue-light filter

## Status

Accepted.

## Context

An active morning routine needs a strong blue-light-reduction effect that stays
visible after Sunlight moves to the background. A regular activity window cannot
remain above other applications, and third-party applications cannot change the
device's global display color calibration.

## Decision

Offer Android's user-managed `SYSTEM_ALERT_WINDOW` approval from an explicit
action during a normal routine. Also offer to open Android's user-managed Night
Display settings, where the user can enable the built-in filter and choose its
intensity. While the routine is active and Sunlight is backgrounded, start a
non-sticky foreground service after that approval which displays a semi-transparent,
non-touchable amber `TYPE_APPLICATION_OVERLAY`. The overlay uses alpha 180/255,
reducing blue light while preserving some visibility of the underlying app. The
notification includes a Turn off action. Remove
the overlay when the routine ends, the user exits early, or the app task is
removed. Do not use the overlay during debug simulations.

## Consequences

The effect remains over other applications but does not block input or claim to
alter Android's global Night Light setting or hardware color output. Android may
hide the overlay when needed, and users must explicitly approve the special
permission. The foreground-service notification makes its background operation
visible and controllable.
