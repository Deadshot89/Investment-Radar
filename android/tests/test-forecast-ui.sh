#!/usr/bin/env bash
set -euo pipefail
DETAIL="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

grep -q 'PROGNOSE' "$DETAIL"
for period in '1 Monat' '3 Monate' '6 Monate' '12 Monate'; do grep -q "$period" "$DETAIL"; done
grep -q 'Bull' "$DETAIL"
grep -q 'Basis' "$DETAIL"
grep -q 'Bear' "$DETAIL"
grep -q 'Warum diese Prognose' "$DETAIL"
grep -q 'modellbasierte Einschätzung' "$DETAIL"
grep -q '12M Prognose' "$RADAR"
# Die drei Dashboard-Kacheln sollen fest unterschiedliche Akzentfarben haben.
grep -q 'DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow' "$MAIN"
grep -q 'DarkMetricCard("BUDGET", "\$budget €", RadarBlue' "$MAIN"
grep -q 'DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", RadarGreen' "$MAIN"
# WARTEN in der Metrik darf nicht mehr titleLarge verwenden.
grep -q 'valueStyle:' "$MAIN"

echo "PASS forecast UI wiring"
