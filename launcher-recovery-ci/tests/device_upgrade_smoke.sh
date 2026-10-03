#!/usr/bin/env bash
set -euo pipefail

mkdir -p build/device-upgrade
BASE_APK="build/upgrade/launcher-v1.apk"
TARGET_APK="build/upgrade/launcher-v2.apk"
PKG="de.tobias.launcher.recovery"
COMPONENT="$PKG/.MainActivity"

assert_version_code() {
  local expected="$1"
  adb shell dumpsys package "$PKG" | tr -d '\r' | grep -m1 "versionCode=$expected "
}

resolve_home() {
  adb shell cmd package resolve-activity --brief --user 0 \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME | tr -d '\r'
}

set_home_and_verify() {
  local set_log="build/device-upgrade/set-home-v1.txt"
  local resolved_log="build/device-upgrade/home-before-update.txt"
  : > "$set_log"
  for attempt in 1 2 3 4 5; do
    echo "attempt=$attempt package-set-home" >> "$set_log"
    adb shell cmd package set-home-activity --user 0 "$COMPONENT" >> "$set_log" 2>&1 || true
    resolve_home | tee "$resolved_log"
    if grep -q "$PKG" "$resolved_log"; then
      return 0
    fi

    echo "attempt=$attempt role-add-home" >> "$set_log"
    adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >> "$set_log" 2>&1 || true
    sleep 2
    resolve_home | tee "$resolved_log"
    if grep -q "$PKG" "$resolved_log"; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: failed to assign and verify HOME role" >&2
  cat "$set_log" >&2
  cat "$resolved_log" >&2
  return 1
}

foreground() {
  adb shell dumpsys activity activities \
    | tr -d '\r' \
    | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' \
    | head -20
}

test -s "$BASE_APK"
test -s "$TARGET_APK"

adb uninstall "$PKG" >/dev/null 2>&1 || true
adb install "$BASE_APK" | tee build/device-upgrade/install-v1.txt
assert_version_code 1 | tee build/device-upgrade/version-v1.txt

set_home_and_verify

adb install -r "$TARGET_APK" | tee build/device-upgrade/install-v2.txt
assert_version_code 2 | tee build/device-upgrade/version-v2.txt

adb shell cmd package resolve-activity --brief --user 0 \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  | tr -d '\r' | tee build/device-upgrade/home-after-update.txt
grep -q "$PKG" build/device-upgrade/home-after-update.txt

adb shell am start -W \
  -a android.intent.action.MAIN \
  -c android.intent.category.HOME \
  | tee build/device-upgrade/home-start-after-update.txt || true
sleep 2
foreground | tee build/device-upgrade/home-foreground-after-update.txt
grep -q "$PKG" build/device-upgrade/home-foreground-after-update.txt

printf '%s\n' \
  'PASS: release v1 installed' \
  'PASS: HOME assigned before update' \
  'PASS: release v2 installed over v1 without uninstall/downgrade' \
  'PASS: versionCode advanced from 1 to 2' \
  'PASS: HOME assignment persisted across signed update' \
  'PASS: updated launcher resumed as HOME' \
  | tee build/device-upgrade/RESULT.txt
