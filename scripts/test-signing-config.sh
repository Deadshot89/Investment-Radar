#!/usr/bin/env bash
set -euo pipefail

fail() { echo "FAIL: $1" >&2; exit 1; }

APP_GRADLE="android/app/build.gradle.kts"
WORKFLOW=".github/workflows/android-build.yml"

grep -q 'versionCode = 10' "$APP_GRADLE" || fail "versionCode 10 fehlt"
grep -q 'versionName = "1.1.9"' "$APP_GRADLE" || fail "versionName 1.1.9 fehlt"
grep -q 'signingConfigs' "$APP_GRADLE" || fail "Release-Signing-Konfiguration fehlt"
grep -q 'ANDROID_KEYSTORE_PATH' "$APP_GRADLE" || fail "Keystore-Pfad wird nicht an Gradle übergeben"
grep -q 'assembleRelease' "$WORKFLOW" || fail "Workflow baut nicht die Release-APK"
grep -q 'ANDROID_KEYSTORE_BASE64' "$WORKFLOW" || fail "Keystore-Secret wird nicht verwendet"
grep -q 'ANDROID_KEYSTORE_PASSWORD' "$WORKFLOW" || fail "Keystore-Passwort-Secret wird nicht verwendet"
grep -q 'ANDROID_KEY_ALIAS' "$WORKFLOW" || fail "Alias-Secret wird nicht verwendet"
grep -q 'ANDROID_KEY_PASSWORD' "$WORKFLOW" || fail "Key-Passwort-Secret wird nicht verwendet"
grep -Eq 'apksigner.*verify|APKSIGNER.*verify' "$WORKFLOW" || fail "Signaturprüfung fehlt"
grep -q 'app-release.apk' "$WORKFLOW" || fail "Release-APK wird nicht hochgeladen"

if grep -q 'assembleDebug' "$WORKFLOW"; then
  fail "Workflow baut weiterhin Debug-APK"
fi


grep -q 'ANDROID_KEYSTORE_BASE64.txt' .gitignore || fail "Base64-Keystore-Ausgabe ist nicht gitignored"
test -f scripts/create-android-signing-key.ps1 || fail "PowerShell-Helfer zum einmaligen Erzeugen des Schlüssels fehlt"
if find . -type f \( -name '*.jks' -o -name '*.keystore' \) | grep -q .; then
  fail "Privater Keystore darf nicht im Paket/Repository liegen"
fi

echo "PASS: dauerhafte Release-Signierung ist konfiguriert"
