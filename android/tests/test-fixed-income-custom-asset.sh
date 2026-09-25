#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
METRICS="android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt"
DASHBOARD="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"

grep -F 'FilterChip(selected = type == "Festzins"' "$MAIN" >/dev/null
grep -F 'fixedPrincipalEur = if (isFixedIncome) fixedPrincipal else null' "$MAIN" >/dev/null
grep -F 'fixedMaturityValueEur = if (isFixedIncome) fixedMaturityValue else null' "$MAIN" >/dev/null
grep -F 'existing == null && !isFixedIncome' "$MAIN" >/dev/null
grep -F 'fixedIncomePrincipal != null || position.isActiveHolding()' "$METRICS" >/dev/null
grep -F 'fixedIncome -> 0.0' "$METRICS" >/dev/null
grep -F 'if (isFixedIncome) "Anlagewert" else "Aktueller Wert"' "$DASHBOARD" >/dev/null
grep -F '"festzins" -> "Festzins"' "$STORE" >/dev/null

echo "fixed-income custom asset contract: OK"
