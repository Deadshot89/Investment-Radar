#!/usr/bin/env bash
set -euo pipefail
UI="android/app/src/main/java/de/tobias/investmentradar/LiveBuyCandidatesSection.kt"
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

# Live must expose a dedicated, visible buy-candidate section.
test -f "$UI"
grep -q 'KAUFKANDIDATEN' "$UI"
grep -q 'AKTIEN' "$UI"
grep -q 'ETFs' "$UI"

# Aktien and ETFs are separated and empty states are explicit instead of blank.
grep -q 'type.equals("ETF", ignoreCase = true)' "$UI"
grep -q 'Aktuell erfüllt keine Aktie die Kaufkriterien' "$UI"
grep -q 'Aktuell erfüllt kein ETF die Kaufkriterien' "$UI"

# Each candidate must show the requested decision data and actions.
grep -q 'Score' "$UI"
grep -q 'LiveForecastSummary(item, compact = true)' "$UI"
grep -q 'IM DEPOT' "$UI"
grep -q 'Details' "$UI"
grep -q 'Trade Republic' "$UI"
grep -q 'TradeRepublicNavigator.open(context, item)' "$UI"
grep -q 'onOpenDetail(item.id)' "$UI"

# Dashboard wiring must pass current BUY candidates and existing holdings into the section.
grep -q 'LiveBuyCandidatesSection(' "$MAIN"
grep -q 'buyCandidates = buyCandidates' "$MAIN"
grep -q 'holdingIds = holdingIds' "$MAIN"
grep -q 'onOpenDetail = onOpenDetail' "$MAIN"

echo "PASS Live buy candidates UI"
