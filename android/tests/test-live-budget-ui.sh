#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"

# Investment Radar 2.4: the root UI must consume the live journal-backed budget state.
grep -q 'val budgetState by vm.budgetState.collectAsState()' "$SRC"
grep -q 'budgetState = budgetState' "$SRC"
grep -q 'budgetState.advisorBudgetEur' "$SRC"
grep -q 'budgetState.monthlyAvailableEur' "$SRC"
grep -q 'vm::setMonthlyBudget' "$SRC"
grep -q 'vm.addExtraFunding' "$SRC"
grep -q 'vm.executeBuy' "$SRC"
grep -q 'vm.executeSale' "$SRC"

# Static monthly-budget preference must no longer drive recommendations or the dashboard.
if grep -q 'prefs.getInt("monthly_budget"' "$SRC"; then
  echo "Static monthly_budget preference still drives MainActivity"
  exit 1
fi

# The visible cockpit must expose the key budget buckets.
for label in 'Monatsbudget' 'Für Käufe frei' 'Übertrag (kein Kaufbudget)' 'Zusatz-Cash (kein Kaufbudget)' 'Depot-Einstand' 'Kontostand gesamt' 'Reserviert' 'Cash gesamt (kein Kaufbudget)'; do
  grep -q "$label" "$SRC" || {
    echo "Missing budget cockpit label: $label"
    exit 1
  }
done

grep -q 'Kauf ausgeführt' "$SRC"
grep -q 'Verkauf ausgeführt' "$SRC"

# Editing/deleting an execution-backed transaction must keep portfolio and budget journal in sync.
for method in reviseBuy deleteBuy reviseSale deleteSale; do
  grep -q "InvestmentBudgetExecutionService.$method" "$VM" || {
    echo "Missing budget-aware transaction edit path: $method"
    exit 1
  }
done

grep -q 'InvestmentBudgetStore.saveEntries' "$VM"
grep -q 'InvestmentBudgetStore.saveReservations' "$VM"
grep -q 'refreshBudgetState' "$VM"

echo "PASS live investment budget cockpit wiring"

grep -q 'addBudgetAdjustment' "$VM"
grep -q 'feeEur' "$VM"

ADVISOR="android/app/src/main/java/de/tobias/investmentradar/PortfolioAdvisorEngine.kt"
if grep -Fq 'budget.availableEur' "$ADVISOR"; then
  echo "Advisor darf Gesamt-Cash nicht als Kaufbudget akzeptieren"
  exit 1
fi
if grep -Fq '"Gesamt verfügbar" to formatMoney' "$SRC"; then
  echo "Altes mehrdeutiges Gesamt-verfügbar-Label darf nicht mehr als Budget-Kachel erscheinen"
  exit 1
fi
if grep -Fq '"Rest Vormonate" to formatMoney' "$SRC"; then
  echo "Übertrag muss ausdrücklich als kein Kaufbudget gekennzeichnet sein"
  exit 1
fi
if grep -Fq 'listOf(50, 100, 200, 500)' "$SRC"; then
  echo "500-Euro-Budget-Schnellwahl darf nicht mehr angezeigt werden"
  exit 1
fi
