#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE_FILE="$ROOT/android/app/build.gradle.kts"
WORKFLOW_FILE="$ROOT/.github/workflows/android-build.yml"
TEMP_NAV_WORKFLOW="$ROOT/.github/workflows/apply-2-2-navigation-fix.yml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'versionCode = 58' "$GRADLE_FILE" || fail 'Android versionCode muss 58 sein.'
grep -Fq 'versionName = "2.1.5"' "$GRADLE_FILE" || fail 'Android versionName muss 2.1.5 sein.'
grep -Fq 'EXPECTED_BACKEND_VERSION: "2.1.0"' "$WORKFLOW_FILE" || fail 'Backend-Vertrag muss bei 2.1.0 bleiben.'

if grep -Fq 'versionCode = 57' "$GRADLE_FILE"; then
  fail 'Alter versionCode 57 darf im Release-Kandidaten nicht mehr aktiv sein.'
fi
if grep -Fq 'versionName = "2.1.4"' "$GRADLE_FILE"; then
  fail 'Alte versionName 2.1.4 darf im Release-Kandidaten nicht mehr aktiv sein.'
fi
if [ -e "$TEMP_NAV_WORKFLOW" ]; then
  fail 'Temporärer Navigations-Reparaturworkflow darf im Release-Kandidaten nicht enthalten sein.'
fi

echo 'PASS: Android 2.1.5/code58 mit Backend-Vertrag 2.1.0 und bereinigtem Release-Branch.'
