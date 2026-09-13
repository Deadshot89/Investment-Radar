#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
PREFILL="android/app/src/main/java/de/tobias/investmentradar/RecommendationTradePrefill.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'var pendingActionShares by remember { mutableStateOf<Double?>(null) }' "$UI" || fail 'Empfohlene Anteile werden nicht im UI-Zustand gehalten.'
grep -Fq 'RecommendationTradePrefill.calculate(' "$UI" || fail 'Aktionscenter nutzt die Stückzahlberechnung nicht.'
grep -Fq 'priceEur = euroComparablePrice(item)' "$UI" || fail 'Aktueller EUR-Kurs wird nicht für den Vorschlag verwendet.'
grep -Fq 'heldShares = positions[item.id]?.shares' "$UI" || fail 'Aktueller Bestand wird bei Verkauf/Reduzierung nicht berücksichtigt.'
grep -Fq 'initialShares = pendingActionShares' "$UI" || fail 'Transaktionsdialog erhält die vorgeschlagenen Anteile nicht.'
grep -Fq 'initialPrefillMessage = pendingActionPrefillMessage' "$UI" || fail 'Hinweis zur Vorbelegung wird nicht angezeigt.'
grep -Fq 'mutableStateOf(initialShares?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())' "$UI" || fail 'Vorgeschlagene Anteile werden nicht sichtbar vorbefüllt.'
grep -Fq 'ActionType.SELL -> holding' "$PREFILL" || fail 'Vollverkauf übernimmt nicht den tatsächlichen Bestand.'
grep -Fq 'holding?.let { min(calculated, it) } ?: calculated' "$PREFILL" || fail 'Reduzierung ist nicht auf den vorhandenen Bestand begrenzt.'
grep -Fq 'onExecutePurchase(purchase, fee)' "$UI" || fail 'Kauf muss weiterhin manuell bestätigt werden.'
grep -Fq 'onExecuteSale(sale, fee)' "$UI" || fail 'Verkauf muss weiterhin manuell bestätigt werden.'

echo 'PASS: Empfehlungen füllen Betrag und Stückzahl vor, ohne automatische Order.'
