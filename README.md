# Sunlight

Android infrastructure is ready for application development; production application code and user-facing screens have not been created yet.

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

The test commands currently validate the harness only: application code and UI tests have intentionally not been added.

## GitHub release setup

Create a protected `release` environment with `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, `ANDROID_KEYSTORE_PASSWORD`, and `ANDROID_KEY_PASSWORD` secrets. The release workflow intentionally stops before publishing until a later application task wires secure APK signing into the module.

## Troubleshooting

- **Android SDK missing:** Install API 36 and Build Tools 36.0.0 with Android Studio’s SDK Manager, then create ignored `local.properties` containing `sdk.dir=/absolute/path/to/Android/Sdk`.
- **Wrong Java version:** Set Android Studio’s Gradle JDK or `JAVA_HOME` to JDK 17.
- **Docker unavailable:** Start Docker Desktop/Engine, then rerun the Docker build command.
- **First Docker verification is slow:** Gradle downloads Android and Compose dependencies on its first run; allow several minutes.
