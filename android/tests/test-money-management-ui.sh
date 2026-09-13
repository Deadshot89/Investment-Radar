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

echo 'PASS: Geldverwaltung zeigt Kontostand, konkrete Aktionen, Schnellbuchungen und Geldverlauf.'
