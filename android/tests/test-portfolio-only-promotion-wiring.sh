#!/usr/bin/env bash
set -euo pipefail

VM="android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
STORE="android/app/src/main/java/de/tobias/investmentradar/CustomInvestmentStore.kt"

# Promoted backend assets must be detected by identity, not only by exact id.
grep -q 'CustomInvestmentStore.promotedTargets' "$VM"
grep -q 'fun promotedTargets' "$STORE"
grep -q 'normalizeIdentity(custom.isin)' "$STORE"
grep -q 'normalizeIdentity(custom.ticker)' "$STORE"

# Position migration is explicitly conflict-safe: only source-present/target-missing
# mappings are allowed to move.
grep -q 'fun safePositionPromotions' "$STORE"
grep -q 'targetId !in existingPositionIds' "$STORE"
grep -q 'source.copy(itemId = targetId)' "$VM"
grep -q 'PortfolioStore.save(application, source.copy(itemId = targetId))' "$VM"
grep -q 'PortfolioStore.remove(application, sourceId)' "$VM"

# Cleanup must not call the destructive explicit-user delete path.
promotion_block=$(awk '/private fun promoteCustomPortfolioAssets/{flag=1} flag{print} /fun markBought/{exit}' "$VM")
if printf '%s\n' "$promotion_block" | grep -q 'removeCustomInvestment'; then
  echo 'Promoted asset cleanup must not delete the portfolio holding through the user delete path'
  exit 1
fi

grep -q 'sourceId == targetId || sourceId !in positionsAfter' "$VM"

# The normal explicit user delete path remains destructive by design.
grep -q 'fun removeCustomInvestment' "$VM"
grep -q 'removeHolding(itemId)' "$VM"

echo 'PASS duplicate custom assets promote by unique identity without overwriting holdings'
