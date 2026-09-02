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

echo "PASS 1.2.0 manual update status UI"
