#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

# Imported snapshot without tracked shares must be labeled as imported fallback.
grep -q 'Importierter Depotwert' "$UI"

# Once tracked shares and a usable quote exist, the UI must say the value is live-derived.
grep -q 'Live-Kurs × Stückzahl' "$UI"

# The footer must distinguish snapshot fallback from active live tracking.
grep -q 'Live-Tracking aktiv · Kaufdaten fehlen' "$UI"
grep -q 'Depotwert importiert · Kaufdaten fehlen' "$UI"

echo 'PASS portfolio value source is explicit for imported and live-tracked holdings'
