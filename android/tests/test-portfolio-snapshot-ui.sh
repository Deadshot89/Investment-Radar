#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
grep -q 'Nicht erfasst' "$FILE"
grep -q 'Depotwert importiert · Kaufdaten fehlen' "$FILE"
echo "PASS snapshot-only portfolio rows do not fake cost basis or transaction history"
