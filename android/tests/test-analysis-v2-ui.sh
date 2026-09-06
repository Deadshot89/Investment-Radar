#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/Models.kt"
RADAR_MODELS="android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt"
API="android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
CARD="android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt"
FILTERS="android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt"

grep -q 'scoreTotal' "$MODELS"
grep -q 'PortfolioAdvisorEngine.allocate' "$SRC"
grep -q 'Datenabdeckung' "$CARD"
grep -q 'Qualität' "$CARD"
grep -q 'Bewertung' "$CARD"
grep -q 'Wachstum' "$CARD"
grep -q 'Momentum' "$CARD"

test -f "$RADAR"
test -f "$RADAR_MODELS"
grep -q 'Name, Ticker oder ISIN suchen' "$RADAR"
grep -q 'RADAR 2.1' "$RADAR"
grep -q 'RadarRecommendationFilter' "$RADAR"
grep -q 'RadarHoldingFilter' "$RADAR"
grep -q 'RadarDataQualityFilter' "$RADAR"
grep -q 'RadarRiskFilter' "$RADAR"
grep -q 'RadarQuery(' "$RADAR"
grep -q 'ApiClient.loadRadarPage' "$RADAR"
grep -q 'Weitere 40 Ergebnisse laden' "$RADAR"
grep -q 'onOpenDetail' "$RADAR"
grep -q 'FilterGroup("SORTIERUNG")' "$RADAR"
grep -q 'Beste Chancen' "$RADAR"
grep -q 'Momentum' "$RADAR"
grep -q 'Name' "$RADAR"
grep -q 'filters = filters.copy(sort = option); requestedPage = 1' "$RADAR"
grep -q 'data class RadarPage' "$RADAR_MODELS"
grep -q 'loadRadarPage' "$API"
grep -q 'loadRadarDetail' "$API"
grep -q 'RadarFilterEngine' "$FILTERS"

grep -q 'RadarScreenV2(' "$SRC"
grep -q 'val advisorPlan = PortfolioAdvisorEngine.allocate' "$SRC"
grep -q 'val advisorById = advisorPlan.candidates.associateBy' "$SRC"
! grep -q 'RecommendationEngine.plan' "$SRC"
! grep -q 'private enum class RadarSortOption' "$SRC"
! grep -q 'focusItemId: String?' "$SRC"
! grep -q 'private fun RadarScreen(' "$SRC"

bash android/tests/test-score-null-display.sh
bash android/tests/test-investment-detail-ui.sh
bash android/tests/test-investment-detail-wiring.sh

echo "PASS Analysis V2 remains wired through Radar 2.1 and Advisor 2.3"
