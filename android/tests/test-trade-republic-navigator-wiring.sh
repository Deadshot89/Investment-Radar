#!/usr/bin/env bash
set -euo pipefail

NAV="android/app/src/main/java/de/tobias/investmentradar/TradeRepublicNavigator.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

grep -Fq 'fun open(context: Context, item: InvestmentItem)' "$NAV"
grep -Fq 'setPackage("de.traderepublic.app")' "$NAV"
grep -Fq 'Trade Republic ISIN' "$NAV"
grep -Fq 'BROWSE_URL' "$NAV"
grep -Fq 'TradeRepublicNavigator.open(context, item)' "$MAIN"
if grep -Fq 'TRADE_REPUBLIC_STOCK_BASE_URL' "$MAIN"; then
  echo 'Legacy Trade Republic navigation still lives in MainActivity'
  exit 1
fi

echo "PASS 1.2.0 Trade Republic navigator wiring"
