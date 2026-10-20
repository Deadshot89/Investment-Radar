#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() { echo "FAIL: $1" >&2; exit 1; }

# Advisor 2.3 ersetzt nur die Zuteilungsquelle; die Live-Zeile muss weiterhin die echte Depotposition erhalten.
# Fremdwährungs-Kurse dürfen dabei niemals als EUR-Depotwert interpretiert werden.
grep -Fq 'private fun RecommendationRow(' "$MAIN" || fail "RecommendationRow is missing"
grep -Fq 'position: PortfolioPosition?' "$MAIN" || fail "RecommendationRow does not accept the portfolio position"
grep -Fq 'position = positions[item.id]' "$MAIN" || fail "Live purchase-plan rows do not receive the portfolio position"
grep -Fq 'val depotValue = position?.takeIf { it.isActiveHolding() }?.currentValue(euroComparablePrice(item))' "$MAIN" || fail "Live recommendation row does not value the depot position with the EUR-comparable price"
grep -Fq 'val topDepotValue = topPosition?.takeIf { it.isActiveHolding() }?.currentValue(euroComparablePrice(top))' "$MAIN" || fail "Top recommendation does not value the depot position with the EUR-comparable price"
! grep -Fq 'currentValue(item.price)' "$MAIN" || fail "Live recommendation row still uses the native-currency quote as if it were EUR"
! grep -Fq 'currentValue(top.price)' "$MAIN" || fail "Top recommendation still uses the native-currency quote as if it were EUR"
grep -Fq 'IM DEPOT' "$MAIN" || fail "Owned stocks are not visibly marked in Live"
grep -Fq 'formatMoney(depotValue)' "$MAIN" || fail "Live depot value is not formatted as money"
grep -Fq '"Depotwert" to' "$MAIN" || fail "Today recommendation does not expose the current depot value"

# Live recommendations must be transferable into the depot without creating duplicate portfolio entries.
grep -Fq 'onAddToPortfolio: (InvestmentItem) -> Unit' "$MAIN" || fail "DashboardScreen has no direct depot callback"
grep -Fq 'onAddToPortfolio = { investmentDialogItem = it }' "$MAIN" || fail "Live is not wired to the existing portfolio purchase editor"
grep -Fq 'if (isHolding) "Position erhöhen" else "Zum Depot hinzufügen"' "$MAIN" || fail "Live recommendation rows lack add/increase depot action"
grep -Fq 'if (topInDepot) "Position erhöhen" else "Zum Depot"' "$MAIN" || fail "Top Live recommendation lacks add/increase depot action"
grep -Fq 'onAddToPortfolio(top)' "$MAIN" || fail "Top Live recommendation does not open portfolio entry flow"
grep -Fq 'onAddToPortfolio(item)' "$MAIN" || fail "Live recommendation row does not open portfolio entry flow"
grep -Fq 'label = "EUR-Kurs (nur Orientierung)"' "$MAIN" || fail "Trade editor no longer labels the current EUR quote as orientation."
grep -Fq 'Kein automatischer Einstand. Betrag, Kurs und Anteile selbst prüfen.' "$MAIN" || fail "Trade editor no longer separates quote price from real purchase cost basis."
! grep -Fq 'Aktueller Live-Kurs:' "$MAIN" || fail "Undated trade quote again incorrectly claims realtime data."

echo "PASS: Live recommendations use EUR-comparable prices for depot values and retain add/increase actions"
