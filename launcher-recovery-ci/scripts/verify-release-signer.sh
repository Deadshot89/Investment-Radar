#!/usr/bin/env bash
set -euo pipefail

APK="${1:-}"
if [[ -z "$APK" || ! -f "$APK" ]]; then
  echo "ERROR: APK file is required and must exist." >&2
  exit 2
fi

EXPECTED_RAW="${EXPECTED_SIGNING_CERT_SHA256:-}"
if [[ -z "$EXPECTED_RAW" ]]; then
  echo "ERROR: EXPECTED_SIGNING_CERT_SHA256 is required." >&2
  exit 3
fi

if [[ -n "${APKSIGNER_BIN:-}" ]]; then
  APKSIGNER="$APKSIGNER_BIN"
elif [[ -n "${ANDROID_HOME:-}" ]]; then
  APKSIGNER="$ANDROID_HOME/build-tools/35.0.0/apksigner"
else
  echo "ERROR: APKSIGNER_BIN or ANDROID_HOME is required." >&2
  exit 4
fi

if [[ ! -x "$APKSIGNER" ]]; then
  echo "ERROR: apksigner is not executable: $APKSIGNER" >&2
  exit 5
fi

normalize_fingerprint() {
  printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | tr -d '[:space:]:'
}

SIGNATURE_OUTPUT="$($APKSIGNER verify --verbose --print-certs "$APK")"
printf '%s\n' "$SIGNATURE_OUTPUT"

ACTUAL_RAW="$(printf '%s\n' "$SIGNATURE_OUTPUT" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -n 1)"
if [[ -z "$ACTUAL_RAW" ]]; then
  echo "ERROR: Could not read signer SHA-256 fingerprint from APK." >&2
  exit 6
fi

EXPECTED_SIGNING_CERT_SHA256="$(normalize_fingerprint "$EXPECTED_RAW")"
ACTUAL_SIGNING_CERT_SHA256="$(normalize_fingerprint "$ACTUAL_RAW")"

if [[ ! "$EXPECTED_SIGNING_CERT_SHA256" =~ ^[0-9A-F]{64}$ ]]; then
  echo "ERROR: Expected signing certificate SHA-256 must contain exactly 64 hexadecimal characters." >&2
  exit 7
fi
if [[ ! "$ACTUAL_SIGNING_CERT_SHA256" =~ ^[0-9A-F]{64}$ ]]; then
  echo "ERROR: APK signer SHA-256 is malformed: $ACTUAL_SIGNING_CERT_SHA256" >&2
  exit 8
fi
if [[ "$ACTUAL_SIGNING_CERT_SHA256" != "$EXPECTED_SIGNING_CERT_SHA256" ]]; then
  echo "ERROR: Signing certificate SHA-256 mismatch." >&2
  echo "Expected: $EXPECTED_SIGNING_CERT_SHA256" >&2
  echo "Actual:   $ACTUAL_SIGNING_CERT_SHA256" >&2
  exit 9
fi

echo "PASS: Signing certificate SHA-256 matches pinned production fingerprint: $ACTUAL_SIGNING_CERT_SHA256"
