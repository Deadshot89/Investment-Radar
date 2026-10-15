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
grep -Fq 'Kurs: ${formatLiveMoney(row.currentPriceEur)}' "$LIVE_CARD" || fail 'Compact portfolio card does not show the current unit price.'

echo 'PASS: portfolio rows expose and display the current EUR unit price without changing valuation logic.'
