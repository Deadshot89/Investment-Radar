#!/usr/bin/env bash
set -euo pipefail
VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"

# Live must use the large Radar universe for current BUY candidates instead of relying only on the small dashboard list.
grep -q 'ApiClient.loadRadarPage' "$VM"
grep -q 'recommendation = "BUY"' "$VM"
grep -q 'sort = "SCORE_DESC"' "$VM"
grep -q 'tradeRepublicVerified = true' "$VM"
grep -q 'filter { it.purchaseEligible }' "$VM"
grep -q 'map { it.asInvestmentItem() }' "$VM"

# Radar BUY candidates must lead the merged Live list and top pick, while the dashboard remains a fallback.
grep -q 'topPickId = radarBuyItems.firstOrNull()?.id ?: dashboard.topPickId' "$VM"
grep -q 'items = (radarBuyItems + dashboard.items + customQuotes).distinctBy { it.id }' "$VM"

# A Radar outage must not take the whole Live dashboard down.
grep -q 'runCatching { ApiClient.loadRadarPage' "$VM"
grep -q 'getOrNull()' "$VM"

echo "PASS Live uses verified Radar BUY candidates"
