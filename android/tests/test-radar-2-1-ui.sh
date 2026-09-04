#!/usr/bin/env bash
set -euo pipefail

MODELS="android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt"
API="android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"
SCREEN="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"

# Radar 2.1 requests server-side counters and models them explicitly.
grep -q 'val includeCounts: Boolean = true' "$MODELS"
grep -q 'data class RadarCounts(' "$MODELS"
grep -q 'val stocks: Int' "$MODELS"
grep -q 'val etfs: Int' "$MODELS"
grep -q 'val buy: Int' "$MODELS"
grep -q 'val watch: Int' "$MODELS"
grep -q 'val counts: RadarCounts' "$MODELS"

grep -q 'append("includeCounts", query.includeCounts.toString())' "$API"
grep -q 'optJSONObject("counts")' "$API"
grep -q 'RadarCounts(' "$API"

# The UI identifies the new Radar and exposes the agreed five metrics.
grep -q 'Text("RADAR 2.1"' "$SCREEN"
grep -q '"Gesamt"' "$SCREEN"
grep -q '"Aktien"' "$SCREEN"
grep -q '"ETFs"' "$SCREEN"
grep -q '"Kaufen"' "$SCREEN"
grep -q '"Beobachten"' "$SCREEN"

# A failed BUY request must never fall back to generic local instruments.
grep -q 'filters.recommendation == RadarRecommendationFilter.ALL' "$SCREEN"
grep -q 'Aktuell keine echten Kaufkandidaten' "$SCREEN"

echo "PASS Radar 2.1 counters and exact BUY UI"
