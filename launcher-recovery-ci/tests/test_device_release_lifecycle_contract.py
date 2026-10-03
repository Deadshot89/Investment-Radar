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
