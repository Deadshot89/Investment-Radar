#!/usr/bin/env bash
set -euo pipefail

# Final Integration Contract für Investment Radar 2.5.15 / Android 2.5.15.
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

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'versionCode = 85' "$GRADLE_FILE" || fail 'Android versionCode muss 85 sein.'
grep -Fq 'versionName = "2.5.15"' "$GRADLE_FILE" || fail 'Android versionName muss 2.5.15 sein.'
grep -Fq 'Release candidate: Investment Radar 2.5.15' "$GRADLE_FILE" || fail 'Release-Kandidat muss 2.5.15 benennen.'
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

echo 'PASS: Android 2.5.15/code85 mit Backend-Vertrag 2.1.0 und bereinigtem Release-Branch.'
