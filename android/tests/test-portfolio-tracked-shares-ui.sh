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

# Android 2.5.48 invariant: quote availability and current-value calculability are separate states.
# currentPriceEur is the explicit source of truth for quote availability; hasUsablePrice keeps its
# existing meaning that the position's current value is actually calculable.
# A snapshot can already have a valid live EUR quote while its share count is still unknown.
# In that case the UI must not falsely claim that the quote itself is missing.
grep -q '"Stückzahl fehlt"' "$DASHBOARD" || {
  echo "FAIL missing tracked shares are still not distinguished from a missing quote"
  exit 1
}
grep -Fq 'row.currentPriceEur != null -> "Stückzahl fehlt"' "$DASHBOARD" || {
  echo "FAIL current-value fallback does not use explicit quote availability to distinguish missing shares"
  exit 1
}

# The UI must clearly label that cost basis is imported rather than transaction-derived.
grep -q 'Einstand importiert' "$DASHBOARD"

echo "PASS imported portfolio positions distinguish missing shares from missing quotes and retain imported performance basis"
