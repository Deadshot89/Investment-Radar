#!/usr/bin/env bash
set -euo pipefail
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
PORTFOLIO="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"

grep -q 'selectedDetailId' "$MAIN"
grep -q 'detailReturnTab' "$MAIN"
grep -q 'InvestmentDetailScreen(' "$MAIN"
grep -q 'onOpenDetail: (String) -> Unit' "$RADAR"
grep -q 'onOpenDetail: (String) -> Unit' "$PORTFOLIO"
grep -q 'Text("Details")' "$PORTFOLIO"
grep -q 'selectedDetailId = stored.alert.itemId' "$MAIN"
grep -q 'customItems.any' "$MAIN"
! grep -q 'radarFocusId' "$MAIN"

COUNT=$(grep -c 'selectedDetailId = id' "$MAIN" || true)
if [ "$COUNT" -lt 2 ]; then
  echo "Expected Radar and Portfolio detail navigation, found $COUNT"
  exit 1
fi

echo "PASS investment detail wiring"
