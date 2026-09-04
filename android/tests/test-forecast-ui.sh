#!/usr/bin/env bash
set -euo pipefail
DETAIL="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

grep -q 'PROGNOSE' "$DETAIL"
for period in '1 Monat' '3 Monate' '6 Monate' '12 Monate'; do grep -q "$period" "$DETAIL"; done
grep -q 'Schwach' "$DETAIL"
grep -q 'Erwartet' "$DETAIL"
grep -q 'Stark' "$DETAIL"
grep -q 'Warum diese Prognose' "$DETAIL"
grep -q 'modellbasierte Einschätzung' "$DETAIL"

# Jede normale Radar-Aktienkarte muss die 12M-Prognose kompakt sichtbar machen.
grep -q 'ForecastEngine.forecast' "$RADAR"
grep -q 'ForecastHorizon.TWELVE_MONTHS' "$RADAR"
grep -q '12M Prognose' "$RADAR"
grep -q 'Basisziel' "$RADAR"
grep -q 'Prognose-Spanne' "$RADAR"
grep -q 'Warum:' "$RADAR"

# Die drei Dashboard-Kacheln sollen fest unterschiedliche Akzentfarben haben.
grep -q 'DarkMetricCard("MARKT", data.marketLight.uppercase(), RadarYellow' "$MAIN"
grep -q 'DarkMetricCard("BUDGET", "$budget €", RadarBlue' "$MAIN"
grep -q 'val signalAccent = if (top != null) RadarGreen else RadarPurple' "$MAIN"
grep -q 'DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", signalAccent' "$MAIN"
# WARTEN in der Metrik darf nicht mehr titleLarge verwenden.
grep -q 'val signalStyle = if (top == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge' "$MAIN"

echo "PASS forecast UI wiring"
