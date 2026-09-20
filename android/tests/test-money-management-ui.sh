#!/usr/bin/env bash
set -euo pipefail

SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'title = { Text("Geldverwaltung") }' "$SRC" || fail 'Budget-Dialog muss Geldverwaltung heißen.'
grep -Fq 'Text("Was soll ich mit meinem Geld tun?"' "$SRC" || fail 'Konkrete Geldaktions-Sektion fehlt.'
grep -Fq 'actionCenter: DepotActionCenterState' "$SRC" || fail 'Budget-Dialog muss den Depot-Aktionsplan erhalten.'
grep -Fq 'Text(action.cashImpactText' "$SRC" || fail 'Budgetwirkung der Aktion muss sichtbar sein.'
grep -Fq 'Text("Geldverlauf"' "$SRC" || fail 'Budgethistorie muss als Geldverlauf benannt sein.'
grep -Fq 'Text("+ 5 €")' "$SRC" || fail '5-Euro-Schnellbuchung für Wechselgeld fehlt.'
grep -Fq 'Text("+ 10 €")' "$SRC" || fail '10-Euro-Schnellbuchung für Wechselgeld fehlt.'
grep -Fq 'onExecuteAction: (DepotActionCenterItem) -> Unit' "$SRC" || fail 'Geldverwaltung muss Aktionen in die manuelle Erfassung weiterreichen.'
grep -Fq 'label = { Text("Geld") }' "$SRC" || fail 'Geldverwaltung muss als eigener Hauptreiter erreichbar sein.'
grep -Fq 'private fun MoneyManagementScreen(' "$SRC" || fail 'Eigener Geldverwaltungs-Screen fehlt.'
grep -Fq 'Netto-Verkaufserlös nach Gebühren' "$SRC" || fail 'Verkauf darf nicht als Cash-Gutschrift beschriftet werden.'
grep -Fq 'Er bleibt privat und erhöht App-Cash und Kaufbudget nicht.' "$SRC" || fail 'Private Verwendung von Verkaufserlösen ist nicht klar beschrieben.'
grep -Fq 'val isSale = entry.title == "Verkauf"' "$SRC" || fail 'Verkauf wird im Geldverlauf nicht neutral dargestellt.'
grep -Fq 'visibleBudgetHistoryNote' "$SRC" || fail 'Interne Geldbedarf-Kennung darf nicht roh angezeigt werden.'
grep -Fq 'ihre Erlöse werden privat verwendet und erhöhen dein App-Cash nicht' "$SRC" || fail 'Verkaufserlöse dürfen nicht als App-Cash dargestellt werden.'
grep -Fq 'tab = 4' "$SRC" || fail 'Geldverwaltungs-Reiter ist nicht in der Root-Navigation verdrahtet.'

echo 'PASS: Geldverwaltung zeigt Kontostand, konkrete Aktionen, Schnellbuchungen und Geldverlauf.'
