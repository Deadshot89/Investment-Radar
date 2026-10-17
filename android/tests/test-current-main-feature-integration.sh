#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CUSTOM_STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"
PORTFOLIO_STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt"
METRICS="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt"
DASHBOARD="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
VM="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# Current production cleanup must survive the screenshot-import branch.
grep -Fq 'filterNot { it.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID }' "$CUSTOM_STORE" || fail 'Custom asset loader lost the retired iBond cleanup.'
grep -Fq 'if (item.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return' "$CUSTOM_STORE" || fail 'Custom asset writes can re-add the retired iBond.'
grep -Fq 'filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }' "$PORTFOLIO_STORE" || fail 'Portfolio loader lost the retired iBond cleanup.'
grep -Fq 'if (position.itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return' "$PORTFOLIO_STORE" || fail 'Portfolio writes can re-add the retired iBond.'
grep -Fq '.remove(lifecycleStatusKey(retiredId))' "$PORTFOLIO_STORE" || fail 'Retired iBond cleanup leaves lifecycle metadata behind.'
grep -Fq '.remove(snapshotCapturedAtKey(retiredId))' "$PORTFOLIO_STORE" || fail 'Retired iBond cleanup leaves screenshot timestamp metadata behind.'

# Current-EUR-price visibility must be retained without weakening SOLD_MARKED.
grep -Fq 'val currentPriceEur: Double?' "$METRICS" || fail 'Portfolio metrics lost the current EUR unit price.'
grep -Fq 'currentPriceEur = draft.usablePrice' "$METRICS" || fail 'Portfolio metrics do not propagate the current EUR unit price.'
grep -Fq 'position.lifecycleStatus != PortfolioLifecycleStatus.SOLD_MARKED' "$METRICS" || fail 'SOLD_MARKED lifecycle override was lost during integration.'
grep -Fq 'PortfolioDashboardValue("Aktueller Kurs", row.currentPriceEur?.let(::portfolioMoney) ?: "Kurs fehlt")' "$DASHBOARD" || fail 'Portfolio dashboard lost the current EUR unit price display.'

# Quote availability and whether a current position value is calculable are separate states.
grep -Fq 'val missingPriceCount = activeDrafts.count { it.fixedIncomePrincipal == null && it.usablePrice == null }' "$METRICS" || fail 'Missing-price count again treats missing shares as a missing quote.'
grep -Fq 'val currentValueComplete = activeDrafts.all { it.currentValue != null }' "$METRICS" || fail 'Current-value completeness no longer tracks actual calculability.'
grep -Fq 'row.currentPriceEur != null -> "Stückzahl fehlt"' "$DASHBOARD" || fail 'Snapshot with a valid quote still does not distinguish missing shares from a missing quote.'

# Full-portfolio concentration context must remain wired in both interactive and persisted plans.
grep -Fq 'concentrationContext = PortfolioConcentrationContext(' "$MAIN" || fail 'Main UI advisor allocation lost portfolio concentration context.'
grep -Fq 'concentrationContext = PortfolioConcentrationContext(' "$VM" || fail 'Persisted advisor allocation lost portfolio concentration context.'

echo 'PASS: current production portfolio invariants coexist with screenshot-import lifecycle semantics.'