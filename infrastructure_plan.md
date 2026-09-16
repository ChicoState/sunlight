# Infrastructure Plan

> Planning only. This document describes future infrastructure work. No installations, configuration changes, containers, workflows, deployments, or other implementation files were created by the infrastructure-planning process.

## 1. Project and User Experience

- **Application:** Android-only device-centered application.
- **Primary users:** Individual mobile users.
- **Primary user task:** Uses Android-native device capabilities in an installed mobile application.
- **Selected platform:** Native Android.
- **User-experience rationale:** Native Android supplies direct, well-supported access to Android capabilities.
- **Required operating systems, browsers, or devices:** Android devices; an Android emulator is suitable for development and automated UI tests.
- **Offline or native-device requirements:** Self-contained operation; native Android device capability access; no account or backend.

## 2. Connectivity and Application Shape

- **Connectivity model:** Self-contained.
- **Accounts and authentication:** None.
- **Backend required:** No.
- **Cross-device persistence:** No; data remains on the device.
- **Interaction between accounts:** None.
- **Primary application components:** Jetpack Compose UI, Kotlin application logic, Android device integrations, and DataStore preferences.

## 3. Selected Technology Stack

| Area | Selected technology | Purpose | Version policy |
|---|---|---|---|
| Primary language | Kotlin | Android application code | Current stable Android-supported Kotlin release |
| Application framework | Jetpack Compose | Declarative native Android UI | Current stable AndroidX release compatible with Kotlin |
| Runtime or SDK | Android SDK | Platform APIs, build tools, emulator, and device tooling | Stable API level and build tools supported by Android Studio |
| Package manager | Gradle with version catalog | Resolves and locks JVM/Android dependencies | Gradle version supported by Android Gradle Plugin |
| Build or packaging tool | Android Gradle Plugin | Debug/release APK builds | Stable version compatible with selected Gradle/Kotlin |

## 4. Storage and Persistence

- **Storage model:** Local.
- **Primary data store:** Jetpack DataStore Preferences for small settings, flags, and simple saved state.
- **User files or object storage:** None planned.
- **Local-development storage:** App-private DataStore on the emulator or test device.
- **Production hosting model:** None.
- **Schema and migration approach:** Use DataStore preference keys; provide explicit one-time migration code if a persisted key is renamed or format changes.
- **Backup, export, or recovery approach:** Android device backup behavior is not relied on; clearing app data resets local preferences.
- **Secrets and connection-string approach:** No backend connection strings or application secrets; do not hard-code any future third-party credentials.
- **Reason this storage fits the access pattern:** The confirmed scope only needs small, device-local preferences and has no sharing or synchronization.

## 5. Testing Tools

| Test layer | Tool or library | Planned scope | Planned execution point |
|---|---|---|---|
| Unit | JUnit with Kotlin test support | Application logic and DataStore-adjacent behavior | Local and pull requests |
| Integration | AndroidX Test | Android framework interactions where needed | Local and pull requests |
| End-to-end or UI | Jetpack Compose UI Test on an emulator | Important screen interactions and primary user flows | Pull requests and release validation |

## 6. Test Analysis

| Capability | Tool | Planned policy |
|---|---|---|
| Coverage | JaCoCo | Collect unit-test coverage in CI |
| Coverage threshold or regression rule | JaCoCo verification | Block pull requests when coverage falls below a modest baseline agreed before implementation |
| Flaky-test or duration analysis | Gradle test reports and GitHub Actions artifacts | Review failures and slow/flaky tests from CI artifacts |
| Reporting | GitHub Actions summary and uploaded JaCoCo report | Preserve coverage report when checks fail |

## 7. Static Analysis and Security

| Check | Tool | Planned enforcement |
|---|---|---|
| Formatting | ktfmt | Verify on pull requests; developers may format locally |
| Linting | Android Lint | Block pull requests on applicable errors |
| Type checking or compiler warnings | Kotlin compiler and Android Gradle Plugin | Treat new warnings according to project policy; compilation must pass |
| Anti-pattern or maintainability analysis | Detekt | Block pull requests on agreed ruleset violations |
| Dependency vulnerability scanning | Dependabot | Open update pull requests; review before merge |
| Secret scanning | Gitleaks | Block pull requests when committed secrets are detected |
| Static security analysis | CodeQL for Java/Kotlin | Run on pull requests and scheduled scans; address actionable findings |

