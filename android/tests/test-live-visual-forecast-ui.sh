#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
LIVE_FORECAST="android/app/src/main/java/de/tobias/investmentradar/LiveForecastSummary.kt"

# SIGNAL must visually distinguish an active buy signal from an inactive WAIT state.
grep -q 'val signalAccent = if (top != null) RadarGreen else RadarPurple' "$MAIN"
grep -q 'val signalStyle = if (top == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleLarge' "$MAIN"
grep -q 'DarkMetricCard("SIGNAL", if (top != null) "AKTIV" else "WARTEN", signalAccent' "$MAIN"

# Forecast scenarios use three distinct, explicit meanings: Bear red, Base cyan, Bull green.
grep -q 'private val LiveForecastBear = Color(0xFFFF6577)' "$LIVE_FORECAST"
grep -q 'private val LiveForecastBase = Color(0xFF4DE6FF)' "$LIVE_FORECAST"
grep -q 'private val LiveForecastBull = Color(0xFF2EE59D)' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBear' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBase' "$LIVE_FORECAST"
grep -q 'color = LiveForecastBull' "$LIVE_FORECAST"

# The primary forecast line must show both 12M direction/change and the base target price when available.
grep -q '12M Prognose' "$LIVE_FORECAST"
grep -q 'Ziel ' "$LIVE_FORECAST"
grep -q 'Warum?' "$LIVE_FORECAST"
grep -q 'Datenlage' "$LIVE_FORECAST"

echo "PASS live visual forecast hierarchy"
