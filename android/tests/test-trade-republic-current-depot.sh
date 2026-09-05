#!/usr/bin/env bash
set -euo pipefail
FILE="android/app/src/main/java/de/tobias/investmentradar/UserPortfolioSeed.kt"
for expected in \
  'trade_republic_snapshot_2026_09_05_v4_full' \
  '"meta" to ImportedPosition(1213.11, 2.291921, 519.04)' \
  '"custom-nel-asa" to ImportedPosition(113.45, 582.392344, 0.226)' \
  '"spyi" to ImportedPosition(50.25, 4.339524, 11.75)' \
  '"custom-samsung-gdr" to ImportedPosition(45.45, 0.010822, 4029.32)' \
  '"msft" to ImportedPosition(31.31, 0.072857, 395.68)' \
  '"is3s" to ImportedPosition(20.30, 0.284292, 73.87)' \
  '"googl" to ImportedPosition(15.00, 0.051449, 310.99)' \
  '"custom-ibonds-dec-2026-usd" to ImportedPosition(3.02, 0.688382, 5.81)'; do
  grep -Fq "$expected" "$FILE" || { echo "Missing current depot data: $expected"; exit 1; }
done
grep -Fq 'trackedShares = imported.shares' "$FILE" || { echo 'Imported shares are not persisted'; exit 1; }
grep -Fq 'snapshotCostBasisEur = imported.shares * imported.buyIn' "$FILE" || { echo 'Imported cost basis is not derived from shares and buy-in'; exit 1; }
grep -Fq 'name = "iBonds Dec 2026 USD (Dist)"' "$FILE" || { echo 'iBonds asset missing'; exit 1; }
echo 'Current Trade Republic depot snapshot contract passed.'
