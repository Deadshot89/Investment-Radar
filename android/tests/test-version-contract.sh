#!/usr/bin/env bash
set -euo pipefail

# Final Integration Contract for the current Android release candidate.
# Gradle is the source of truth for Android version metadata.
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
GRADLE_FILE="$ROOT/android/app/build.gradle.kts"
WORKFLOW_FILE="$ROOT/.github/workflows/android-build.yml"
TEMP_NAV_WORKFLOW="$ROOT/.github/workflows/apply-2-2-navigation-fix.yml"
TEMP_DETAIL_WORKFLOW="$ROOT/.github/workflows/apply-2-2-detail-cleanup.yml"
TEMP_TASK10_WORKFLOW="$ROOT/.github/workflows/task10-ui-patch.yml"
TEMP_MONEY_WORKFLOW="$ROOT/.github/workflows/apply-money-management.yml"
TEMP_MONEY_SCRIPT="$ROOT/.github/scripts/apply_money_management.py"
TEMP_PREFILL_WORKFLOW="$ROOT/.github/workflows/apply-action-prefill.yml"
TEMP_PREFILL_SCRIPT="$ROOT/.github/scripts/apply_action_prefill.py"
TEMP_SHARE_PREFILL_WORKFLOW="$ROOT/.github/workflows/apply-recommendation-share-prefill.yml"
TEMP_SHARE_PREFILL_SCRIPT="$ROOT/.github/scripts/apply_recommendation_share_prefill.py"
EXPECTED_VERSION_NAME="2.5.36"
EXPECTED_VERSION_CODE=106

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

VERSION_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -1)
VERSION_CODE=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)
[ -n "$VERSION_NAME" ] || fail 'Android versionName konnte nicht aus build.gradle.kts gelesen werden.'
[ -n "$VERSION_CODE" ] || fail 'Android versionCode konnte nicht aus build.gradle.kts gelesen werden.'

if [ "$VERSION_CODE" -ne "$EXPECTED_VERSION_CODE" ]; then
  fail "Android versionCode muss für diesen Release-Kandidaten exakt $EXPECTED_VERSION_CODE sein (gefunden: $VERSION_CODE)."
fi
if [ "$VERSION_NAME" != "$EXPECTED_VERSION_NAME" ]; then
  fail "Android versionName muss für diesen Release-Kandidaten exakt $EXPECTED_VERSION_NAME sein (gefunden: $VERSION_NAME)."
fi

grep -Fq "Release candidate: Investment Radar $VERSION_NAME" "$GRADLE_FILE" || fail "Release-Kandidat muss die aktuelle Android-Version $VERSION_NAME benennen."
grep -Fq 'EXPECTED_BACKEND_VERSION: "2.1.0"' "$WORKFLOW_FILE" || fail 'Backend-Vertrag muss bei 2.1.0 bleiben.'

if grep -Fq 'versionCode = 80' "$GRADLE_FILE"; then
  fail 'Alter versionCode 80 darf im Release-Kandidaten nicht mehr aktiv sein.'
fi
if grep -Fq 'versionName = "2.5.10"' "$GRADLE_FILE"; then
  fail 'Alte versionName 2.5.10 darf im Release-Kandidaten nicht mehr aktiv sein.'
fi
for temp_artifact in "$TEMP_NAV_WORKFLOW" "$TEMP_DETAIL_WORKFLOW" "$TEMP_TASK10_WORKFLOW" "$TEMP_MONEY_WORKFLOW" "$TEMP_MONEY_SCRIPT" "$TEMP_PREFILL_WORKFLOW" "$TEMP_PREFILL_SCRIPT" "$TEMP_SHARE_PREFILL_WORKFLOW" "$TEMP_SHARE_PREFILL_SCRIPT"; do
  if [ -e "$temp_artifact" ]; then
    fail "Temporäres Reparaturartefakt darf im Release-Kandidaten nicht enthalten sein: $temp_artifact"
  fi
done

echo "PASS: Android $VERSION_NAME/code$VERSION_CODE mit Backend-Vertrag 2.1.0 und bereinigtem Release-Branch."
