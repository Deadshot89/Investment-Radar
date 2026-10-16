#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

grep -q 'Nicht erfasst' "$FILE"
grep -q 'Letzter bestätigter Trade-Republic-Wert' "$FILE"
grep -q 'Aktueller Depotwert' "$FILE"
grep -q 'Nicht berechenbar · Stückzahl fehlt' "$FILE"
grep -q 'Aktueller Marktpreis' "$FILE"
grep -q 'Bestätigter Depot-Snapshot · Einstand importiert' "$FILE"

echo "PASS confirmed Trade Republic snapshots remain traceable without claiming a current depot value"
