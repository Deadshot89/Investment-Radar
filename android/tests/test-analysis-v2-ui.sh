#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/Models.kt"
CARD="android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt"
FILTERS="android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt"

grep -q 'scoreTotal' "$MODELS"
grep -q 'RecommendationEngine' "$SRC"
grep -q 'Datenabdeckung' "$CARD"
grep -q 'Qualität' "$CARD"
grep -q 'Bewertung' "$CARD"
grep -q 'Wachstum' "$CARD"
grep -q 'Momentum' "$CARD"

test -f "$RADAR"
grep -q 'Suchen nach Name, Ticker, ISIN oder Typ' "$RADAR"
grep -q 'RadarRecommendationFilter' "$RADAR"
grep -q 'RadarHoldingFilter' "$RADAR"
grep -q 'RadarDataQualityFilter' "$RADAR"
grep -q 'RadarRiskFilter' "$RADAR"
grep -q 'RadarSortMode.ALLOCATION' "$RADAR"
grep -q 'RadarSortMode.MOMENTUM_6M' "$RADAR"
grep -q 'RadarSortMode.DAY_ASC' "$RADAR"
grep -q 'RadarSortMode.DAY_DESC' "$RADAR"
grep -q 'onOpenDetail' "$RADAR"
grep -q 'RadarFilterEngine' "$FILTERS"

grep -q 'RadarScreenV2(' "$SRC"
grep -q 'val personalPlan = RecommendationEngine.plan' "$SRC"
grep -q 'val personalById = personalPlan.items.associateBy' "$SRC"
! grep -q 'private enum class RadarSortOption' "$SRC"
! grep -q 'focusItemId: String?' "$SRC"
! grep -q 'private fun RadarScreen(' "$SRC"

bash android/tests/test-score-null-display.sh
bash android/tests/test-investment-detail-ui.sh
bash android/tests/test-investment-detail-wiring.sh
