#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"

grep -q 'selectedDetailId' "$SRC"
grep -q 'detailReturnTab' "$SRC"
grep -q 'InvestmentDetailScreen(' "$SRC"
grep -q 'onOpenDetail: (String) -> Unit' "$SRC"
grep -q 'Text("Details")' "$SRC"
grep -q 'selectedDetailId = stored.alert.itemId' "$SRC"
grep -q 'customItems.any' "$SRC"
! grep -q 'radarFocusId' "$SRC"

COUNT=$(grep -c 'selectedDetailId = id' "$SRC" || true)
if [ "$COUNT" -lt 2 ]; then
  echo "Expected Radar and Portfolio detail navigation, found $COUNT"
  exit 1
fi

echo "PASS investment detail wiring"
