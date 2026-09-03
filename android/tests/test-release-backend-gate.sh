#!/usr/bin/env bash
# Android release verification: backend gate plus monotonic app versioning for in-app updates.
set -euo pipefail

WF=".github/workflows/android-build.yml"
GRADLE="android/app/build.gradle.kts"

grep -q 'Verify live backend before Android publish' "$WF"
grep -q 'EXPECTED_BACKEND_VERSION: "1.2.0"' "$WF"
grep -Fq 'BASE_URL: ${{ vars.INVESTMENT_API_BASE_URL }}' "$WF"
grep -q "github.ref == 'refs/heads/main'" "$WF"
grep -q '/api/health' "$WF"
grep -q 'backendVersion' "$WF"
grep -q 'Publish APK for in-app updates' "$WF"

# Jede Änderung an der veröffentlichten App braucht eine neue monotone Version.
grep -q 'versionCode = 36' "$GRADLE"
grep -q 'versionName = "1.2.5"' "$GRADLE"

# Ein vorhandenes Tag darf nie still mit anderem App-Code überschrieben werden.
if grep -q -- '--clobber' "$WF"; then
  echo 'Release workflow darf bestehende App-Versionen nicht überschreiben'
  exit 1
fi

# Wiederholungsläufe sind erlaubt, wenn android/app am Release-Tag unverändert ist.
grep -Fq 'git fetch --no-tags origin "refs/tags/$TAG:refs/tags/$TAG"' "$WF"
grep -Fq 'CURRENT_APP_TREE=$(git rev-parse "HEAD:android/app")' "$WF"
grep -Fq 'RELEASE_APP_TREE=$(git rev-parse "$TAG:android/app")' "$WF"
grep -q 'Release $TAG existiert bereits mit anderem App-Code' "$WF"
grep -q 'App-Code ist identisch' "$WF"
grep -q 'Version erhöhen' "$WF"

# Der alte Bytevergleich ist absichtlich verboten: Build-Metadaten können die APK verändern,
# obwohl der App-Code identisch ist.
if grep -q 'sha256sum "$RELEASE_APK"' "$WF"; then
  echo 'Release workflow darf APK-Bytes nicht als App-Code-Identität verwenden'
  exit 1
fi

gate_line=$(grep -n 'Verify live backend before Android publish' "$WF" | head -1 | cut -d: -f1)
publish_line=$(grep -n 'Publish APK for in-app updates' "$WF" | head -1 | cut -d: -f1)
test -n "$gate_line"
test -n "$publish_line"
test "$gate_line" -lt "$publish_line"

echo "PASS Android publish is gated on live backend 1.2.0"
echo "PASS Android app release is monotonic at 1.2.5 / code 36"
echo "PASS existing releases are immutable by android/app tree"
