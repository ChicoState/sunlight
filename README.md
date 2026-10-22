# Sunlight

Sunlight is an Android morning-routine prototype. The intended experience is a wake-up alarm followed by a user-selected duration of regularly checked sunlight, with only user-allowed apps available until completion and a hold-to-unlock emergency exit. The current preview checks camera brightness once and then runs a timer; alarm ringing, app restrictions, and repeated sunlight checks are not implemented yet.

## Repository map

- `app/` — Android module, Compose UI, foreground camera preview flow, and tests.
- `gradle/`, `build.gradle.kts`, and `settings.gradle.kts` — Gradle wrapper and build configuration.
- `scripts/` — Infrastructure-only smoke test.
- `Dockerfile` — Reproducible command-line Android build environment.
- `.github/workflows/` — Pull-request and guarded release workflows.
- `infrastructure_plan.md` — Selected infrastructure decisions.
- `AGENTS.md` — Contributor and agent guidance.

## Getting Started

1. Install [Git](https://git-scm.com/downloads), [Android Studio](https://developer.android.com/studio), and Docker Desktop/Engine if using Docker. Install Android API 36, Build Tools 36.0.0, an API 36 emulator image, and use JDK 17.
2. Open the repository in Android Studio and set the Gradle JDK to 17.
3. Run `chmod +x gradlew scripts/infrastructure-smoke.sh`.
4. Run `./gradlew --no-daemon verify` after Android SDK setup, or use Docker below.

No `.env` file or backend configuration is required. Do not commit signing keys; local SDK paths belong in ignored `local.properties`.

## Commands

| Command | Purpose |
|---|---|
| `./gradlew --no-daemon ktfmtCheck` | Verify Kotlin and Gradle-script formatting. |
| `./gradlew --no-daemon verify` | Run static checks, test harness, coverage configuration, and debug package validation. |
| `./scripts/infrastructure-smoke.sh` | Confirm the wrapper, workflows, Dockerfile, and Gradle configuration load. |
| `docker build --tag android-build-env:local .` | Build the pinned Android development image. |
| `docker run --rm --user root -v "$PWD":/workspace -w /workspace android-build-env:local --no-daemon verify` | Run full verification in Docker. |

The app is self-contained: it has no accounts, backend, location collection, or network use. Camera frames are analyzed in memory only; it never records, stores, or transmits photos or video. A routine preview consumes battery because the screen stays on. Android system navigation remains available and the app provides a hold-to-exit escape action; it is not a device lock.

On Home, edit your wake-up time and sunlight duration in the same morning panel, then select **Try morning routine**. Duration defaults to 15 minutes and can be set from 1–120 minutes; it is saved locally and cannot change mid-routine. The app waits until the configured wake-up time (tomorrow if it has passed) before requesting camera permission or opening the camera. Sustained bright light starts the selected timer, then the camera stops. Bright artificial light can also satisfy this heuristic. No alarm will ring.

Debug builds also show **Debug: simulate sunlight**. It starts an immediate, clearly marked test without changing the saved wake-up time or requesting camera permission. Simulated brightness readings pass through the same sustained-light detector and timer transition as real camera frames. Use a one-minute duration to try the whole countdown, completion, and hold-to-exit flow. The test button and its handler are disabled in non-debuggable builds.

During an active debug test, the **Time speed** slider adjusts simulated time from 1×–100×. Changes apply while dragging, preserving elapsed progress and affecting both light detection and the countdown. Each new test starts at 1×; normal routines always use real time. At 100×, a 15-minute countdown takes about nine real seconds.

The bottom navigation is Home, Blocked apps, Alarms, and Settings, with Settings at the far right. The latter three pages are intentionally blank apart from their titles. Navigation stays visible and the hold-to-exit control remains accessible on every page during an active preview. The preview retains a black background, red text, and dimmed display. See [UI design notes](docs/ui-design.md) for the intended flow, current limitations, and UI test command.

## GitHub release setup

Create a protected `release` environment with `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` secrets. The release workflow intentionally stops before publishing until a later application task wires secure APK signing into the module.

## Troubleshooting

- **Android SDK missing:** Install API 36 and Build Tools 36.0.0 with Android Studio’s SDK Manager, then create ignored `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.
- **Wrong Java version:** Set Android Studio’s Gradle JDK or `JAVA_HOME` to JDK 17.
- **Docker unavailable:** Start Docker Desktop/Engine, then rerun the Docker build command.
- **First Docker verification is slow:** Gradle downloads Android and Compose dependencies on its first run; allow several minutes.
