#!/usr/bin/env bash
set -euo pipefail

SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail(){ echo "FAIL: $1" >&2; exit 1; }

grep -Fq 'onEditRecommendation: (InvestmentItem, Int, Boolean) -> Unit' "$SRC" || fail 'Dashboard bietet keinen direkten Empfehlungs-Edit-Callback.'
grep -Fq 'Text("Bearbeiten"' "$SRC" || fail 'Empfehlungszeile hat keinen Bearbeiten-Button.'
grep -Fq 'Text("Empfehlung bearbeiten"' "$SRC" || fail 'Heutige Empfehlung hat keinen direkten Bearbeiten-Button.'
grep -Fq 'Text("Signal"' "$SRC" || fail 'Signal wird nicht separat dargestellt.'
grep -Fq 'Text("Betrag"' "$SRC" || fail 'Empfehlungsbetrag wird nicht separat dargestellt.'
grep -Fq 'pendingActionPrefillMessage = "Empfehlung direkt bearbeiten · ${prefill.message}"' "$SRC" || fail 'Direkter Edit übernimmt die Empfehlungsdaten nicht in die Transaktionsmaske.'
grep -Fq 'pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }' "$SRC" || fail 'Empfohlener Betrag wird nicht vorbefüllt.'
grep -Fq 'pendingActionShares = prefill.shares' "$SRC" || fail 'Empfohlene Stückzahl wird nicht vorbefüllt.'
grep -Fq 'investmentDialogEntryType = if (sellMode) "SELL" else "BUY"' "$SRC" || fail 'Direkter Edit unterscheidet Kauf- und Verkaufsmaske nicht.'

echo "PASS: Empfehlungen zeigen Signal und Betrag getrennt und lassen sich direkt mit vorbefülltem Betrag/Stückzahl bearbeiten."
