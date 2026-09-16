#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root_dir"

test -x ./gradlew
test -f gradle/wrapper/gradle-wrapper.jar
test -f .github/workflows/pr-checks.yml
test -f .github/workflows/release.yml
test -f Dockerfile
test -f .gitleaks.toml

./gradlew --no-daemon help >/dev/null
./gradlew --no-daemon :app:tasks --group verification >/dev/null

echo "Infrastructure smoke test passed. It validates tooling only; no product behavior exists yet."
