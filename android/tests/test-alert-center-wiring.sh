#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
PUSH="android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt"

grep -Fq 'StateFlow<List<StoredAlert>>' "$VM"
grep -Fq 'AlertStore.mergeRemote' "$VM"
grep -Fq 'AlertPreferencesStore.save' "$VM"
grep -Fq 'markAllAlertsRead' "$VM"
grep -Fq 'deleteAlert' "$VM"
grep -Fq 'clearAlerts' "$VM"
grep -Fq 'vm.alerts.collectAsState' "$MAIN"
grep -Fq 'vm.alertPreferences.collectAsState' "$MAIN"
grep -Fq 'onMarkAllRead = vm::markAllAlertsRead' "$MAIN"
grep -Fq 'missingAlertItemMessage' "$MAIN"
grep -Fq 'else -> missingAlertItemMessage = "Das Wertpapier ist im aktuellen Radar nicht mehr verfügbar."' "$MAIN"
grep -Fq 'Wertpapier nicht verfügbar' "$MAIN"

grep -Fq 'putExtra("openItemId", itemId)' "$PUSH"
grep -Fq 'initialDetailId' "$MAIN"
grep -Fq 'intent.getStringExtra("openItemId")' "$MAIN"
grep -Fq 'selectedDetailId by remember { mutableStateOf(initialDetailId)' "$MAIN"
grep -Fq 'detailReturnTab by remember { mutableIntStateOf(if (initialDetailId.isNullOrBlank()) initialTab.coerceIn(0, 3) else 3) }' "$MAIN"

if grep -Fq 'private fun AlertsScreen(' "$MAIN"; then
  echo 'Legacy private AlertsScreen still present in MainActivity'
  exit 1
fi

echo "PASS 1.2.0 alert center wiring"
echo "PASS push detail deep link"
