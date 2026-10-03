#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

APK="$TMP/app-release.apk"
: > "$APK"
FAKE="$TMP/apksigner"
cat > "$FAKE" <<'FAKEEOF'
#!/usr/bin/env bash
set -euo pipefail
echo 'Verifies'
echo 'Verified using v1 scheme (JAR signing): true'
echo 'Verified using v2 scheme (APK Signature Scheme v2): true'
echo 'Signer #1 certificate SHA-256 digest: 00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff'
FAKEEOF
chmod +x "$FAKE"

EXPECTED_SIGNING_CERT_SHA256='00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF' APKSIGNER_BIN="$FAKE" "$ROOT/scripts/verify-release-signer.sh" "$APK" > "$TMP/match.txt"
grep -q 'PASS: Signing certificate SHA-256 matches pinned production fingerprint' "$TMP/match.txt"

set +e
EXPECTED_SIGNING_CERT_SHA256='FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF' APKSIGNER_BIN="$FAKE" "$ROOT/scripts/verify-release-signer.sh" "$APK" > "$TMP/mismatch.out" 2> "$TMP/mismatch.err"
code=$?
set -e
test "$code" -eq 9
grep -q 'Signing certificate SHA-256 mismatch' "$TMP/mismatch.err"

set +e
EXPECTED_SIGNING_CERT_SHA256='not-a-sha256' APKSIGNER_BIN="$FAKE" "$ROOT/scripts/verify-release-signer.sh" "$APK" > "$TMP/malformed.out" 2> "$TMP/malformed.err"
code=$?
set -e
test "$code" -eq 7
grep -q 'exactly 64 hexadecimal characters' "$TMP/malformed.err"

echo 'PASS: release signer guard accepts the pinned signer and rejects mismatched or malformed fingerprints'
