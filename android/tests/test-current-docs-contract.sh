#!/usr/bin/env bash
set -euo pipefail

START="START_HIER.md"
SETUP="SETUP.md"
BACKEND="backend/README.md"
PLAN="docs/superpowers/plans/2026-09-11-investment-budget-ledger.md"
SPEC="docs/superpowers/specs/2026-09-11-investment-budget-ledger-design.md"

fail(){ echo "FAIL: $1" >&2; exit 1; }

grep -Fq '# START HIER – Investment Radar 2.5.20' "$START" || fail 'START_HIER ist nicht auf 2.5.20.'
grep -Fq '**Android:** 2.5.20 / versionCode 90' "$START" || fail 'START_HIER enthält nicht Android 2.5.20/code90.'
grep -Fq 'Verkaufserlöse werden privat verwendet und erhöhen **weder App-Cash noch Kaufbudget**.' "$START" || fail 'Aktuelle Verkaufserlös-Regel fehlt in START_HIER.'
grep -Fq 'versionName = 2.5.20' "$SETUP" || fail 'SETUP ist nicht auf 2.5.20.'

for endpoint in   'GET /api/health'   'GET /api/dashboard'   'GET /api/radar'   'GET /api/instrument/{id}'   'GET /api/custom-quote'   'GET /api/market-events'   'POST /api/test-push'
do
  grep -Fq "$endpoint" "$BACKEND" || fail "Backend-Doku fehlt Endpunkt: $endpoint"
done

grep -Fq 'also **alle 5 Minuten**' "$BACKEND" || fail 'Backend-Doku beschreibt den marketWatch-Timer nicht als 5-Minuten-Takt.'
grep -Fq 'Status 2026-09-20: HISTORISCH / ÜBERHOLT.' "$PLAN" || fail 'Alter Budget-Plan ist nicht als historisch markiert.'
grep -Fq 'Status 2026-09-20: HISTORISCH / ÜBERHOLT.' "$SPEC" || fail 'Alte Budget-Spezifikation ist nicht als historisch markiert.'
grep -Fq 'Verkaufserlöse bleiben privat und erhöhen weder App-Cash noch Kaufbudget.' "$SPEC" || fail 'Alte Budget-Spezifikation verweist nicht auf die aktuelle Verkaufserlös-Regel.'

echo 'PASS: aktuelle Dokumentation ist auf 2.5.20, Backend-Routen/Timer stimmen und alte Cash-Logik ist klar als historisch markiert.'
