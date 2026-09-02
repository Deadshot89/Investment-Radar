#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
ENGINE="android/app/src/main/java/de/tobias/investmentradar/RecommendationEngine.kt"
grep -q 'RecommendationEngine.plan' "$SRC"
grep -q 'cashAmount' "$SRC"
grep -q 'DIESEN MONAT WARTEN' "$SRC"
grep -q 'currentWeightPct' "$ENGINE"
