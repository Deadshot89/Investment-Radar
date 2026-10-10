#!/usr/bin/env bash
set -euo pipefail

DASHBOARD="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
VIEWMODEL="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
POSITION="android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt"

# Imported snapshot positions must let the user add a real share count while preserving imported cost basis.
grep -q 'Stückzahl ergänzen' "$DASHBOARD"
grep -q 'Stückzahl speichern' "$DASHBOARD"
grep -q 'trackedShares' "$POSITION"
grep -q 'setTrackedShares' "$VIEWMODEL"
grep -q 'trackedShares' "$STORE"

# Share input must use the same locale-safe decimal parser as money inputs so grouped values
# like 1.000,50 and 1,000.50 keep their magnitude instead of becoming invalid.
grep -q 'GermanDecimalInput.parse(input)' "$DASHBOARD"
if grep -Fq "input.trim().replace(',', '.').toDoubleOrNull()" "$DASHBOARD"; then
  echo "FAIL tracked share input still uses ad-hoc decimal parsing"
  exit 1
fi

# The UI must clearly label that cost basis is imported rather than transaction-derived.
grep -q 'Einstand importiert' "$DASHBOARD"

echo "PASS imported portfolio positions can use locale-safe live share tracking while retaining imported performance basis"
