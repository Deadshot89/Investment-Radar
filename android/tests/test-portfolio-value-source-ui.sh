#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
LIVE="android/app/src/main/java/de/tobias/investmentradar/LivePortfolioCard.kt"

grep -q 'Live-Kurs × Stückzahl' "$UI"
grep -q 'Live-Tracking aktiv · Einstand importiert' "$UI"
grep -q 'Bestätigter Depot-Snapshot · Einstand importiert' "$UI"
grep -q 'Letzter bestätigter Trade-Republic-Wert' "$UI"
grep -q 'Aktueller Marktpreis' "$UI"
grep -q 'Nicht berechenbar · Stückzahl fehlt' "$UI"
grep -q 'Nicht vollständig berechenbar' "$UI"
grep -q 'Nicht vollständig berechenbar' "$LIVE"

# Missing-current-value details belong on the affected portfolio position, not on the
# compact live dashboard card. In particular, legacy custom fixed-income/iBond IDs
# must never leak into this overview as a red "Prüfen" warning.
if grep -q 'Position(en) ohne belastbaren aktuellen Wert' "$LIVE"; then
  echo 'FAIL live portfolio card still shows the global missing-value warning'
  exit 1
fi
if grep -q 'Prüfen: .*Kurs oder Stückzahl fehlt' "$LIVE"; then
  echo 'FAIL live portfolio card still leaks missing-value instrument names'
  exit 1
fi

echo 'PASS current portfolio values are live-derived; confirmed snapshots and single-security market prices remain explicitly separated'
