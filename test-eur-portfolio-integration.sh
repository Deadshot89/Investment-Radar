#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/android/app/src/main/java/de/tobias/investmentradar/Models.kt"
API="$ROOT/android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
UI="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
CFG="$ROOT/backend/data/investments.json"
DASH="$ROOT/backend/src/lib/dashboard.mjs"

grep -q 'val priceEur: Double?' "$MODELS"
grep -q 'priceEur = if (o.isNull("priceEur")) null else o.optDouble("priceEur")' "$API"
grep -q 'item.priceEur' "$UI"
grep -q '"yahooSymbol": "SPYI.DE"' "$CFG"
grep -q '"yahooSymbol": "IS3S.DE"' "$CFG"
grep -q '"yahooSymbol": "IS3Q.DE"' "$CFG"
grep -q 'priceEur:' "$DASH"
SHEETS="$ROOT/backend/src/lib/sheets.mjs"
grep -q 'yahooSymbol:' "$SHEETS"
