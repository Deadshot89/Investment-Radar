#!/usr/bin/env bash
set -euo pipefail

# Regression contract: keep the approved history cards dark enough that their text stays readable.
SRC="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
BUILD="android/app/build.gradle.kts"

if ! grep -q 'private const val NeonPanelGlowAlpha = 0.04f' "$SRC"; then
  echo "Expected NeonPanelGlowAlpha to reduce the card glare to 0.04f"
  exit 1
fi

if ! grep -q 'accent.copy(alpha = NeonPanelGlowAlpha)' "$SRC"; then
  echo "Expected NeonPanel to use NeonPanelGlowAlpha in its gradient"
  exit 1
fi

if grep -q 'accent.copy(alpha = 0.10f), RadarSurface.copy(alpha = 0.99f)' "$SRC"; then
  echo "Old 0.10f card glare is still active"
  exit 1
fi

# The glare fix remains present in the current immutable Android release line.
if ! grep -q 'versionCode = 55' "$BUILD" || ! grep -q 'versionName = "2.1.2"' "$BUILD"; then
  echo "Expected the current release line to be Android 2.1.2 (code 55)"
  exit 1
fi

echo "History card glare and release contract OK"
