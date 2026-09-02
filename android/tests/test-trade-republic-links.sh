#!/usr/bin/env bash
set -euo pipefail

FILE="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

# Direct instrument navigation must use the stable Trade Republic web-app stock route.
grep -Fq 'https://app.traderepublic.com/stocks/' "$FILE"
# If no usable ISIN/direct target is available, keep a stock-universe fallback.
grep -Fq 'https://app.traderepublic.com/browse/stock' "$FILE"
# The user-facing actions should clearly name the broker destination.
COUNT=$(grep -Fc 'Text("Trade Republic öffnen")' "$FILE" || true)
if [ "$COUNT" -lt 2 ]; then
  echo "Expected Trade Republic öffnen in Radar and Portfolio, found $COUNT"
  exit 1
fi
# ISIN remains available as a clipboard fallback.
grep -Fq 'Trade Republic ISIN' "$FILE"

echo "PASS 1.1.29 Trade Republic links"
