#!/usr/bin/env bash
set -euo pipefail
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt"
API="android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"

for label in \
  'Top Chancen' \
  'Neu im Radar' \
  'Starkes Momentum' \
  'Attraktive Bewertung' \
  'Qualitätsaktien' \
  'ETFs' \
  'Depot-Ergänzungen'; do
  grep -q "$label" "$RADAR"
done

grep -q 'Werte im Analyseuniversum' "$RADAR"
grep -q 'Serverseitige Analyse' "$RADAR"
grep -q 'TR-geprüft' "$RADAR"
grep -q 'Trade-Republic-Handelbarkeit noch nicht bestätigt' "$RADAR"
grep -q 'Weitere 40 Ergebnisse laden' "$RADAR"
grep -q 'pageSize = 40' "$RADAR"
grep -q 'data class RadarQuery' "$MODELS"
grep -q 'data class RadarSummaryItem' "$MODELS"
grep -q 'data class RadarPage' "$MODELS"
grep -q 'tradeRepublicEligible: Boolean?' "$MODELS"
grep -q 'purchaseEligible: Boolean' "$MODELS"
grep -q 'loadRadarPage' "$API"
grep -q '/api/radar' "$API"
grep -q '/api/instrument/' "$API"

echo "PASS Radar 2.0 discovery, paging and Trade Republic safety contract"