## 8. Development Technologies Requiring Manual Installation

These are developer-workstation prerequisites that will not be supplied by the planned Docker environment.

| Technology | Why it is needed | Required on which machines | Version policy | Planned installation or verification method | Why Docker does not provide it |
|---|---|---|---|---|---|
| Git | Source control and GitHub workflow | All developer and CI environments | Supported current stable release | Future workstation setup documentation | Host credential integration and normal developer workflow |
| Android Studio | IDE, Android SDK manager, emulator management, and debugging | Developer workstations | Current stable release | Future Android developer setup documentation | Native IDE and emulator management |
| Android SDK and platform/build tools | Compile, package, debug, and test Android app | Developer workstations and Android CI runners | API/build-tools versions selected by project | Future SDK Manager setup | Emulator/device tooling needs host integration |
| JDK | Runs Gradle and Android build tools | Developer workstations and CI | Android Gradle Plugin-supported LTS JDK | Future documented setup or Android Studio bundle verification | Native build/IDE integration |
| Android emulator or physical device | Compose UI test and device-capability validation | Developer workstations | Matching supported Android API level | Future AVD/device setup | Hardware acceleration and USB/device access |
| Android signing keystore tools | Sign release APK | Authorized release workstation/CI only | Compatible Android SDK tooling | Future secure release setup | Signing material must remain protected outside images |
| Docker Desktop or Docker Engine | Reproducible command-line build environment | Developers using the container workflow | Supported stable release | Future Docker setup documentation | Docker itself is the host runtime |

### Host tools intentionally not required

- **Not required because Docker supplies them:** The selected container image will supply its pinned command-line JDK, Gradle environment, and Android command-line SDK components.
- **Not required for this platform:** iOS/Xcode tooling, backend runtime, database server, and production container host.

## 9. Docker Plan

- **Planned Docker role:** Reproducible command-line build environment.
- **Future files that would be created during implementation:** `Dockerfile`, `.dockerignore`, and optional Compose file only if a local developer wrapper proves useful.
- **Planned images and services:** One Android command-line build image; no database or backend services.
- **Development container behavior:** Run Gradle compile, unit-test, static-analysis, and packaging validation tasks with pinned SDK/JDK tooling; Compose UI tests remain on native Android runners with an emulator.
- **Ports:** None.
- **Bind mounts and named volumes:** Bind-mount source for local checks; use named Gradle caches where safe; never mount signing keys by default.
- **Environment-variable and secret handling:** Pass only non-secret build settings; use CI secrets for release signing and never embed them in images.
- **Local database or service containers:** None.
- **Production image or non-container release path:** Build a signed Android APK natively or in CI, then attach it to a GitHub Release; no production container.
- **Build stages and hardening:** Use a minimal pinned build image, non-root user where compatible, `.dockerignore`, no embedded secrets, and no claim that Docker replaces Android emulators or signing environments.
- **Planned future development command:** `docker build -t android-build-env .` and an explicitly documented `docker run` Gradle command.
- **Planned future production or packaging command:** A documented Gradle release-APK task executed only after signing configuration exists.

## 10. GitHub Actions Plan

### A. Automated pull-request checks

- **Future workflow file:** `.github/workflows/pr-checks.yml`
- **Trigger:** `pull_request`.
- **Runner or matrix:** Ubuntu latest for Kotlin/JVM checks; Android-capable Linux runner with hardware acceleration for Compose UI tests when practical.
- **Permissions:** Read-only `contents`; grant only the minimum additional permissions required by CodeQL or artifact upload.
- **Planned jobs in order:**
  1. Checkout, install the pinned JDK, restore Gradle cache, and perform lockfile-enforced dependency resolution.
  2. Verify ktfmt; run Android Lint, Detekt, Kotlin compilation, unit tests, and JaCoCo coverage verification.
  3. Build a debug APK for package validation.
  4. Run Compose UI tests on an emulator when runner capacity permits; upload test reports, screenshots, logs, and traces on failure.
  5. Run Gitleaks and CodeQL; Dependabot supplies dependency-update pull requests separately.
