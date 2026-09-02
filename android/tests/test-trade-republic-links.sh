#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
NAV="android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt"

# Direct instrument navigation must use the stable Trade Republic web-app stock route.
grep -Fq 'https://app.traderepublic.com/stocks/' "$NAV"
# If no usable ISIN/direct target is available, keep a stock-universe fallback.
grep -Fq 'https://app.traderepublic.com/browse/stock' "$NAV"
# Dashboard, Radar and Portfolio must each expose a clearly named broker action.
COUNT=$(( $(grep -Fc 'Trade Republic öffnen' "$MAIN" || true) + $(grep -Fc 'Trade Republic öffnen' "$RADAR" || true) ))
if [ "$COUNT" -lt 3 ]; then
  echo "Expected Trade Republic öffnen in Dashboard/Radar/Portfolio, found $COUNT"
  exit 1
fi
# The extracted Radar must call the same hardened navigator.
grep -Fq 'TradeRepublicNavigator.open' "$RADAR"
# ISIN remains available as a clipboard fallback.
grep -Fq 'Trade Republic ISIN' "$NAV"

echo "PASS Trade Republic direct links"
