#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
VM="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
API="$ROOT/android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"
[ -f "$STORE" ] || { echo 'FAIL: CustomInvestmentStore fehlt'; exit 1; }
grep -q 'Aktie/ETF hinzufügen' "$MAIN"
grep -q 'Eigene Werte' "$MAIN"
grep -q 'CustomInvestmentDialog' "$MAIN"
grep -q 'loadCustomQuote' "$API"
grep -q '/api/custom-quote' "$API"
grep -q 'customItems' "$VM"
grep -q 'addCustomInvestment' "$VM"
grep -q 'removeCustomInvestment' "$VM"
grep -q 'RadarSortOption' "$MAIN"
grep -q 'PORTFOLIO RISIKO' "$MAIN"
grep -q 'Konzentrationswarnung' "$MAIN"
grep -q 'openSavedTradeRepublicUrl' "$MAIN"
echo 'PASS: custom assets + risk/sort UI wiring present'
