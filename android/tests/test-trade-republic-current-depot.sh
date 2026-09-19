#!/usr/bin/env bash
# Regression contract: production must never fabricate a "current" depot from compiled-in snapshots.
set -euo pipefail

SEED="android/app/src/main/java/de/tobias/investmentradar/UserPortfolioSeed.kt"
VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
POSITION="android/app/src/main/java/de/tobias/investmentradar/PortfolioPosition.kt"
ANALYSIS="android/app/src/main/java/de/tobias/investmentradar/PortfolioAnalysis.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

if grep -Fq 'UserPortfolioSeed.ensureSeeded(app)' "$VM"; then
  fail 'MainViewModel darf kein fest eingebautes Altdepot automatisch einspielen.'
fi

for forbidden in   'ImportedPosition('   'trade_republic_snapshot_'   '1213.11'   '1714.83'   '1675.88'   '1491.89'; do
  if grep -Fq "$forbidden" "$SEED"; then
    fail "Fest eingebauter Depotwert/Alt-Snapshot gefunden: $forbidden"
  fi
done

grep -Fq 'Automatic portfolio seeding is retired' "$SEED" || fail 'Legacy-Seeding muss ausdrücklich stillgelegt sein.'

# Historical snapshots may stay persisted for traceability, but they are not current values.
if grep -Fq 'tracked != null -> snapshotValueEur' "$POSITION"; then
  fail 'Fehlender Livekurs darf nicht auf historischen Snapshotwert zurückfallen.'
fi
if grep -Fq 'snapshotValueEur?.takeIf' "$ANALYSIS"; then
  fail 'Advisor darf historischen Snapshot nicht als aktuellen Portfoliowert verwenden.'
fi
if grep -Fq 'position.investedAmount.takeIf' "$ANALYSIS"; then
  fail 'Einstand darf nicht als aktueller Marktwert verwendet werden.'
fi

echo 'PASS: Keine fest eingebauten Depotbeträge und keine Snapshot-Fallbacks als aktuelle Werte.'
