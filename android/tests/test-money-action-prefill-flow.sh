#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# Eine konkrete Aktion aus der Geldverwaltung muss ihren empfohlenen Betrag
# bis in die manuelle Kauf-/Verkaufsmaske mitnehmen. Die neue Vorbelegung
# übernimmt weiterhin action.displayAmountEur und ergänzt daraus die Stückzahl.
grep -Fq 'var pendingActionAmountEur by remember { mutableStateOf<Double?>(null) }' "$UI" || fail 'Empfohlener Aktionsbetrag wird nicht als UI-Zustand gehalten.'
grep -Fq 'amountEur = action.displayAmountEur' "$UI" || fail 'Empfohlener Aktionsbetrag wird nicht an die Vorbelegung übergeben.'
grep -Fq 'pendingActionAmountEur = prefill.amountEur.takeIf { it > 0.0 }' "$UI" || fail 'Berechneter Aktionsbetrag wird beim Öffnen der Transaktionsmaske nicht übernommen.'
grep -Fq 'initialAmountEur = pendingActionAmountEur' "$UI" || fail 'Transaktionsdialog erhält den empfohlenen Aktionsbetrag nicht.'
grep -Fq 'initialAmountEur: Double? = null' "$UI" || fail 'PurchaseHistoryDialog unterstützt keinen empfohlenen Startbetrag.'
grep -Fq 'mutableStateOf(initialAmountEur?.takeIf { it > 0.0 }?.let(::formatEditableNumber).orEmpty())' "$UI" || fail 'Empfohlener Betrag wird nicht sichtbar vorbefüllt.'

# Die Order bleibt manuell: erst der vorhandene Bestätigungsbutton bucht über
# die Budget-Execution-Services. Kein automatischer Trade beim Öffnen.
grep -Fq 'onExecutePurchase(purchase, fee)' "$UI" || fail 'Manuell bestätigter Kauf muss weiter über executeBuy laufen.'
grep -Fq 'onExecuteSale(sale, fee)' "$UI" || fail 'Manuell bestätigter Verkauf muss weiter über executeSale laufen.'

# Nach Schließen/Abschluss darf der Vorschlagsbetrag nicht in die nächste
# beliebige Depotbearbeitung durchsickern.
grep -Fq 'pendingActionAmountEur = null' "$UI" || fail 'Empfohlener Aktionsbetrag wird nach dem Dialog nicht zurückgesetzt.'

echo 'PASS: Geldaktion übernimmt den Betrag in die manuelle Transaktionsmaske und bleibt budgetwirksam bestätigt.'
