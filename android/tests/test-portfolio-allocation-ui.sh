#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

# Es gibt genau einen automatischen AdvisorPlan; manuelle Overrides liegen transparent darüber.
grep -q 'val automaticAdvisorPlan = PortfolioAdvisorEngine.allocate' "$SRC"
grep -q 'val advisorPlan = RecommendationOverrideEngine.apply' "$SRC"
grep -q 'PortfolioAnalysis.values(s.data.items, positions, customItems)' "$SRC"
grep -q 'SavingsPlanBudget.monthlyAmounts' "$SRC"
grep -q 'advisorPlan = advisorPlan' "$SRC"
grep -q 'advisorById = advisorById' "$SRC"

COUNT=$(grep -c 'PortfolioAdvisorEngine.allocate' "$SRC" || true)
if [ "$COUNT" -ne 1 ]; then
  echo "Expected exactly one shared PortfolioAdvisorEngine.allocate call, found $COUNT"
  exit 1
fi

if grep -q 'RecommendationEngine.plan' "$SRC"; then
  echo "Legacy RecommendationEngine.plan must not drive the root UI"
  exit 1
fi

echo "PASS shared advisor plan with persistent manual recommendation overrides"
