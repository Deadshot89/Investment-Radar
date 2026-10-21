#!/usr/bin/env bash
set -euo pipefail

MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

! grep -Fq 'Aktueller Live-Kurs:' "$MAIN" || fail 'Trade editor again claims unverified live pricing.'
grep -Fq 'label = "EUR-Kurs (nur Orientierung)"' "$MAIN" || fail 'Trade editor omits non-misleading EUR quote label.'
grep -Fq 'value = euroComparablePrice(item)?.let(::formatMoney) ?: "Nicht verfügbar"' "$MAIN" || fail 'Trade editor must not fabricate a missing quote.'
grep -Fq 'val quoteStatus = PriceLineQuoteStatus.render(' "$MAIN" || fail 'Trade editor ignores quote source/delay classification.'
grep -Fq 'maxLines = 1' "$MAIN" || fail 'Trade editor line wrap protection missing.'
grep -Fq 'Kein automatischer Einstand. Betrag, Kurs und Anteile selbst prüfen.' "$MAIN" || fail 'Trade editor no longer warns against inferred cost basis.'

echo 'PASS: trade editor quotes have transparent provenance, preserve missing states and stay readable.'
