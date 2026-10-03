from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

required = [
    'signingConfigs',
    'create("release")',
    'getByName("release")',
    'isDebuggable = false',
    'LAUNCHER_KEYSTORE_PATH',
    'LAUNCHER_KEYSTORE_PASSWORD',
    'LAUNCHER_KEY_ALIAS',
    'LAUNCHER_KEY_PASSWORD',
    'verifyReleaseSigningInputs',
    'preReleaseBuild',
]
for token in required:
    assert token in build, f"missing release signing contract: {token}"

assert 'debug.keystore' not in build
assert 'androiddebugkey' not in build

print('PASS: release build requires explicit stable signing inputs and is non-debuggable')
