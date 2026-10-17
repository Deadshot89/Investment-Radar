#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

grep -q 'Nicht erfasst' "$FILE"
grep -q 'Letzter bestätigter Trade-Republic-Wert' "$FILE"
grep -q 'Aktueller Depotwert' "$FILE"
grep -Fq 'val currentValueMissingReason = when {' "$FILE"
grep -Fq 'row.currentPriceEur != null -> "Stückzahl fehlt"' "$FILE"
grep -Fq 'else -> "Kurs fehlt"' "$FILE"
grep -Fq 'PortfolioDashboardValue("Aktueller Depotwert", "Nicht berechenbar · $currentValueMissingReason")' "$FILE"
grep -q 'Aktueller Marktpreis' "$FILE"
grep -q 'Bestätigter Depot-Snapshot · Einstand importiert' "$FILE"

echo "PASS confirmed Trade Republic snapshots remain traceable and distinguish missing shares from missing quotes"