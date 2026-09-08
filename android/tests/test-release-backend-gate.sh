#!/usr/bin/env bash
# Investment Radar 2.4.2 release verification: backend compatibility, 2000-item radar and monotonic Android update.
# This contract is also the trigger used to re-verify candidate release workflow changes end-to-end.
set -euo pipefail

WF=".github/workflows/android-build.yml"
GRADLE="android/app/build.gradle.kts"
HEALTH="backend/src/functions/health.mjs"
BACKEND="backend/package.json"

grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "2.1.0"' "$WF"
grep -Fq 'BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}' "$WF"
grep -Fq "github.ref == 'refs/heads/main' || github.ref == 'refs/heads/feature/investment-radar-2.4'" "$WF"
grep -q '/api/health' "$WF"
grep -q '/api/radar' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'universeTotal' "$WF"
grep -q '2000' "$WF"
grep -q 'Publish APK for in-app updates' "$WF"

# Publishing remains production-only even though the live-backend gate can validate the 2.4 candidate branch.
publish_line=$(grep -n 'Publish APK for in-app updates' "$WF" | head -1 | cut -d: -f1)
publish_if=$(sed -n "$((publish_line + 1))p" "$WF")
echo "$publish_if" | grep -Fq "github.ref == 'refs/heads/main'"
if echo "$publish_if" | grep -q 'feature/investment-radar-2.4'; then
  echo 'Feature-Branch darf In-App-Updates nicht veröffentlichen'
  exit 1
fi

# The live backend contract remains 2.1.0; Android 2.4.2/code62 is the new update candidate.
grep -q 'versionCode = 62' "$GRADLE"
grep -q 'versionName = "2.4.2"' "$GRADLE"
grep -q 'Investment Radar 2.4.2' "$GRADLE"
grep -q '"version": "2.1.0"' "$BACKEND"
grep -q 'backendVersion: "2.1.0"' "$HEALTH"
grep -q 'universeTarget: 2000' "$HEALTH"

if grep -q -- '--clobber' "$WF"; then
  echo 'Release workflow darf bestehende App-Versionen nicht überschreiben'
  exit 1
fi

grep -Fq 'git fetch --no-tags origin "refs/tags/$TAG:refs/tags/$TAG"' "$WF"
grep -Fq 'CURRENT_APP_TREE=$(git rev-parse "HEAD:android/app")' "$WF"
grep -Fq 'RELEASE_APP_TREE=$(git rev-parse "$TAG:android/app")' "$WF"
grep -q 'Release $TAG existiert bereits mit anderem App-Code' "$WF"
grep -q 'App-Code ist identisch' "$WF"
grep -q 'Version erhöhen' "$WF"

if grep -q 'sha256sum "$RELEASE_APK"' "$WF"; then
  echo 'Release workflow darf APK-Bytes nicht als App-Code-Identität verwenden'
  exit 1
fi

# Release notes must follow the current Android version instead of carrying stale 2.1 copy forever.
if grep -Fq -- '--notes "Investment Radar 2.1:' "$WF"; then
  echo 'Release-Notizen dürfen nicht mehr auf Investment Radar 2.1 fest verdrahtet sein'
  exit 1
fi
grep -Fq 'RELEASE_NOTES=' "$WF" || { echo 'Dynamische RELEASE_NOTES fehlen'; exit 1; }
grep -Fq 'Investment Radar $VERSION_NAME' "$WF" || { echo 'Release-Notizen müssen VERSION_NAME verwenden'; exit 1; }
grep -Fq -- '--notes "$RELEASE_NOTES"' "$WF" || { echo 'gh release create muss die dynamischen RELEASE_NOTES verwenden'; exit 1; }

gate_line=$(grep -n 'Verify live backend before Android publish' "$WF" | head -1 | cut -d: -f1)
test -n "$gate_line"
test -n "$publish_line"
test "$gate_line" -lt "$publish_line"

echo "PASS Android candidate and main both validate live backend 2.1.0 and >=2000 radar instruments"
echo "PASS Android in-app publishing remains restricted to main"
echo "PASS Android app release is monotonic at 2.4.2 / code 62"
echo "PASS existing releases are immutable by android/app tree"
echo "PASS Android release notes follow VERSION_NAME instead of stale 2.1 copy"
