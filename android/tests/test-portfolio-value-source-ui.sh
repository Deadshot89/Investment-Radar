#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

# Imported snapshot without tracked shares must be labeled as imported fallback.
grep -q 'Importierter Depotwert' "$UI"

# Once tracked shares and a usable quote exist, the UI must say the value is live-derived.
grep -q 'Live-Kurs × Stückzahl' "$UI"

# The footer must distinguish snapshot fallback from active live tracking and imported cost basis.
grep -q 'Live-Tracking aktiv · Einstand importiert' "$UI"
grep -q 'Depotwert und Einstand importiert' "$UI"

echo 'PASS portfolio value source is explicit for imported cost basis and live-tracked holdings'
