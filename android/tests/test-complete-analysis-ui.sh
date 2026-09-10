#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RADAR="$ROOT/android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
DETAIL="$ROOT/android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"

for token in 'Datenabdeckung' 'Kurs' 'Historie' 'Fundamentals' 'Prognosequalität' 'Basis' 'Bull' 'Bear' 'Treiber' 'Risiken'; do
  grep -Fq "$token" "$RADAR" "$DETAIL" || { echo "Missing UI analysis label: $token"; exit 1; }
done

for token in 'Qualität' 'Bewertung' 'Wachstum' 'Momentum' 'Risiko'; do
  grep -Fq "$token" "$RADAR" "$DETAIL" || { echo "Missing score pillar label: $token"; exit 1; }
done

grep -Fq 'NICHT_BELASTBAR' "$RADAR" || { echo "Radar must explicitly handle NICHT_BELASTBAR"; exit 1; }
grep -Fq 'Nicht verfügbar' "$RADAR" "$DETAIL" || { echo "Missing values must be shown as Nicht verfügbar"; exit 1; }

echo "Complete analysis UI contract OK"
