# Agent Guidance

## Project status

This repository has an Android infrastructure foundation and a Compose morning-routine prototype. `infrastructure_plan.md` is the approved source of truth for platform, storage, quality, Docker, CI, and release decisions; revise it with `infra-planner` before changing those decisions. `docs/ui-design.md` records the intended alarm/sunlight/app-restriction flow and the current preview limitations.

## Repository map

- `app/` — Android module, SunlightActivity entrypoint, Compose UI, camera preview flow, and unit/instrumentation tests.
- `gradle/`, `build.gradle.kts`, `settings.gradle.kts` — Gradle toolchain and version catalog.
- `scripts/` — infrastructure-only smoke checks.
- `Dockerfile`, `.dockerignore` — reproducible command-line Android build environment; no services or production image.
- `.github/workflows/` — pull-request checks and guarded GitHub Release workflow.
- `README.md` and `infrastructure_plan.md` — developer setup and approved infrastructure decisions.
- `.agents/skills/` — project skills. No API/backend exists; alarms, app restrictions, and repeated sunlight checks are not implemented.

## Required workflow

Read `infrastructure_plan.md`, this file, and applicable `.agents/skills/*/SKILL.md` before changing files. Use `infra-planner` for infrastructure decisions, `infra-builder` for planned infrastructure, `frontend-ui-engineering` for UI, `test-driven-development` for behavior changes, `security-and-hardening` for untrusted input or secrets, `ci-cd-and-automation` for workflow changes, `documentation-and-adrs` for material decisions, `code-review-and-quality` before merging, and `git-workflow-and-versioning` for every change.

Do not add product behavior during infrastructure work, alter plan decisions without revising the plan, or commit secrets/signing material/build output. Keep UI claims accurate: the current foreground preview is not an alarm or device lock.

## Verification

Run `./gradlew --no-daemon ktfmtCheck`, `./gradlew --no-daemon verify`, and `./scripts/infrastructure-smoke.sh`. Docker validation uses `docker build --tag android-build-env:local .`; it does not replace Android Studio, an emulator/device, or release signing. Remove locally built images only if explicitly requested.

## Change checklist

1. Keep changes scoped and update relevant documentation.
2. Run the applicable verification commands after each increment.
3. Check `git diff --check` and inspect the staged diff before committing.
4. Update README and this file when commands, paths, or developer prerequisites change.
