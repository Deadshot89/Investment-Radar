#!/usr/bin/env bash
set -euo pipefail
SCREEN="android/app/src/main/java/de/tobias/investmentradar/SavingsPlansScreen.kt"
PORTFOLIO="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

[ -f "$SCREEN" ] || { echo "SavingsPlansScreen.kt missing"; exit 1; }
grep -Fq 'Text("Sparpläne")' "$SCREEN" || { echo 'Savings Plans title missing'; exit 1; }
grep -Fq 'Text("Ausgeführt")' "$SCREEN" || { echo 'Executed confirmation action missing'; exit 1; }
grep -Fq 'Text("Nicht ausgeführt")' "$SCREEN" || { echo 'Skipped confirmation action missing'; exit 1; }
grep -Fq 'Ausführungstage' "$SCREEN" || { echo 'Editable execution days missing'; exit 1; }
grep -Fq 'Instrument noch nicht eindeutig zugeordnet' "$SCREEN" || { echo 'Private Equity safety message missing'; exit 1; }
grep -Fq 'onOpenSavingsPlans' "$PORTFOLIO" || { echo 'Portfolio savings-plan entry missing'; exit 1; }
grep -Fq 'SavingsPlansScreen(' "$MAIN" || { echo 'Savings Plans navigation wiring missing'; exit 1; }
if grep -Eq 'calculableCurrentValue[^\n]*(amountEur|SavingsPlan)|investedCostBasis[^\n]*(amountEur|SavingsPlan)' "$SCREEN" "$PORTFOLIO"; then
  echo 'Planned savings-plan amounts must not be added to portfolio totals'
  exit 1
fi
echo 'Savings plans UI contract passed.'
