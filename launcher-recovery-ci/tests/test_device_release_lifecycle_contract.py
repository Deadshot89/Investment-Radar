from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ci = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
smoke = (ROOT / 'tests/device_smoke.sh').read_text(encoding='utf-8')
upgrade = (ROOT / 'tests/device_upgrade_smoke.sh').read_text(encoding='utf-8')

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
print('PASS: device tests retry HOME assignment and verify resolver state')

assert 'grep -m1 "versionCode=$expected "' not in upgrade, \
    'version assertion must not short-circuit a pipe under pipefail and trigger SIGPIPE/141'
print('PASS: version assertion consumes full dumpsys output without SIGPIPE under pipefail')

assert 'pm list packages --show-versioncode --user 0 "$PKG"' in upgrade, \
    'installed version verification must use package-manager version listing on modern Android'
assert 'dumpsys package "$PKG"' not in upgrade, \
    'installed version verification must not depend on unstable dumpsys package text formatting'
print('PASS: installed version check uses stable package-manager version listing')
