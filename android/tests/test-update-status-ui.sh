#!/usr/bin/env bash
set -euo pipefail

MANAGER="android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

grep -Fq 'suspend fun checkResult' "$MANAGER"
grep -Fq 'UpdateCheckResult.Current' "$MANAGER"
grep -Fq 'UpdateCheckResult.Error' "$MANAGER"
grep -Fq 'AppUpdateManager.checkResult' "$MAIN"
grep -Fq 'Du nutzt bereits die aktuelle Version' "$MAIN"
grep -Fq 'Update konnte nicht geprüft werden' "$MAIN"

# Die installierte App-Version muss im Header sichtbar sein.
grep -Fq 'v${BuildConfig.VERSION_NAME}' "$MAIN"

# Wenn eine neuere Version vorhanden ist, muss der Header statt des neutralen Update-Buttons
# einen klaren Versionshinweis anzeigen.
grep -Fq 'Update verfügbar · v${availableUpdate!!.versionName}' "$MAIN"

echo "PASS manual update status UI"
echo "PASS installed app version visible in header"
echo "PASS update availability badge includes target version"
