#!/usr/bin/env bash
set -euo pipefail

START="START_HIER.md"
SETUP="SETUP.md"
BACKEND="backend/README.md"
PLAN="docs/superpowers/plans/2026-09-11-investment-budget-ledger.md"
SPEC="docs/superpowers/specs/2026-09-11-investment-budget-ledger-design.md"

fail(){ echo "FAIL: $1" >&2; exit 1; }

grep -Fq '# START HIER – Investment Radar 2.5.21' "$START" || fail 'START_HIER ist nicht auf 2.5.21.'
grep -Fq '**Android:** 2.5.21 / versionCode 91' "$START" || fail 'START_HIER enthält nicht Android 2.5.21/code91.'
grep -Fq 'Verkaufserlöse werden privat verwendet und erhöhen **weder App-Cash noch Kaufbudget**.' "$START" || fail 'Aktuelle Verkaufserlös-Regel fehlt in START_HIER.'
grep -Fq '`versionName = 2.5.21`' "$SETUP" || fail 'SETUP ist nicht auf 2.5.21.'

for endpoint in   'GET /api/health'   'GET /api/dashboard'   'GET /api/radar'   'GET /api/instrument/{id}'   'GET /api/custom-quote'   'GET /api/market-events'   'POST /api/test-push'
do
  grep -Fq "$endpoint" "$BACKEND" || fail "Backend-Doku fehlt Endpunkt: $endpoint"
done

grep -Fq 'Das entspricht **alle 5 Minuten**.' "$BACKEND" || fail 'Backend-Doku beschreibt den marketWatch-Timer nicht als 5-Minuten-Takt.'
grep -Fq 'HISTORISCH / ÜBERHOLT' "$PLAN" || fail 'Alter Budget-Plan ist nicht als historisch markiert.'
grep -Fq 'HISTORISCH / ÜBERHOLT' "$SPEC" || fail 'Alte Budget-Spezifikation ist nicht als historisch markiert.'
grep -Fq 'Verkaufserlöse bleiben privat und erhöhen weder App-Cash noch Kaufbudget.' "$SPEC" || fail 'Alte Budget-Spezifikation verweist nicht auf die aktuelle Verkaufserlös-Regel.'

echo 'PASS: aktuelle Projektdoku, Backend-Doku und historische Cash-Regeln sind konsistent.'
