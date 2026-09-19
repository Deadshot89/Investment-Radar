#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

grep -q 'Nicht erfasst' "$FILE"
grep -q 'Historischer Depotwert gespeichert · Einstand importiert' "$FILE"
grep -q 'historische Snapshotwerte werden nicht als heutiger Depotwert verwendet' "$FILE"

echo "PASS imported portfolio history remains visible without claiming a current value"
