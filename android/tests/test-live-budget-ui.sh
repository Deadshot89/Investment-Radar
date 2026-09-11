#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

# Investment Radar 2.4: the root UI must consume the live journal-backed budget state.
grep -q 'val budgetState by vm.budgetState.collectAsState()' "$SRC"
grep -q 'budgetState = budgetState' "$SRC"
grep -q 'budgetState.advisorBudgetEur' "$SRC"
grep -q 'vm.setMonthlyBudget' "$SRC"
grep -q 'vm.addExtraFunding' "$SRC"

# Static monthly-budget preference must no longer drive recommendations or the dashboard.
if grep -q 'prefs.getInt("monthly_budget"' "$SRC"; then
  echo "Static monthly_budget preference still drives MainActivity"
  exit 1
fi

# The visible cockpit must expose the key budget buckets.
for label in 'Monatsbudget' 'Zusätzlich' 'Investiert' 'Reserviert' 'Verfügbar'; do
  grep -q "$label" "$SRC" || {
    echo "Missing budget cockpit label: $label"
    exit 1
  }
done

echo "PASS live investment budget cockpit wiring"
