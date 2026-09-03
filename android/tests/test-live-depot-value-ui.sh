#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() { echo "FAIL: $1" >&2; exit 1; }

# This contract is intentionally source-level so every Live UI release gates the ownership/value marker.
grep -Fq 'RecommendationRow(item, personalById[item.id], positions[item.id])' "$MAIN" || fail "Live purchase-plan rows do not receive the portfolio position"
grep -Fq 'private fun RecommendationRow(item: InvestmentItem, personal: PersonalRecommendation?, position: PortfolioPosition?' "$MAIN" || fail "RecommendationRow does not accept the portfolio position"
grep -Fq 'val depotValue = position?.takeIf { it.isActiveHolding() }?.currentValue(item.price)' "$MAIN" || fail "Current depot value is not calculated from the live item price"
grep -Fq 'IM DEPOT' "$MAIN" || fail "Owned stocks are not visibly marked in Live"
grep -Fq 'formatMoney(depotValue)' "$MAIN" || fail "Live depot value is not formatted as money"
grep -Fq '"Depotwert" to' "$MAIN" || fail "Today recommendation does not expose the current depot value"

echo "PASS: Live purchase plan shows owned positions with current depot value"
