#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
LIVE_FORECAST="android/app/src/main/java/de/tobias/investmentradar/LiveForecastSummary.kt"
DETAIL="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"
FORECAST_ENGINE="android/app/src/main/java/de/tobias/investmentradar/ForecastEngine.kt"

# SIGNAL must visually distinguish an active buy signal from an inactive WAIT state.
grep -q 'val signalAccent = if (top != null) RadarGreen else RadarPurple' "$MAIN"
grep -q 'val signalStyle = if (top == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge' "$MAIN"
grep -q 'DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", signalAccent' "$MAIN"

# Forecast scenarios are fully German and use three distinct colors.
grep -q 'private val LiveForecastBear = Color(0xFFFF6577)' "$LIVE_FORECAST"
grep -q 'private val LiveForecastBase = Color(0xFF4DE6FF)' "$LIVE_FORECAST"
grep -q 'private val LiveForecastBull = Color(0xFF2EE59D)' "$LIVE_FORECAST"
grep -q 'Text("Schwach ' "$LIVE_FORECAST"
grep -q 'Text("Erwartet ' "$LIVE_FORECAST"
grep -q 'Text("Stark ' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBear' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBase' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBull' "$LIVE_FORECAST"
grep -q 'DetailValueRow("Schwach"' "$DETAIL"
grep -q 'DetailValueRow("Erwartet"' "$DETAIL"
grep -q 'DetailValueRow("Stark"' "$DETAIL"
! grep -q 'DetailValueRow("Bear"' "$DETAIL"
! grep -q 'DetailValueRow("Bull"' "$DETAIL"
! grep -q '"Bear ' "$LIVE_FORECAST"
! grep -q '"Bull ' "$LIVE_FORECAST"
! grep -q 'Bear-Szenario' "$FORECAST_ENGINE"

# The primary forecast line must show both 12M direction/change and the expected target price when available.
grep -q '12M Prognose' "$LIVE_FORECAST"
grep -q 'Ziel ' "$LIVE_FORECAST"
grep -q 'Warum?' "$LIVE_FORECAST"
grep -q 'Datenlage' "$LIVE_FORECAST"

echo "PASS live visual forecast hierarchy"
