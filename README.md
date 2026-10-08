# Sunlight

Sunlight is an Android Focus app that keeps a dim red, screen-awake session open until a user-selected morning time. It then uses live front-camera brightness analysis and runs a final 15-minute countdown after bright light is detected.

## Repository map

- `app/` — Android module configuration and an intentionally entrypoint-free manifest.
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

The app is self-contained: it has no accounts, backend, location collection, or network use. Camera frames are analyzed in memory only; it never records, stores, or transmits photos or video. A Focus session consumes battery because the screen stays on. Android system navigation remains available and the app provides a hold-to-end escape action; it is not a device lock.

On first use, select the local time when camera checking should begin. The app requests camera permission when Focus starts. After the configured time, move the phone into direct sunlight; a brightness heuristic starts a fixed 15-minute final countdown. Bright artificial light can also satisfy the heuristic.

## GitHub release setup

Create a protected `release` environment with `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` secrets. The release workflow intentionally stops before publishing until a later application task wires secure APK signing into the module.

## Troubleshooting

- **Android SDK missing:** Install API 36 and Build Tools 36.0.0 with Android Studio’s SDK Manager, then create ignored `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.
- **Wrong Java version:** Set Android Studio’s Gradle JDK or `JAVA_HOME` to JDK 17.
- **Docker unavailable:** Start Docker Desktop/Engine, then rerun the Docker build command.
- **First Docker verification is slow:** Gradle downloads Android and Compose dependencies on its first run; allow several minutes.
