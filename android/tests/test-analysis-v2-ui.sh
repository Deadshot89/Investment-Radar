#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
MODELS="android/app/src/main/java/de/tobias/investmentradar/Models.kt"
CARD="android/app/src/main/java/de/tobias/investmentradar/ScoreBreakdownCard.kt"
grep -q 'scoreTotal' "$MODELS"
grep -q 'RecommendationEngine' "$SRC"
grep -q 'Datenabdeckung' "$CARD"
grep -q 'Qualität' "$CARD"
grep -q 'Bewertung' "$CARD"
grep -q 'Wachstum' "$CARD"
grep -q 'Momentum' "$CARD"
