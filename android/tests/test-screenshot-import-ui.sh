#!/usr/bin/env bash
set -euo pipefail

DASH="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
NAV="android/app/src/main/java/de/tobias/investmentradar/AppNavigationState.kt"
SCREEN="android/app/src/main/java/de/tobias/investmentradar/DepotScreenshotImportScreen.kt"
MANIFEST="android/app/src/main/AndroidManifest.xml"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

[ -f "$SCREEN" ] || fail "DepotScreenshotImportScreen.kt fehlt."
grep -Fq 'onUpdateDepot: () -> Unit' "$DASH" || fail 'PortfolioDashboard braucht onUpdateDepot.'
grep -Fq 'Text("Depot aktualisieren"' "$DASH" || fail 'Button Depot aktualisieren fehlt.'
grep -Fq 'DEPOT_SCREENSHOT_IMPORT' "$NAV" || fail 'Navigation kennt DEPOT_SCREENSHOT_IMPORT nicht.'
grep -Fq 'DepotScreenshotImportScreen(' "$MAIN" || fail 'Import-Screen ist nicht in MainActivity verdrahtet.'
grep -Fq 'ActivityResultContracts.OpenMultipleDocuments()' "$SCREEN" || fail 'Mehrfachauswahl muss OpenMultipleDocuments verwenden.'
grep -Fq 'arrayOf("image/*")' "$SCREEN" || fail 'Screenshot-Picker muss auf image/* begrenzt sein.'
grep -Fq 'Text("Änderungen übernehmen"' "$SCREEN" || fail 'Finaler Apply-Button fehlt.'
grep -Fq 'Screenshots analysiert' "$SCREEN" || fail 'Fortschrittsdarstellung für analysierte Screenshots fehlt.'

if grep -Eq 'READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES' "$MANIFEST"; then
  fail 'Screenshot-Import darf keine breite Storage-Berechtigung hinzufügen.'
fi

echo "PASS: Screenshot-Import UI source contract"
