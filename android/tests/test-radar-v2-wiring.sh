#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"

test -f "$RADAR"
grep -q 'RadarScreenV2(' "$MAIN"
grep -q 'personalById = RecommendationEngine' "$MAIN"
! grep -q 'private enum class RadarSortOption' "$MAIN"
! grep -q 'focusItemId: String?' "$MAIN"
! grep -q 'private fun RadarScreen(' "$MAIN"
