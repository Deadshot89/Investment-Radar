#!/usr/bin/env bash
set -euo pipefail
FILE="$1"
grep -Fq 'https://app.traderepublic.com/instrument/$isin?timeframe=1d' "$FILE"
grep -Fq 'setPackage("de.traderepublic.app")' "$FILE"
grep -Fq 'resolveActivity(context.packageManager)' "$FILE"
! grep -Fq 'https://www.google.com/search?q=' "$FILE"
! grep -Fq 'site:traderepublic.com' "$FILE"
echo 'Trade-Republic-Link-Test: OK'
