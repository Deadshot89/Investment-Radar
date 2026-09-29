#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
BACKUP="android/app/src/main/java/de/tobias/investmentradar/UserDataBackup.kt"
MANIFEST="android/app/src/main/AndroidManifest.xml"
HEALTH="backend/src/functions/health.mjs"
BUILD_INFO="backend/src/lib/buildInfo.mjs"
ANDROID_WF=".github/workflows/android-build.yml"
BACKEND_WF=".github/workflows/backend-deploy.yml"

fail(){ echo "FAIL: $1" >&2; exit 1; }

grep -Fq 'ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)' "$VM" || fail 'Periodischer Refresh ist nicht an den App-Lifecycle gekoppelt.'
grep -Fq 'if (refreshJob?.isActive == true) return' "$VM" || fail 'Parallele Refreshes werden nicht verhindert.'
grep -Fq 'val refreshNotice: StateFlow<String?>' "$VM" || fail 'Refreshfehler werden nach geladenen Daten nicht separat sichtbar gehalten.'
grep -Fq 'refreshNotice by vm.refreshNotice.collectAsState()' "$MAIN" || fail 'Refreshfehler werden in der UI nicht dargestellt.'
grep -Fq 'android:allowBackup="false"' "$MANIFEST" || fail 'Lokale Finanzdaten dürfen nicht in Android Auto Backup landen.'
grep -Fq 'android:fullBackupContent="false"' "$MANIFEST" || fail 'Full Backup muss für lokale Finanzdaten deaktiviert sein.'
grep -Fq 'Text("DATENSICHERUNG"' "$MAIN" || fail 'Datensicherung ist in der Geldverwaltung nicht erreichbar.'
grep -Fq 'Text("Backup exportieren"' "$MAIN" || fail 'Backup-Export fehlt in der UI.'
grep -Fq 'Text("Backup wiederherstellen"' "$MAIN" || fail 'Backup-Restore fehlt in der UI.'
grep -Fq 'Text("Backup wiederherstellen?")' "$MAIN" || fail 'Destruktiver Restore hat keine Bestätigung.'
grep -Fq 'Text("Datei auswählen")' "$MAIN" || fail 'Restore-Bestätigung öffnet den Dateidialog nicht.'
grep -Fq 'UserDataBackupManager.restoreJson' "$MAIN" || fail 'Backup-Restore ist nicht mit dem Manager verdrahtet.'
grep -Fq 'reloadLocalUserDataAfterRestore' "$VM" || fail 'Nach Restore werden lokale ViewModel-Daten nicht neu geladen.'
grep -Fq 'SCHEMA_VERSION = 1' "$BACKUP" || fail 'Backup-Format ist nicht versioniert.'
grep -Fq 'MAX_BACKUP_BYTES = 5 * 1024 * 1024' "$BACKUP" || fail 'Backup-Import hat kein Größenlimit.'
grep -Fq '"investment_radar_portfolio"' "$BACKUP" || fail 'Depotdaten fehlen im Backup.'
grep -Fq '"investment_radar_budget"' "$BACKUP" || fail 'Budgetdaten fehlen im Backup.'
grep -Fq '"investment_radar_savings_plans"' "$BACKUP" || fail 'Sparplandaten fehlen im Backup.'
grep -Fq 'sourceRevision: buildInfo.sourceRevision' "$HEALTH" || fail 'Healthcheck liefert keine Quellrevision.'
grep -Fq 'deployId: buildInfo.deployId' "$HEALTH" || fail 'Healthcheck liefert keine Deploy-ID.'
grep -Fq 'apiSchemaVersion: buildInfo.apiSchemaVersion' "$HEALTH" || fail 'Healthcheck liefert keine API-Schemaversion.'
grep -Fq 'API_SCHEMA_VERSION = "2026-09-14.1"' "$BUILD_INFO" || fail 'API-Schema ist nicht versioniert.'
grep -Fq 'EXPECTED_API_SCHEMA: "2026-09-14.1"' "$ANDROID_WF" || fail 'Android Release Gate prüft API-Schema nicht.'
grep -Fq 'BACKEND_REVISION="$(git rev-parse HEAD:backend)"' "$BACKEND_WF" || fail 'Backend Deploy berechnet keine exakte Backend-Inhaltsrevision.'
grep -Fq 'LAST_REVISION' "$BACKEND_WF" || fail 'Backend Deploy prüft die exakte live Backend-Inhaltsrevision nicht.'
grep -Fq 'EXPECTED_BACKEND_REVISION="$(git rev-parse HEAD:backend)"' "$ANDROID_WF" || fail 'Android Release Gate ist nicht an die exakte Backend-Inhaltsrevision gebunden.'
grep -Fq 'LAST_DEPLOY_ID' "$BACKEND_WF" || fail 'Backend Deploy prüft die exakte Deploy-ID nicht.'
grep -Fq 'actions: read' "$ANDROID_WF" || fail 'Android Release Gate darf Companion-Workflowstatus nicht lesen.'
grep -Fq 'Verify companion release gates' "$ANDROID_WF" || fail 'APK-Publish wartet nicht auf die übrigen Release-Gates.'
grep -Fq 'Android Contract Tests' "$ANDROID_WF" || fail 'Contract-Gate ist nicht an den APK-Publish gekoppelt.'
grep -Fq 'Android Instrumented UI Tests' "$ANDROID_WF" || fail 'UI-Gate ist nicht an den APK-Publish gekoppelt.'
grep -Fq 'head_sha=$GITHUB_SHA' "$ANDROID_WF" || fail 'Companion-Gates werden nicht für exakt denselben Commit geprüft.'
companion_line=$(grep -n 'Verify companion release gates' "$ANDROID_WF" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'Publish APK for in-app updates' "$ANDROID_WF" | head -1 | cut -d: -f1)
[ -n "$companion_line" ] && [ -n "$publish_line" ] && [ "$companion_line" -lt "$publish_line" ] || fail 'Companion-Gates müssen vor dem APK-Publish laufen.'

echo "PASS: Lifecycle-Refresh, Backup, Deployment-Provenienz und gekoppelte Release-Gates sind verdrahtet."
