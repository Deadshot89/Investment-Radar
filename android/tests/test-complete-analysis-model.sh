#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MODELS="$ROOT/android/app/src/main/java/de/tobias/investmentradar/RadarModels.kt"
API="$ROOT/android/app/src/main/java/de/tobias/investmentradar/ApiClient.kt"

for token in 'data class RadarDataQuality' 'data class RadarScorePillar' 'data class RadarScoreBreakdown' 'data class RadarForecast' 'data class RadarDiagnostics'; do
  grep -Fq "$token" "$MODELS" || { echo "Missing model: $token"; exit 1; }
done

for field in 'val dataQuality:' 'val scoreBreakdown:' 'val forecast:' 'val diagnostics:'; do
  grep -Fq "$field" "$MODELS" || { echo "RadarSummaryItem missing $field"; exit 1; }
done

for parser in 'parseRadarDataQuality' 'parseRadarScoreBreakdown' 'parseRadarForecast' 'parseRadarDiagnostics'; do
  grep -Fq "$parser" "$API" || { echo "Missing parser: $parser"; exit 1; }
done

grep -Fq 'nullableDouble("expectedChangePct")' "$API" || { echo "Forecast expectedChangePct must stay nullable"; exit 1; }
grep -Fq 'nullableDouble("bearChangePct")' "$API" || { echo "Forecast bearChangePct must stay nullable"; exit 1; }
grep -Fq 'nullableDouble("bullChangePct")' "$API" || { echo "Forecast bullChangePct must stay nullable"; exit 1; }

echo "Complete Android analysis model contract OK"
