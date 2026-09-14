#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
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
grep -Fq 'sourceRevision: buildInfo.sourceRevision' "$HEALTH" || fail 'Healthcheck liefert keine Quellrevision.'
grep -Fq 'deployId: buildInfo.deployId' "$HEALTH" || fail 'Healthcheck liefert keine Deploy-ID.'
grep -Fq 'apiSchemaVersion: buildInfo.apiSchemaVersion' "$HEALTH" || fail 'Healthcheck liefert keine API-Schemaversion.'
grep -Fq 'API_SCHEMA_VERSION = "2026-09-14.1"' "$BUILD_INFO" || fail 'API-Schema ist nicht versioniert.'
grep -Fq 'EXPECTED_API_SCHEMA: "2026-09-14.1"' "$ANDROID_WF" || fail 'Android Release Gate prüft API-Schema nicht.'
grep -Fq 'BACKEND_REVISION="$(git rev-parse HEAD:backend)"' "$BACKEND_WF" || fail 'Backend Deploy berechnet keine exakte Backend-Inhaltsrevision.'
grep -Fq 'LAST_REVISION' "$BACKEND_WF" || fail 'Backend Deploy prüft die exakte live Backend-Inhaltsrevision nicht.'
grep -Fq 'EXPECTED_BACKEND_REVISION="$(git rev-parse HEAD:backend)"' "$ANDROID_WF" || fail 'Android Release Gate ist nicht an die exakte Backend-Inhaltsrevision gebunden.'
grep -Fq 'LAST_DEPLOY_ID' "$BACKEND_WF" || fail 'Backend Deploy prüft die exakte Deploy-ID nicht.'

echo "PASS: Lifecycle-Refresh, sichtbare Stale-Daten-Warnung, Backup-Schutz und Deployment-Provenienz sind verdrahtet."
