#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CUSTOM_STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"
PORTFOLIO_STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt"
WATCHLIST_STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/WatchlistStore.kt"
CLEANUP="$ROOT/android/app/src/main/java/de/tobias/investmentradar/LegacyPortfolioCleanup.kt"
INSTRUMENTED_TEST="$ROOT/android/app/src/androidTest/java/de/tobias/investmentradar/LegacyIBondCleanupInstrumentedTest.kt"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

grep -Fq 'const val RETIRED_IBOND_ID = "custom-ibonds-dec-2026-usd"' "$CLEANUP" || fail 'Retired iBond ID must remain explicit and exact.'
grep -Fq 'filterNot { it.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID }' "$CUSTOM_STORE" || fail 'Custom asset loader must purge the retired iBond.'
grep -Fq 'if (item.id == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return' "$CUSTOM_STORE" || fail 'Custom asset writes must reject the retired iBond.'
grep -Fq 'filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }' "$PORTFOLIO_STORE" || fail 'Portfolio loader must purge the retired iBond ID.'
grep -Fq '.remove(investedKey(retiredId))' "$PORTFOLIO_STORE" || fail 'Portfolio cleanup must remove retired invested data.'
grep -Fq '.remove(sharesKey(retiredId))' "$PORTFOLIO_STORE" || fail 'Portfolio cleanup must remove retired share data.'
grep -Fq 'if (position.itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return' "$PORTFOLIO_STORE" || fail 'Portfolio writes must reject the retired iBond.'
grep -Fq 'filterNot { it == LegacyPortfolioCleanup.RETIRED_IBOND_ID }' "$WATCHLIST_STORE" || fail 'Watchlist loader must purge the retired iBond.'
grep -Fq 'if (itemId == LegacyPortfolioCleanup.RETIRED_IBOND_ID) return read(context)' "$WATCHLIST_STORE" || fail 'Watchlist writes must reject the retired iBond.'
grep -Fq 'viewModelStartupRemovesRetiredIBondFromPersistedUserData' "$INSTRUMENTED_TEST" || fail 'Instrumented migration regression must remain present.'
grep -Fq 'assertFalse(portfolioPrefs.contains("position.$retiredId.invested"))' "$INSTRUMENTED_TEST" || fail 'Instrumented regression must prove raw invested data is deleted.'
grep -Fq 'assertFalse(watchlistPrefs.getString("item_ids", "").orEmpty().contains(retiredId))' "$INSTRUMENTED_TEST" || fail 'Instrumented regression must prove raw watchlist data is deleted.'

echo 'PASS: retired iBond is purged from persisted custom assets, portfolio and watchlist and cannot be re-added.'
