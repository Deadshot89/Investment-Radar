#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
NAV="android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt"

# Direct instrument navigation must use the stable Trade Republic web-app stock route.
grep -Fq 'https://app.traderepublic.com/stocks/' "$NAV"
# If no usable ISIN/direct target is available, keep a stock-universe fallback.
grep -Fq 'https://app.traderepublic.com/browse/stock' "$NAV"
# The user-facing actions should clearly name the broker destination.
COUNT=$(grep -Fc 'Text("Trade Republic öffnen")' "$MAIN" || true)
if [ "$COUNT" -lt 2 ]; then
  echo "Expected Trade Republic öffnen in Dashboard/Radar/Portfolio, found $COUNT"
  exit 1
fi
# ISIN remains available as a clipboard fallback.
grep -Fq 'Trade Republic ISIN' "$NAV"

echo "PASS Trade Republic direct links"
