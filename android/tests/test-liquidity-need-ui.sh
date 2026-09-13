#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
ENGINE="android/app/src/main/java/de/tobias/investmentradar/LiquidityNeedEngine.kt"
fail(){ echo "FAIL: $1" >&2; exit 1; }
grep -Fq 'ICH BRAUCHE GELD' "$MAIN" || fail 'Geldbedarf-Funktion fehlt.'
grep -Fq 'Freies Cash zuerst verwenden' "$MAIN" || fail 'Cash-zuerst-Schalter fehlt.'
grep -Fq 'LiquidityNeedEngine.plan(' "$MAIN" || fail 'Geldbedarf wird nicht berechnet.'
grep -Fq 'Auszahlung verbuchen' "$MAIN" || fail 'Auszahlung kann nicht gebucht werden.'
grep -Fq 'onExecuteLiquiditySale' "$MAIN" || fail 'Verkaufsvorschlag öffnet keine manuelle Verkaufsmaske.'
grep -Fq 'PortfolioAdvisorAction.VERKAUFEN -> 1000' "$ENGINE" || fail 'Verkaufssignale werden nicht zuerst verkauft.'
grep -Fq 'PortfolioAdvisorAction.NACHKAUFEN -> 50' "$ENGINE" || fail 'Starke Nachkaufpositionen werden nicht geschützt.'
grep -Fq 'cashUsedEur' "$ENGINE" || fail 'Vorhandenes Cash wird nicht berücksichtigt.'
echo "PASS: Geldbedarf plant Cash, priorisierte Verkäufe und Auszahlung ohne Auto-Trade."
