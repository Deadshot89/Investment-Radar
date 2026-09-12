#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
STATE="android/app/src/main/java/de/tobias/investmentradar/InvestmentBudgetViewState.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/InvestmentBudgetStore.kt"
UI="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

# Ein Erstkauf eines eigenen Werts darf nicht mehr am Budget-Journal vorbeilaufen.
grep -Fq 'executeBuy(item.id, initialPurchase' "$VM" || fail 'Erstkauf eines eigenen Werts muss über executeBuy budgetwirksam gebucht werden.'

# Der Budget-Zustand muss eine sichtbare, aufbereitete Buchungshistorie liefern.
grep -Fq 'val history: List<BudgetHistoryItem>' "$STATE" || fail 'BudgetViewState muss eine Historie enthalten.'
grep -Fq 'entries: List<BudgetJournalEntry>' "$STATE" || fail 'BudgetViewState muss aus Summary und Journal-Einträgen aufgebaut werden.'
grep -Fq 'entries = readEntries(context)' "$STORE" || fail 'BudgetStore muss Journal-Einträge an den ViewState übergeben.'

# Die Historie muss für den Nutzer im Budgetbereich sichtbar sein.
grep -Fq 'BUDGET-HISTORIE' "$UI" || fail 'Im Budgetbereich fehlt die sichtbare Überschrift BUDGET-HISTORIE.'
grep -Fq 'current.history' "$UI" || fail 'Die Budget-Historie wird in der Oberfläche nicht gerendert.'

echo 'PASS: Budget-Historie und budgetwirksamer Erstkauf sind vollständig verdrahtet.'
# Finale Verifikation des vollständigen Feature-Stands.

grep -Fq 'ensureCurrentMonth' "$STORE" || fail 'Monatswechsel muss Restbudget und neues Monatsbudget automatisch fortführen.'
grep -Fq 'Rest Vormonate' "$UI" || fail 'Restbudget aus Vormonaten muss sichtbar erklärt werden.'
grep -Fq 'Budgetwirksam' "$UI" || fail 'Historische Transaktionen brauchen eine explizite Budgetwirksam-Auswahl.'
grep -Fq 'Davon Gebühren' "$UI" || fail 'Gebühren müssen bei Transaktionen erfassbar sein.'
