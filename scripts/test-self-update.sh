#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/android/app/src/main/java/de/tobias/investmentradar/AppUpdateManager.kt"
MANIFEST="$ROOT/android/app/src/main/AndroidManifest.xml"
PATHS="$ROOT/android/app/src/main/res/xml/update_file_paths.xml"
GRADLE="$ROOT/android/app/build.gradle.kts"
WORKFLOW="$ROOT/.github/workflows/android-build.yml"

fail() { echo "FAIL: $*" >&2; exit 1; }

[[ -f "$APP" ]] || fail "AppUpdateManager.kt fehlt"
[[ -f "$MANIFEST" ]] || fail "AndroidManifest.xml fehlt"
[[ -f "$PATHS" ]] || fail "update_file_paths.xml fehlt"
[[ -f "$WORKFLOW" ]] || fail "android-build.yml fehlt"

grep -q 'api.github.com/repos/' "$APP" || fail "GitHub Releases API wird nicht verwendet"
grep -q 'browser_download_url' "$APP" || fail "Release-APK URL wird nicht gelesen"
grep -q 'DownloadManager' "$APP" || fail "APK wird nicht per DownloadManager geladen"
grep -q 'FileProvider' "$APP" || fail "APK wird nicht sicher per FileProvider geöffnet"
grep -q 'ACTION_MANAGE_UNKNOWN_APP_SOURCES' "$APP" || fail "Unknown-app permission handling fehlt"
grep -q 'application/vnd.android.package-archive' "$APP" || fail "APK MIME type fehlt"
grep -q 'investment-radar.apk' "$APP" || fail "Release asset name fehlt"

grep -q 'REQUEST_INSTALL_PACKAGES' "$MANIFEST" || fail "REQUEST_INSTALL_PACKAGES fehlt"
grep -q 'androidx.core.content.FileProvider' "$MANIFEST" || fail "FileProvider fehlt"
grep -q 'update_file_paths' "$MANIFEST" || fail "FileProvider paths fehlen"

grep -q 'GITHUB_REPOSITORY' "$GRADLE" || fail "GITHUB_REPOSITORY BuildConfig fehlt"
grep -q 'versionCode = 20' "$GRADLE" || fail "versionCode 20 fehlt"
grep -q 'versionName = "1.1.19"' "$GRADLE" || fail "versionName 1.1.19 fehlt"

grep -q '^permissions:' "$WORKFLOW" || fail "Workflow permissions fehlen"
grep -q 'contents: write' "$WORKFLOW" || fail "contents write permission fehlt"
grep -q 'gh release' "$WORKFLOW" || fail "GitHub Release publish fehlt"
grep -q 'investment-radar.apk' "$WORKFLOW" || fail "APK release asset fehlt"
grep -q 'PGITHUB_REPOSITORY' "$WORKFLOW" || fail "Repository slug wird nicht in App gebaut"

echo "PASS: self-update integration"
