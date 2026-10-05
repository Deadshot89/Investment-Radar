from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ci = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
smoke = (ROOT / 'tests/device_smoke.sh').read_text(encoding='utf-8')
upgrade = (ROOT / 'tests/device_upgrade_smoke.sh').read_text(encoding='utf-8')
drawer_assert = (ROOT / 'tests/assert_drawer_grid.py').read_text(encoding='utf-8')

assert 'APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"' in smoke, \
    'device smoke must accept an explicit APK so release artifacts can run the full lifecycle'
assert 'adb uninstall "$PKG" | tee build/device-upgrade/uninstall-after-test.txt' not in upgrade, \
    'upgrade smoke must leave the updated release installed for the lifecycle test'
assert 'bash tests/device_smoke.sh build/upgrade/launcher-v2.apk' in ci, \
    'CI must run the full lifecycle against the signed release-v2 artifact'

print('PASS: full launcher lifecycle is executed against the signed release upgrade target')

for script_name in ('tests/device_smoke.sh', 'tests/device_upgrade_smoke.sh'):
    script = (ROOT / script_name).read_text(encoding='utf-8')
    assert 'set_home_and_verify()' in script, f'{script_name} must retry HOME assignment and verify the resolver'
    assert 'cmd package resolve-activity' in script, f'{script_name} must verify the HOME resolver after assignment'
    home_block = script.split('set_home_and_verify() {', 1)[1].split('\n}', 1)[0]
    assert home_block.index('cmd role add-role-holder') < home_block.index('cmd package set-home-activity'), \
        f'{script_name} must grant the real HOME role before using package preferred-activity fallback'
print('PASS: device tests grant the real HOME role first and verify resolver state')

assert 'grep -m1 "versionCode=$expected "' not in upgrade, \
    'version assertion must not short-circuit a pipe under pipefail and trigger SIGPIPE/141'
print('PASS: version assertion consumes full dumpsys output without SIGPIPE under pipefail')

assert 'pm list packages --show-versioncode --user 0 "$PKG"' in upgrade, \
    'installed version verification must use package-manager version listing on modern Android'
assert 'dumpsys package "$PKG"' not in upgrade, \
    'installed version verification must not depend on unstable dumpsys package text formatting'
print('PASS: installed version check uses stable package-manager version listing')

assert 'install_with_retry()' in upgrade, \
    'upgrade smoke must retry transient adb/package-manager install failures'
assert 'for attempt in 1 2 3; do' in upgrade, \
    'upgrade install retry must be bounded'
assert 'adb install -r "$TARGET_APK"' in upgrade, \
    'upgrade retry must preserve in-place install semantics'
assert 'ERROR: failed to install release v2 after 3 attempts' in upgrade, \
    'upgrade retry must fail closed after the retry budget is exhausted'
print('PASS: release upgrade retries transient package-manager transport failures with a bounded fail-closed policy')


dump_ui_block = smoke.split('dump_ui() {', 1)[1].split('\n}', 1)[0]
assert 'for attempt in 1 2 3 4 5; do' in dump_ui_block, \
    'UIAutomator dump must retry transient null-root failures on slow emulators'
assert 'adb shell rm -f "$remote"' in dump_ui_block, \
    'UI dump retry must remove stale remote XML before each attempt'
assert 'adb shell test -s "$remote"' in dump_ui_block, \
    'UI dump retry must verify that a fresh XML file was actually produced'
print('PASS: UIAutomator dumps retry transient null-root failures without reusing stale XML')

assert 'android.widget.GridView' in drawer_assert, \
    'drawer assertion helper must scope checks to GridView content'
assert 'max(grids, key=lambda node: area(' in drawer_assert, \
    'drawer assertion helper must select the main drawer grid rather than smaller homescreen favorites'
assert 'python3 tests/assert_drawer_grid.py build/device/drawer-filtered.xml --present Settings --absent Calendar' in smoke, \
    'filtered drawer verification must inspect the actual drawer grid instead of globally grepping the UI tree'
assert 'Search filter did not narrow drawer results' not in smoke, \
    'legacy global Calendar grep must be removed because underlying translucent homescreen content remains in UIAutomator tree'
print('PASS: drawer filtering assertions ignore underlying homescreen favorites and validate only drawer grid contents')

