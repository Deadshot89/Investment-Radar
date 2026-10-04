#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/device
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="de.tobias.launcher.recovery"
COMPONENT="$PKG/.MainActivity"

resolve_home() {
  adb shell cmd package resolve-activity --brief --user 0 \
    -a android.intent.action.MAIN \
    -c android.intent.category.HOME | tr -d '\r'
}

set_home_and_verify() {
  local set_log="build/device/set-home.txt"
  local resolved_log="build/device/home-resolved.txt"
  : > "$set_log"
  for attempt in 1 2 3 4 5; do
    echo "attempt=$attempt role-add-home" >> "$set_log"
    adb shell cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >> "$set_log" 2>&1 || true
    sleep 2
    resolve_home | tee "$resolved_log"
    if grep -q "$PKG" "$resolved_log"; then return 0; fi

    echo "attempt=$attempt package-set-home" >> "$set_log"
    adb shell cmd package set-home-activity --user 0 "$COMPONENT" >> "$set_log" 2>&1 || true
    sleep 2
    resolve_home | tee "$resolved_log"
    if grep -q "$PKG" "$resolved_log"; then return 0; fi
    sleep 2
  done
  echo "ERROR: failed to assign and verify HOME role" >&2
  cat "$set_log" >&2
  cat "$resolved_log" >&2
  return 1
}

foreground() {
  adb shell dumpsys activity activities | tr -d '\r' | grep -E 'mResumedActivity|topResumedActivity|ResumedActivity' | head -20
}
window_focus() {
  adb shell dumpsys window windows | tr -d '\r' | grep -E 'mCurrentFocus|mFocusedApp' | head -20 || true
}
dump_ui() {
  local remote="$1" local_file="$2" attempt
  for attempt in 1 2 3 4 5; do
    rm -f "$local_file"
    adb shell rm -f "$remote" >/dev/null 2>&1 || true
    if adb shell uiautomator dump "$remote" >/dev/null 2>&1 \
      && adb shell test -s "$remote" >/dev/null 2>&1 \
      && adb pull "$remote" "$local_file" >/dev/null 2>&1 \
      && [ -s "$local_file" ]; then
      return 0
    fi
    sleep 2
  done
  echo "ERROR: UIAutomator did not produce $remote after 5 attempts" >&2
  adb shell uiautomator dump "$remote" || true
  return 1
}
tap_text() {
  local wanted="$1" coords
  dump_ui /sdcard/window.xml build/device/window.xml
  coords="$(python3 tests/tap_text.py build/device/window.xml "$wanted")"
  adb shell input tap $coords
  sleep 2
}
dismiss_known_system_faults() {
  local coords
  for _ in 1 2 3; do
    if ! dump_ui /sdcard/system-dialog.xml build/device/system-dialog.xml; then
      sleep 2
      continue
    fi
    if grep -Eq 'text="(System UI|Process system) isn.t responding"' build/device/system-dialog.xml; then
      coords="$(python3 tests/tap_text.py build/device/system-dialog.xml "Wait")"
      adb shell input tap $coords
      sleep 3
      continue
    fi
    if grep -q 'text="Bluetooth keeps stopping"' build/device/system-dialog.xml \
      && grep -q 'resource-id="android:id/aerr_close"' build/device/system-dialog.xml; then
      coords="$(python3 tests/tap_text.py build/device/system-dialog.xml "Close app")"
      adb shell input tap $coords
      sleep 3
      continue
    fi
    return 0
  done

  if ! dump_ui /sdcard/system-dialog-final.xml build/device/system-dialog-final.xml; then return 0; fi
  if grep -Eq 'text="(System UI|Process system) isn.t responding"|text="Bluetooth keeps stopping"' build/device/system-dialog-final.xml; then
    echo "ERROR: known Android system fault dialog remained visible after retry budget" >&2
    cat build/device/system-dialog-final.xml >&2
    return 1
  fi
  return 0
}

adb shell settings put global hide_error_dialogs 1
test "$(adb shell settings get global hide_error_dialogs | tr -d '\r')" = "1"
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true
adb logcat -c

adb install -r "$APK" | tee build/device/install.txt
adb shell pm list packages | tr -d '\r' | grep -Fx "package:$PKG"
set_home_and_verify
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME | tee build/device/home-start.txt || true
sleep 2
foreground | tee build/device/home-foreground.txt
grep -q "$PKG" build/device/home-foreground.txt
dismiss_known_system_faults
adb exec-out screencap -p > build/device/home.png

tap_text "Apps"
adb exec-out screencap -p > build/device/drawer.png
dump_ui /sdcard/drawer.xml build/device/drawer.xml
python3 tests/assert_drawer_grid.py build/device/drawer.xml --present Settings --present Calendar

