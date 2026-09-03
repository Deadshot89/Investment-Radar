#!/usr/bin/env bash
set -euo pipefail

MODELS="android/app/src/main/java/de/tobias/investmentradar/Models.kt"
API="android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarFilterState.kt"
RECO="android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt"

grep -q 'val portfolioOnly: Boolean' "$MODELS"
grep -q 'portfolioOnly = o.optBoolean("portfolioOnly", false)' "$API"
grep -q '!it.portfolioOnly' "$RADAR"
grep -q '!item.portfolioOnly' "$RECO"

echo "PASS portfolio-only assets are parsed and excluded from recommendation surfaces"