assert 'drawer-after-back.xml' in smoke, \
    'device smoke must capture drawer state after returning from a launched app with Android Back'
assert 'adb shell input keyevent KEYCODE_BACK' in smoke, \
    'device smoke must exercise app-to-launcher Back navigation instead of only HOME navigation'
assert 'python3 tests/assert_drawer_grid.py build/device/drawer-after-back.xml --present Settings --absent Calendar' in smoke, \
    'drawer filter must remain applied after returning with Back'
assert 'PASS: filtered drawer survives app Back navigation' in smoke, \
    'device smoke result must record the Back-navigation drawer regression gate'
print('PASS: filtered drawer query and results are verified after app Back navigation')

assert 'adb shell settings put global hide_error_dialogs 1' in smoke, \
    'device smoke must suppress emulator-owned crash/ANR dialogs before UIAutomator interaction'
assert "ANR in $PKG" in smoke or "ANR in ${PKG}" in smoke, \
    'dialog suppression must be paired with a hard launcher-ANR logcat gate'
print('PASS: emulator-owned error dialogs are suppressed without masking launcher ANRs')

assert 'System UI|Process system' in smoke, \
    'device smoke must recognize both legacy System UI and API 36 process-system ANR titles'
assert 'text="(System UI|Process system) isn.t responding"' in smoke, \
    'system ANR dismissal must be narrowly scoped to known Android system dialog titles'
print('PASS: device smoke recognizes API 36 process-system ANR dialogs without broadening to app ANRs')

assert 'text="Bluetooth keeps stopping"' in smoke, \
    'device smoke must recognize the observed API 36 emulator Bluetooth crash dialog'
assert 'android:id/aerr_close' in smoke and '"Close app"' in smoke, \
    'known Bluetooth crash handling must close the crashing system component instead of navigating to app info'
assert '.*keeps stopping' not in smoke and '.+keeps stopping' not in smoke, \
    'system crash handling must not match arbitrary app crash dialogs'
assert 'T Launcher Recovery keeps stopping' not in smoke, \
    'device smoke must never whitelist the launcher crash dialog'
print('PASS: device smoke narrowly dismisses the observed Bluetooth system crash without hiding launcher crashes')

fault_block = smoke.split('dismiss_known_system_faults() {', 1)[1].split('\n}', 1)[0]
assert 'ERROR: known Android system fault dialog remained visible after retry budget' in fault_block, \
    'system-fault handler must fail closed instead of continuing behind a still-visible blocking dialog'
assert 'return 1' in fault_block, \
    'system-fault handler must return failure when a known blocking dialog survives the retry budget'
assert 'if ! dump_ui /sdcard/system-dialog.xml build/device/system-dialog.xml; then' in fault_block, \
    'optional system-fault probing must guard transient UIAutomator null-root failures under set -e'
assert 'if ! dump_ui /sdcard/system-dialog-final.xml build/device/system-dialog-final.xml; then' in fault_block, \
    'final optional system-fault probe must not abort the lifecycle solely because UIAutomator has no root'
print('PASS: system-fault probing tolerates transient UIAutomator null-root failures but known dialogs still fail closed')

assert 'runs-on: macos-15-intel' in ci, \
    'standalone CI must use hardware-accelerated Intel macOS for API 36 lifecycle'
assert 'api-level: 36' in ci, \
    'standalone CI must exercise API 36 because targetSdk is 36'
assert 'api-level: 29' not in ci, \
    'standalone CI must not regress lifecycle coverage to API 29'
assert 'emulator-boot-timeout: 900' in ci, \
    'standalone CI must allow bounded headroom for observed slow API 36 emulator boots'
assert 'python3 tests/test_device_release_lifecycle_contract.py' in ci, \
    'standalone CI must execute the lifecycle contract that protects its device-test guarantees'
assert 'Enable KVM for API 36 emulator' not in ci, \
    'macOS standalone CI must not execute Linux-only KVM setup'
assert 'udevadm' not in ci and '/dev/kvm' not in ci, \
    'macOS standalone CI must not retain Linux KVM commands'
assert 'ReactiveCircus/android-emulator-runner@v2' in ci, \
    'standalone CI must run the real Android emulator lifecycle'
print('PASS: standalone CI is self-contained and exercises the resilient target API 36 lifecycle')
