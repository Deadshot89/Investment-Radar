#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() { echo "FAIL: $1" >&2; exit 1; }

# Advisor 2.3 ersetzt nur die Zuteilungsquelle; die Live-Zeile muss weiterhin die echte Depotposition erhalten.
grep -Fq 'RecommendationRow(item, null, positions[item.id]' "$MAIN" || fail "Live purchase-plan rows do not receive the portfolio position"
grep -Fq 'private fun RecommendationRow(item: InvestmentItem, personal: PersonalRecommendation?, position: PortfolioPosition?' "$MAIN" || fail "RecommendationRow does not accept the portfolio position"
grep -Fq 'val depotValue = position?.takeIf { it.isActiveHolding() }?.currentValue(item.price)' "$MAIN" || fail "Current depot value is not calculated from the live item price"
grep -Fq 'IM DEPOT' "$MAIN" || fail "Owned stocks are not visibly marked in Live"
grep -Fq 'formatMoney(depotValue)' "$MAIN" || fail "Live depot value is not formatted as money"
grep -Fq '"Depotwert" to' "$MAIN" || fail "Today recommendation does not expose the current depot value"

# Live recommendations must be transferable into the depot without creating duplicate portfolio entries.
grep -Fq 'onAddToPortfolio: (InvestmentItem) -> Unit' "$MAIN" || fail "DashboardScreen has no direct depot callback"
grep -Fq 'onAddToPortfolio = { investmentDialogItem = it }' "$MAIN" || fail "Live is not wired to the existing portfolio purchase editor"
grep -Fq 'Text(if (isHolding) "Position erhöhen" else "Zum Depot hinzufügen")' "$MAIN" || fail "Live recommendation rows lack add/increase depot action"
grep -Fq 'Text(if (topInDepot) "Position erhöhen" else "Zum Depot hinzufügen")' "$MAIN" || fail "Top Live recommendation lacks add/increase depot action"
grep -Fq 'onAddToPortfolio(top)' "$MAIN" || fail "Top Live recommendation does not open portfolio entry flow"
grep -Fq 'onAddToPortfolio(item)' "$MAIN" || fail "Live recommendation row does not open portfolio entry flow"

echo "PASS: Live recommendations can be added to or increased in the depot while retaining current depot values"
