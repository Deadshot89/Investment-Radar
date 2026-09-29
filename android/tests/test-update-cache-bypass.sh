#!/usr/bin/env bash
set -euo pipefail

SRC="android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt"

grep -Fq 'releases/latest?installed=${BuildConfig.VERSION_CODE}' "$SRC" || {
  echo "Updater-URL braucht einen versionCode Cache-Buster"
  exit 1
}
grep -Fq 'setRequestProperty("Cache-Control", "no-cache, no-store")' "$SRC" || {
  echo "Updater muss HTTP-Caches deaktivieren"
  exit 1
}
grep -Fq 'setRequestProperty("Pragma", "no-cache")' "$SRC" || {
  echo "Updater braucht den Legacy-No-Cache-Header"
  exit 1
}
grep -Fq 'useCaches = false' "$SRC" || {
  echo "HttpURLConnection-Caching muss deaktiviert sein"
  exit 1
}

echo "PASS: In-App-Update umgeht veraltete HTTP-/Proxy-Caches"
