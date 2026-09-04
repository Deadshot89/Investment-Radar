#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
LIVE_FORECAST="android/app/src/main/java/de/tobias/investmentradar/LiveForecastSummary.kt"

# Live dashboard must surface the existing 12M model without forcing a Radar/detail navigation.
grep -q 'LiveForecastSummary(top' "$MAIN"
grep -q 'LiveForecastSummary(item' "$MAIN"

test -f "$LIVE_FORECAST"
grep -q 'ForecastEngine.forecast' "$LIVE_FORECAST"
grep -q 'ForecastHorizon.TWELVE_MONTHS' "$LIVE_FORECAST"
grep -q '12M Prognose' "$LIVE_FORECAST"
grep -q 'Schwach' "$LIVE_FORECAST"
grep -q 'Erwartet' "$LIVE_FORECAST"
grep -q 'Stark' "$LIVE_FORECAST"
grep -q 'Warum?' "$LIVE_FORECAST"
grep -q 'Datenlage' "$LIVE_FORECAST"
grep -q 'höhere Unsicherheit' "$LIVE_FORECAST"

echo "PASS live forecast UI wiring"
