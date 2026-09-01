#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() { echo "FAIL: $1" >&2; exit 1; }

grep -q 'ClipData.newPlainText("Trade Republic ISIN", item.isin.trim())' "$MAIN" || fail "ISIN is not copied to clipboard"
grep -q 'getSystemService(android.content.Context.CLIPBOARD_SERVICE)' "$MAIN" || fail "Clipboard service missing"
grep -q 'Intent(Intent.ACTION_MAIN)' "$MAIN" || fail "Trade Republic launcher intent missing"
grep -q 'addCategory(Intent.CATEGORY_LAUNCHER)' "$MAIN" || fail "Launcher category missing"
grep -q 'setPackage("de.traderepublic.app")' "$MAIN" || fail "Trade Republic package missing"
grep -q 'context.startActivity(tradeRepublicIntent)' "$MAIN" || fail "Trade Republic app is not started"
grep -q 'ISIN kopiert' "$MAIN" || fail "User hint about copied ISIN missing"
if grep -q 'app.traderepublic.com/instrument' "$MAIN"; then fail "Instrument web/deep link must not be used"; fi
if grep -q 'context.startActivity(Intent(Intent.ACTION_VIEW, uri))' "$MAIN"; then fail "Browser fallback must be removed"; fi
if grep -q 'google.com/search' "$MAIN"; then fail "Google fallback must not exist"; fi

echo "PASS: Trade Republic app launcher + clipboard"