- **Service containers:** None.
- **Caching:** Cache Gradle wrapper and dependency caches keyed by Gradle files and lockfiles; do not cache secrets or release artifacts.
- **Coverage and analysis reporting:** Upload JaCoCo HTML/XML reports and job summaries; enforce the agreed modest threshold.
- **Failure artifacts:** JaCoCo report, Gradle test reports, Android Lint/Detekt reports, emulator logs, and UI-test screenshots/traces when available.
- **Checks that should block merging:** Formatting, compilation, Android Lint, Detekt, unit tests, coverage threshold, debug build, Gitleaks, and applicable CodeQL findings; UI tests when configured as required.
- **Proposed branch-protection settings:** Require the listed status checks, at least one approving review, up-to-date branches, and no force pushes to the protected branch.

### B. New-release deployment

- **Future workflow file:** `.github/workflows/release.yml`
- **Release trigger:** A pushed `v*` tag, with `workflow_dispatch` retained for a controlled retry.
- **Release destination:** GitHub Releases with signed APK artifacts.
- **Runner or matrix:** Ubuntu latest with Android SDK/JDK setup; use a protected release environment.
- **Planned jobs in order:**
  1. Checkout the tag and rerun formatting, analysis, unit tests, coverage verification, UI tests where practical, and release build validation.
  2. Retrieve protected signing material, build and verify the signed release APK, then generate checksums.
  3. Create or update the GitHub Release, attach the signed APK and checksum, publish release notes, and retain artifacts.
- **Build artifacts:** Signed release APK and SHA-256 checksum file.
- **Signing, notarization, or store requirements:** Android signing keystore, key alias, and protected passwords; no notarization or store credentials for GitHub Release distribution.
- **Database migration step:** None.
- **Environment approval:** Require an authorized maintainer approval through a protected GitHub `release` environment.
- **Post-deployment verification:** Download the release artifact and validate its checksum; manually install on a test Android device before announcing broadly.
- **Failed-release or rollback approach:** Mark the GitHub Release as draft/pre-release or remove the affected asset, publish a corrected signed version with a new tag, and communicate the replacement version.

### GitHub configuration required later

| Name | Type | Purpose |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | Secret | Base64-encoded Android release keystore for the release job |
| `ANDROID_KEY_ALIAS` | Secret | Alias of the release-signing key |
| `ANDROID_KEYSTORE_PASSWORD` | Secret | Password to unlock the keystore |
| `ANDROID_KEY_PASSWORD` | Secret | Password for the signing key |
| `release` | GitHub environment | Protects publishing with reviewer approval and scoped secrets |
| GitHub Actions token | Token | Creates the GitHub Release and uploads artifacts using least privilege |

## 11. Planned Repository Artifacts - Not Created by This Skill

- [ ] Application manifest or project file: Gradle settings, app module, and Android manifest.
- [ ] Lockfile: Gradle dependency locking files or version-catalog lock configuration.
- [ ] Test configuration: JUnit, AndroidX Test, Compose UI Test, and JaCoCo configuration.
- [ ] Static-analysis configuration: ktfmt, Detekt, Android Lint, Gitleaks, and CodeQL settings.
- [ ] Docker or Compose files: `Dockerfile`, `.dockerignore`, and optional Compose wrapper.
- [ ] `.github/workflows/pr-checks.yml`: Pull-request validation workflow.
- [ ] `.github/workflows/release.yml`: Signed APK GitHub Release workflow.
- [ ] Deployment or store configuration: Release signing configuration and GitHub environment setup.

## 12. Assumptions and Open Items

- **Assumptions:** The app’s saved state remains small enough for DataStore Preferences; it does not need media/file storage, accounts, synchronization, or backend services.
- **Decisions still requiring an external account, credential, certificate, or organizational approval:** An authorized GitHub maintainer must create and protect the Android signing keystore and release-environment secrets.
- **Items to confirm before implementation begins:** Target/minimum Android API levels, specific device capabilities, the JaCoCo coverage baseline, final APK package/application ID, and whether UI tests are required on every pull request.
