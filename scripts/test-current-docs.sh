#!/usr/bin/env bash
set -euo pipefail

GRADLE="android/app/build.gradle.kts"
README="README.md"
ANDROID_README="android/README.md"
START="START_HIER.md"
SETUP="SETUP.md"
BACKEND="backend/README.md"
PLAN="docs/superpowers/plans/2026-09-11-investment-budget-ledger.md"
SPEC="docs/superpowers/specs/2026-09-11-investment-budget-ledger-design.md"

fail(){ echo "FAIL: $1" >&2; exit 1; }

VERSION_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)
VERSION_CODE=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -1)
[ -n "$VERSION_NAME" ] || fail 'versionName konnte nicht aus build.gradle.kts gelesen werden.'
[ -n "$VERSION_CODE" ] || fail 'versionCode konnte nicht aus build.gradle.kts gelesen werden.'

grep -Fq "**Aktueller Android-Stand:** $VERSION_NAME / versionCode $VERSION_CODE" "$README" || fail 'README entspricht nicht der Android-Build-Version.'
grep -Fq "**Version:** $VERSION_NAME" "$ANDROID_README" || fail 'android/README versionName ist veraltet.'
grep -Fq "**versionCode:** $VERSION_CODE" "$ANDROID_README" || fail 'android/README versionCode ist veraltet.'
grep -Fq "# START HIER – Investment Radar $VERSION_NAME" "$START" || fail 'START_HIER entspricht nicht der Android-Build-Version.'
grep -Fq "**Android:** $VERSION_NAME / versionCode $VERSION_CODE" "$START" || fail 'START_HIER versionCode ist veraltet.'
grep -Fq "versionName = $VERSION_NAME" "$START" || fail 'START_HIER Release-Abschnitt hat falsche versionName.'
grep -Fq "versionCode = $VERSION_CODE" "$START" || fail 'START_HIER Release-Abschnitt hat falschen versionCode.'
grep -Fq "versionName = $VERSION_NAME" "$SETUP" || fail 'SETUP versionName ist veraltet.'
grep -Fq "versionCode = $VERSION_CODE" "$SETUP" || fail 'SETUP versionCode ist veraltet.'

grep -Fq 'Verkaufserlöse werden privat verwendet und erhöhen **weder App-Cash noch Kaufbudget**.' "$START" || fail 'Aktuelle Verkaufserlös-Regel fehlt in START_HIER.'

for endpoint in \
  'GET /api/health' \
  'GET /api/dashboard' \
  'GET /api/radar' \
  'GET /api/instrument/{id}' \
  'GET /api/custom-quote' \
  'GET /api/market-events' \
  'POST /api/test-push'
do
  grep -Fq "$endpoint" "$BACKEND" || fail "Backend-Doku fehlt Endpunkt: $endpoint"
done

grep -Fq 'Das entspricht **alle 5 Minuten**.' "$BACKEND" || fail 'Backend-Doku beschreibt den marketWatch-Timer nicht als 5-Minuten-Takt.'
grep -Fq 'HISTORISCH / ÜBERHOLT' "$PLAN" || fail 'Alter Budget-Plan ist nicht als historisch markiert.'
grep -Fq 'HISTORISCH / ÜBERHOLT' "$SPEC" || fail 'Alte Budget-Spezifikation ist nicht als historisch markiert.'
grep -Fq 'Verkaufserlöse bleiben privat und erhöhen weder App-Cash noch Kaufbudget.' "$SPEC" || fail 'Alte Budget-Spezifikation verweist nicht auf die aktuelle Verkaufserlös-Regel.'

echo "PASS: Projektdoku folgt dynamisch Android $VERSION_NAME/code$VERSION_CODE; Backend- und historische Cash-Doku sind konsistent."
