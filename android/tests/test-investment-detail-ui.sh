#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"

test -f "$SRC"
grep -q 'Quality' "$SRC"
grep -q 'Valuation' "$SRC"
grep -q 'Growth' "$SRC"
grep -q 'Momentum' "$SRC"
grep -q 'Risk' "$SRC"
grep -q 'Coverage' "$SRC"
for period in 1D 1M 3M 6M 12M; do grep -q "$period" "$SRC"; done
grep -q 'Positiver Trend' "$SRC"
grep -q 'Negativer Trend' "$SRC"
grep -q 'Gemischter Trend' "$SRC"
grep -q 'Datenquellen' "$SRC"
grep -q 'Trade Republic öffnen' "$SRC"
grep -q 'Nicht verfügbar' "$SRC"
! grep -q 'ApiClient' "$SRC"

echo "PASS investment detail UI"
