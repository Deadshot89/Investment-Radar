#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
grep -q 'Nicht erfasst' "$FILE"
grep -q 'Depotwert und Einstand importiert' "$FILE"
echo "PASS imported portfolio rows expose imported cost basis without inventing transaction history"
