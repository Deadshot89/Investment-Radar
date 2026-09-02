#!/usr/bin/env bash
set -euo pipefail

FILE="android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt"

grep -q 'registerActivityLifecycleCallbacks' "$FILE"
grep -q 'canRequestPackageInstalls()' "$FILE"
grep -q 'Update startet automatisch' "$FILE"
grep -q 'startDownload(context, update)' "$FILE"

echo "PASS 1.1.28 update resume"
