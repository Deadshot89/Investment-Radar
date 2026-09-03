#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"

# Promoted backend assets must be detected by id.
grep -q 'CustomInvestmentStore.promotedIds' "$VM"
grep -q 'fun promotedIds' "$STORE"

# Cleanup must remove only the stale custom-asset record. It must not use the
# destructive user action that also removes the portfolio holding/watchlist.
grep -q 'promoted.forEach { CustomInvestmentStore.remove(application, it) }' "$VM"

promotion_block=$(awk '/val promoted = CustomInvestmentStore.promotedIds/{flag=1} flag{print} /val customQuotes =/{exit}' "$VM")
if printf '%s\n' "$promotion_block" | grep -q 'removeCustomInvestment'; then
  echo 'Promoted asset cleanup must not delete the portfolio holding'
  exit 1
fi

# The normal explicit user delete path remains destructive by design.
grep -q 'fun removeCustomInvestment' "$VM"
grep -q 'removeHolding(itemId)' "$VM"

echo 'PASS promoted live assets clean stale custom records without deleting holdings'
