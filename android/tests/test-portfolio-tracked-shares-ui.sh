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

# The UI must clearly label that cost basis is imported rather than transaction-derived.
grep -q 'Einstand importiert' "$DASHBOARD"

echo "PASS imported portfolio positions can use live share tracking while retaining imported performance basis"
