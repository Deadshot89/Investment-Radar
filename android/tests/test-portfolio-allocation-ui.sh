#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

# 2.3 hat genau einen gemeinsamen PortfolioAdvisorPlan am Root.
grep -q 'val advisorPlan = PortfolioAdvisorEngine.allocate' "$SRC"
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

echo "PASS shared Investment Radar 2.3 advisor plan"
