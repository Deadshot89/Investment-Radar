#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
GRADLE_FILE="$ROOT_DIR/android/app/build.gradle.kts"
IMPORTER_FILE="$ROOT_DIR/android/app/src/main/java/de/tobias/investmentradar/TradeRepublicScreenshotImporter.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -F 'implementation("com.google.mlkit:text-recognition:16.0.1")' "$GRADLE_FILE" >/dev/null || \
  fail "bundled ML Kit text recognition dependency is missing"

if grep -F 'play-services-mlkit-text-recognition' "$GRADLE_FILE" >/dev/null; then
  fail "unbundled Play Services ML Kit dependency must not be used"
fi

test -f "$IMPORTER_FILE" || fail "TradeRepublicScreenshotImporter.kt is missing"

forbidden_pattern='ApiClient|HttpURLConnection|OkHttpClient|Retrofit|BuildConfig\.API_BASE_URL|getSharedPreferences|SharedPreferences|PortfolioStore|CustomInvestmentStore|uploadScreenshot|uploadImage|uploadBitmap'
if grep -E "$forbidden_pattern" "$IMPORTER_FILE" >/dev/null; then
  fail "screenshot importer contains network, persistence or upload coupling"
fi

grep -F 'TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)' "$IMPORTER_FILE" >/dev/null || \
  fail "local bundled ML Kit recognizer is not wired"

grep -F 'InputImage.fromFilePath' "$IMPORTER_FILE" >/dev/null || \
  fail "selected image URI is not decoded locally through InputImage.fromFilePath"

echo "PASS: screenshot import remains local and uses bundled OCR"