# Search diagnostics: capture the exact focus/activity state around text entry so
# a launcher state transition can be distinguished from a UIAutomator artifact.
tap_text "Apps suchen"
adb exec-out screencap -p > build/device/search-focused.png
foreground | tee build/device/search-focused-foreground.txt
window_focus | tee build/device/search-focused-window.txt
dump_ui /sdcard/search-focused.xml build/device/search-focused.xml || true
adb logcat -d -v brief > build/device/logcat-before-search-text.txt

adb shell input text Set
sleep 2
adb exec-out screencap -p > build/device/search-after-text.png
foreground | tee build/device/search-after-text-foreground.txt
window_focus | tee build/device/search-after-text-window.txt
adb logcat -d -v brief > build/device/logcat-after-search-text.txt

dump_ui /sdcard/drawer-filtered.xml build/device/drawer-filtered.xml
grep -q 'text="Set"' build/device/drawer-filtered.xml
python3 tests/assert_drawer_grid.py build/device/drawer-filtered.xml --present Settings --absent Calendar
tap_text "✕"
tap_text "Apps"
dump_ui /sdcard/drawer-reopened.xml build/device/drawer-reopened.xml
if grep -q 'text="Set"' build/device/drawer-reopened.xml; then echo "Drawer search query remained visible after close/reopen" >&2; exit 23; fi
python3 tests/assert_drawer_grid.py build/device/drawer-reopened.xml --present Calendar --present Settings

# Regression: leaving a filtered drawer through an app and returning with Android
# Back must preserve both the visible query and the matching filtered result set.
tap_text "Apps suchen"
adb shell input text Set
sleep 2
dump_ui /sdcard/drawer-before-back-launch.xml build/device/drawer-before-back-launch.xml
grep -q 'text="Set"' build/device/drawer-before-back-launch.xml
python3 tests/assert_drawer_grid.py build/device/drawer-before-back-launch.xml --present Settings --absent Calendar
coords="$(python3 tests/tap_text.py build/device/drawer-before-back-launch.xml "Settings")"
adb shell input tap $coords
sleep 2
foreground | tee build/device/app-launch-foreground.txt
grep -q 'com.android.settings' build/device/app-launch-foreground.txt
adb shell input keyevent KEYCODE_BACK
sleep 2
foreground | tee build/device/launcher-after-back.txt
grep -q "$PKG" build/device/launcher-after-back.txt
dismiss_known_system_faults
dump_ui /sdcard/drawer-after-back.xml build/device/drawer-after-back.xml
grep -q 'text="Set"' build/device/drawer-after-back.xml
python3 tests/assert_drawer_grid.py build/device/drawer-after-back.xml --present Settings --absent Calendar

adb shell input keyevent KEYCODE_HOME
sleep 2
foreground | tee build/device/home-after-app.txt
grep -q "$PKG" build/device/home-after-app.txt
dismiss_known_system_faults
dump_ui /sdcard/home-after-app-ui.xml build/device/home-after-app-ui.xml
if grep -q 'resource-id="android:id/search_src_text"' build/device/home-after-app-ui.xml; then echo "Drawer remained visible after returning HOME from a launched app" >&2; exit 21; fi
adb shell am force-stop "$PKG"
sleep 1
adb shell input keyevent KEYCODE_HOME
sleep 3
foreground | tee build/device/home-after-force-stop.txt
grep -q "$PKG" build/device/home-after-force-stop.txt
adb reboot
adb wait-for-device
booted=0
for _ in $(seq 1 90); do
  if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then booted=1; break; fi
  sleep 2
done
test "$booted" = "1"
adb shell settings put global hide_error_dialogs 1
adb shell input keyevent KEYCODE_HOME
sleep 3
dismiss_known_system_faults
adb shell cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tee build/device/home-resolved-after-reboot.txt
grep -q "$PKG" build/device/home-resolved-after-reboot.txt
foreground | tee build/device/home-after-reboot.txt
grep -q "$PKG" build/device/home-after-reboot.txt
adb exec-out screencap -p > build/device/home-after-reboot.png
adb logcat -d -v brief > build/device/logcat.txt
if grep -A20 -B5 'FATAL EXCEPTION' build/device/logcat.txt | grep -q "$PKG"; then echo "Launcher crash found in logcat" >&2; exit 1; fi
if grep -Fq "ANR in $PKG" build/device/logcat.txt; then echo "Launcher ANR found in logcat" >&2; exit 24; fi
printf '%s\n' \
  'PASS: APK install' \
  'PASS: default HOME resolution' \
  'PASS: HOME foreground' \
  'PASS: app drawer and search reset' \
  'PASS: filtered drawer survives app Back navigation' \
  'PASS: launch Settings from drawer' \
  'PASS: return HOME closes transient drawer state' \
  'PASS: HOME after force-stop' \
  'PASS: default HOME persisted after reboot' \
  'PASS: HOME foreground after reboot' \
  'PASS: no launcher fatal exception or ANR in logcat' | tee build/device/RESULT.txt
