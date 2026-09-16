#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
API="android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt"

test -f "$RADAR"
grep -q 'RadarScreenV2(' "$MAIN"
grep -q 'val personalPlan = RecommendationEngine.plan' "$MAIN"
grep -q 'personalById = personalById' "$MAIN"
! grep -q 'private enum class RadarSortOption' "$MAIN"
! grep -q 'focusItemId: String?' "$MAIN"
! grep -q 'private fun RadarScreen(' "$MAIN"

grep -Fq 'val refresh: Boolean = false' "$MODELS"
grep -Fq 'if (query.refresh) add("refresh" to "true")' "$API"
grep -Fq 'Text(if (hardRefreshing) "Fehlende Daten werden neu geladen…" else "Fehlende Daten neu laden")' "$RADAR"
grep -Fq 'refresh = hardRefresh' "$RADAR"
grep -Fq 'Datenquellen neu geladen' "$RADAR"
echo "PASS Radar hard refresh is wired from UI to API query"
