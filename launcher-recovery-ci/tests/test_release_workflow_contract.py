from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
workflow = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
guard = (ROOT / "scripts/verify-release-signer.sh").read_text(encoding="utf-8")

workflow_required = [
    "version_code:",
    "version_name:",
    "required: true",
    '-PAPP_VERSION_CODE="${{ inputs.version_code }}"',
    '-PAPP_VERSION_NAME="${{ inputs.version_name }}"',
    "LAUNCHER_KEYSTORE_BASE64",
    "LAUNCHER_KEYSTORE_PASSWORD",
    "LAUNCHER_KEY_ALIAS",
    "LAUNCHER_KEY_PASSWORD",
    "LAUNCHER_SIGNING_CERT_SHA256",
    "EXPECTED_SIGNING_CERT_SHA256",
    "scripts/verify-release-signer.sh",
    "GITHUB_REF",
    "refs/heads/main",
    "test_launcher_contract.py",
    "test_release_signing_contract.py",
    "test_signer_fingerprint_guard.sh",
    "runs-on: macos-15-intel",
    "api-level: 36",
    "emulator-boot-timeout: 900",
    "bash tests/device_smoke.sh app/build/outputs/apk/release/app-release.apk",
]
for token in workflow_required:
    assert token in workflow, f"missing release workflow contract: {token}"

guard_required = [
    "ACTUAL_SIGNING_CERT_SHA256",
    "EXPECTED_SIGNING_CERT_SHA256",
    "Signer #1 certificate SHA-256 digest:",
    "Signing certificate SHA-256 mismatch",
    "^[0-9A-F]{64}$",
]
for token in guard_required:
    assert token in guard, f"missing signer guard contract: {token}"

main_gate = workflow.index("GITHUB_REF")
key_restore = workflow.index("Restore production signing key")
contract_gate = workflow.index("test_launcher_contract.py")
verify_gate = workflow.index("- name: Verify release")
device_gate = workflow.index("bash tests/device_smoke.sh app/build/outputs/apk/release/app-release.apk")
upload_gate = workflow.index("uses: actions/upload-artifact@v4")

assert main_gate < key_restore, "canonical main branch must be verified before restoring production key"
assert contract_gate < key_restore, "release contract tests must pass before restoring production key"
assert verify_gate < device_gate, "signed APK must be verified before device lifecycle testing"
assert device_gate < upload_gate, "production APK must pass API 36 lifecycle before release artifact upload"

print("PASS: release workflow requires explicit versions, production signing secrets, pinned signer fingerprint, bounded API 36 boot resilience, and device lifecycle before artifact upload")
