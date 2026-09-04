#!/usr/bin/env bash
# Investment Radar 2.1 release verification: backend version, 2000-item radar and monotonic Android update.
set -euo pipefail

WF=".github/workflows/android-build.yml"
GRADLE="android/app/build.gradle.kts"
HEALTH="backend/src/functions/health.mjs"
BACKEND="backend/package.json"

grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "2.1.0"' "$WF"
grep -Fq 'BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}' "$WF"
grep -q "github.ref == 'refs/heads/main'" "$WF"
grep -q '/api/health' "$WF"
grep -q '/api/radar' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'universeTotal' "$WF"
grep -q '2000' "$WF"
grep -q 'Publish APK for in-app updates' "$WF"

grep -q 'versionCode = 53' "$GRADLE"
grep -q 'versionName = "2.1.0"' "$GRADLE"
grep -q 'Investment Radar 2.1.0' "$GRADLE"
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

gate_line=$(grep -n 'Verify live backend before Android publish' "$WF" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'Publish APK for in-app updates' "$WF" | head -1 | cut -d: -f1)
test -n "$gate_line"
test -n "$publish_line"
test "$gate_line" -lt "$publish_line"

echo "PASS Android publish is gated on live backend 2.1.0 and >=2000 radar instruments"
echo "PASS Android app release is monotonic at 2.1.0 / code 53"
echo "PASS existing releases are immutable by android/app tree"
