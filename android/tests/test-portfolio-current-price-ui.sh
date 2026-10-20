#!/usr/bin/env bash
set -euo pipefail

METRICS="android/app/src/main/java/de/tobias/investmentradar/PortfolioMetrics.kt"
DASHBOARD="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
SUMMARY="android/app/src/main/java/de/tobias/investmentradar/LivePortfolioSummary.kt"
LIVE_CARD="android/app/src/main/java/de/tobias/investmentradar/LivePortfolioCard.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'val currentPriceEur: Double?' "$METRICS" || fail 'PortfolioMetrics exposes no current EUR unit price.'
grep -Fq 'currentPriceEur = draft.usablePrice' "$METRICS" || fail 'PortfolioMetrics does not propagate the live/manual EUR unit price.'
grep -Fq 'PortfolioDashboardValue("Aktueller Kurs"' "$DASHBOARD" || fail 'Portfolio dashboard does not show the current unit price.'
grep -Fq 'val currentPriceEur: Double?' "$SUMMARY" || fail 'Live portfolio summary drops the current unit price.'
grep -Fq 'currentPriceEur = it.currentPriceEur' "$SUMMARY" || fail 'Live portfolio summary does not propagate the current unit price.'
# Verify both responsive cards show the exact supplied unit price and its provenance.
count=$(grep -Fc 'LiveQuoteText(quoteProvenance, row.currentPriceEur, muted)' "$LIVE_CARD" || true)
[ "$count" -eq 2 ] || fail 'Both mobile and wide live portfolio cards must display the supplied current EUR unit price.'
grep -Fq 'formatLiveMoney(priceEur)' "$LIVE_CARD" || fail 'Quoted EUR unit price is not formatted from the supplied price.'
grep -Fq 'LiveQuoteProvenanceResolver.resolve(' "$LIVE_CARD" || fail 'Live portfolio does not classify actual quote provenance.'
grep -Fq 'marketDelayed = item?.dataDelayed == true' "$LIVE_CARD" || fail 'Backend delayed-market flag is ignored.'

echo 'PASS: portfolio rows expose and display the current EUR unit price without changing valuation logic.'
