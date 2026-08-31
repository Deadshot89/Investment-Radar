#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() { echo "FAIL: $1" >&2; exit 1; }

grep -q 'https://app.traderepublic.com/instrument/\$isin?timeframe=1d' "$MAIN" || fail "Trade Republic ISIN URL missing"
grep -q 'setPackage("de.traderepublic.app")' "$MAIN" || fail "explicit Trade Republic package missing"
if grep -q 'resolveActivity(context.packageManager)' "$MAIN"; then
  fail "resolveActivity package-visibility precheck must not be used"
fi
grep -q 'try {' "$MAIN" || fail "direct app launch must be protected by try/catch"
grep -q 'context.startActivity(tradeRepublicIntent)' "$MAIN" || fail "direct Trade Republic launch missing"
grep -Eq 'catch \(([^)]*ActivityNotFoundException[^)]*)\)' "$MAIN" || fail "ActivityNotFoundException fallback missing"
grep -q 'context.startActivity(Intent(Intent.ACTION_VIEW, uri))' "$MAIN" || fail "browser fallback missing"
if grep -q 'google.com/search' "$MAIN"; then
  fail "Google fallback must not return"
fi

echo "PASS: direct Trade Republic launch with browser fallback"
