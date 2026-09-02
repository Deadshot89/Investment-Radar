#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
ENGINE="android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt"

grep -q 'val personalPlan = RecommendationEngine.plan' "$SRC"
grep -q 'PortfolioAnalysis.values(s.data.items, positions, customItems)' "$SRC"
grep -q 'personalPlan = personalPlan' "$SRC"
grep -q 'personalPlan: PersonalPlan' "$SRC"
grep -q 'cashAmount' "$SRC"
grep -q 'DIESEN MONAT WARTEN' "$SRC"
grep -q 'currentWeightPct' "$ENGINE"

COUNT=$(grep -c 'RecommendationEngine.plan' "$SRC" || true)
if [ "$COUNT" -ne 1 ]; then
  echo "Expected exactly one shared RecommendationEngine.plan call, found $COUNT"
  exit 1
fi

echo "PASS shared portfolio-aware personal plan"
