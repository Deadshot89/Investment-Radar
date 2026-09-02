#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt"

! grep -q 'item.coverage ?: 0' "$SRC"
! grep -q 'score ?: 0' "$SRC"
grep -q 'Nicht verfügbar' "$SRC"

echo "PASS missing score presentation"
