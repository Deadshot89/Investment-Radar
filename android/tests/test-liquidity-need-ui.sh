#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
ENGINE="android/app/src/main/java/de/tobias/investmentradar/LiquidityNeedEngine.kt"
fail(){ echo "FAIL: $1" >&2; exit 1; }
grep -Fq 'ICH BRAUCHE GELD' "$MAIN" || fail 'Geldbedarf-Funktion fehlt.'
grep -Fq 'Freies Cash zuerst verwenden' "$MAIN" || fail 'Cash-zuerst-Schalter fehlt.'
grep -Fq 'LiquidityNeedEngine.plan(' "$MAIN" || fail 'Geldbedarf wird nicht berechnet.'
grep -Fq 'LiquidityNeedEngine.progress(' "$MAIN" || fail 'Bestätigte Geldbedarf-Buchungen werden nicht auf den Restbedarf angerechnet.'
grep -Fq 'onExecuteLiquiditySale: (LiquiditySaleSuggestion, String) -> Unit' "$MAIN" || fail 'Geldbedarf-Verkäufe verlieren ihre Flow-Kennung.'
grep -Fq 'onRecordWithdrawal: (Double, String) -> Boolean' "$MAIN" || fail 'Cash-Auszahlungen verlieren ihre Flow-Kennung.'
grep -Fq 'pendingActionSaleNote = "$flowTag · privater Verkaufserlös"' "$MAIN" || fail 'Privater Verkaufserlös wird nicht eindeutig markiert.'
grep -Fq 'onRecordWithdrawal(plan.cashUsedEur, liquidityFlowTag)' "$MAIN" || fail 'Nur der tatsächlich verwendete Cash-Anteil muss ausgebucht werden.'
grep -Fq 'Geldbedarf vollständig gedeckt.' "$MAIN" || fail 'Abschlussstatus des Geldbedarfs fehlt.'
grep -Fq 'App-Cash entnehmen' "$MAIN" || fail 'Cash-Anteil kann nicht privat entnommen werden.'
grep -Fq 'onExecuteLiquiditySale' "$MAIN" || fail 'Verkaufsvorschlag öffnet keine manuelle Verkaufsmaske.'
grep -Fq 'onExecuteLiquiditySale: (LiquiditySaleSuggestion, String) -> Unit' "$MAIN" || fail 'Verkaufsvorschlag muss Liquiditätsvorschlag und Flow-Kennung übergeben.'
grep -Fq 'onClick = { onExecuteLiquiditySale(suggestion, liquidityFlowTag) }' "$MAIN" || fail 'Verkaufsvorschlag verliert beim Öffnen der Verkaufsmaske Daten oder Flow-Kennung.'
grep -Fq 'pendingActionAmountEur = suggestion.amountEur' "$MAIN" || fail 'Empfohlener Verkaufsbetrag wird nicht vorbefüllt.'
grep -Fq 'pendingActionShares = suggestion.shares' "$MAIN" || fail 'Empfohlene Stückzahl wird nicht vorbefüllt.'
grep -Fq 'append("Geldbedarf: ")' "$MAIN" || fail 'Geldbedarf-Kontext fehlt in der Verkaufsmaske.'
grep -Fq 'PortfolioAdvisorAction.VERKAUFEN -> 1000' "$ENGINE" || fail 'Verkaufssignale werden nicht zuerst verkauft.'
grep -Fq 'PortfolioAdvisorAction.NACHKAUFEN -> 50' "$ENGINE" || fail 'Starke Nachkaufpositionen werden nicht geschützt.'
grep -Fq 'cashUsedEur' "$ENGINE" || fail 'Vorhandenes Cash wird nicht berücksichtigt.'
if grep -Fq 'withdrawalReady' "$MAIN"; then fail 'Alte Auszahlungssperre würde privaten Verkaufserlös erneut als Cash voraussetzen.'; fi
echo "PASS: Geldbedarf trennt App-Cash und private Verkaufserlöse, rechnet bestätigte Deckung an und vermeidet Doppelverkauf."
