#!/usr/bin/env bash
# Current Android release verification: backend compatibility, 2000-item radar and monotonic Android update.
# This contract is also the trigger used to re-verify candidate release workflow changes end-to-end.
set -euo pipefail

WF=".github/workflows/android-build.yml"
GRADLE="android/app/build.gradle.kts"
HEALTH="backend/src/functions/health.mjs"
BACKEND="backend/package.json"
HELPER="scripts/assert-android-app-production-unchanged.sh"

VERSION_NAME=$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE" | head -1)
VERSION_CODE=$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE" | head -1)
test -n "$VERSION_NAME"
test -n "$VERSION_CODE"
test "$VERSION_CODE" -ge 98
grep -Fq "Release candidate: Investment Radar $VERSION_NAME" "$GRADLE"

grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "2.1.0"' "$WF"
grep -Fq 'BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}' "$WF"
grep -Fq "github.ref == 'refs/heads/main' || github.ref == 'refs/heads/feature/investment-radar-2.4'" "$WF"
grep -q '/api/health' "$WF"
grep -q '/api/radar' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'apiSchemaVersion' "$WF"
grep -q 'sourceRevision' "$WF"
grep -q 'deployId' "$WF"
grep -q 'EXPECTED_API_SCHEMA: "2026-09-14.1"' "$WF"
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

# The live backend contract remains 2.1.0; Android release metadata comes from Gradle.
grep -q '"version": "2.1.0"' "$BACKEND"
grep -q 'backendVersion: "2.1.0"' "$HEALTH"
grep -q 'sourceRevision: buildInfo.sourceRevision' "$HEALTH"
grep -q 'deployId: buildInfo.deployId' "$HEALTH"
grep -q 'apiSchemaVersion: buildInfo.apiSchemaVersion' "$HEALTH"
grep -q 'universeTarget: 2000' "$HEALTH"

if grep -q -- '--clobber' "$WF"; then
  echo 'Release workflow darf bestehende App-Versionen nicht überschreiben'
  exit 1
fi

test -f "$HELPER" || { echo 'Produktionscode-Guard fehlt'; exit 1; }
grep -Fq 'git fetch --no-tags origin "refs/tags/$TAG:refs/tags/$TAG"' "$WF"
grep -Fq 'bash scripts/assert-android-app-production-unchanged.sh "$TAG" HEAD' "$WF" || {
  echo 'Release workflow muss bestehende Versionen gegen echten Produktionscode prüfen'
  exit 1
}
if grep -Fq 'CURRENT_APP_TREE=$(git rev-parse "HEAD:android/app")' "$WF"; then
  echo 'Release workflow darf Testcode nicht mehr über den gesamten android/app Tree als App-Code behandeln'
  exit 1
fi
grep -q 'Release $TAG existiert bereits mit anderem App-Code' "$WF"
grep -q 'Produktiver App-Code ist identisch' "$WF"
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

echo "PASS Android candidate and main validate backend 2.1.0, schema 2026-09-14.1, real deploy identity and >=2000 radar instruments"
echo "PASS Android in-app publishing remains restricted to main"
echo "PASS Android app release is monotonic at $VERSION_NAME / code $VERSION_CODE"
echo "PASS existing releases are immutable by production Android app code while tests may evolve"
echo "PASS Android release notes follow VERSION_NAME instead of stale 2.1 copy"
