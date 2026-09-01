#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
GRADLE="$ROOT/android/app/build.gradle.kts"
MARKET="$ROOT/backend/src/lib/market.mjs"
DASH="$ROOT/backend/src/lib/dashboard.mjs"
CACHE="$ROOT/backend/src/lib/quoteCache.mjs"

grep -q 'versionCode = 18' "$GRADLE"
grep -q 'versionName = "1.1.17"' "$GRADLE"
grep -q 'val quality = if (item.dataDelayed) "verzögert" else "Live"' "$MAIN"
grep -q 'Kursquelle ${item.dataSource}' "$MAIN"
grep -q 'loadQuoteCache' "$DASH"
grep -q 'mergeQuotesWithCache' "$DASH"
grep -q 'query2.finance.yahoo.com' "$MARKET"
test -f "$CACHE"
