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

grep -q 'add("includeCounts" to query.includeCounts.toString())' "$API"
grep -q 'optJSONObject("counts")' "$API"
grep -q 'RadarCounts(' "$API"

# BUY fallback and data-quality diagnostics are part of the Android contract.
grep -q 'val buyFallbackActive: Boolean = false' "$MODELS"
grep -q 'buyFallbackActive = obj.optBoolean("buyFallbackActive", false)' "$API"
grep -q 'Kaufkandidaten – noch nicht bestätigt' "$SCREEN"
grep -q 'Starke WATCH-Werte' "$SCREEN"
grep -q 'Daten fehlen' "$SCREEN"
grep -q 'Datenhinweis:' "$SCREEN"
grep -q 'summary.dataError' "$SCREEN"
grep -q 'radarDataGapReasons(summary)' "$SCREEN"

# The UI identifies the Radar and exposes the agreed five metrics.
grep -q 'Text("RADAR 2.1"' "$SCREEN"
grep -q '"Gesamt"' "$SCREEN"
grep -q '"Aktien"' "$SCREEN"
grep -q '"ETFs"' "$SCREEN"
grep -q '"Kaufen"' "$SCREEN"
grep -q '"Beobachten"' "$SCREEN"

# A failed network request must not silently invent generic BUY results.
grep -q 'filters.recommendation == RadarRecommendationFilter.ALL' "$SCREEN"
grep -q 'Keine bestätigten Kauf- oder starken Beobachtungskandidaten' "$SCREEN"

echo "PASS Radar 2.1 counters, BUY fallback and visible data-quality UI"
