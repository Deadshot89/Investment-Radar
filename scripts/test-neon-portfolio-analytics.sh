#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
GRADLE="$ROOT/android/app/build.gradle.kts"

grep -q 'PORTFOLIO ANALYSE' "$MAIN"
grep -q 'Gewichtung' "$MAIN"
grep -q 'Transaktionen gesamt' "$MAIN"
grep -q 'Bester Wert' "$MAIN"
grep -q 'Schwächster Wert' "$MAIN"
grep -q 'NeonStatStrip' "$MAIN"
grep -q 'RadarPink' "$MAIN"
grep -q 'versionCode = 25' "$GRADLE"
grep -q 'versionName = "1.1.24"' "$GRADLE"

echo "PASS: Neon portfolio analytics 1.1.24"
