#!/usr/bin/env bash
set -euo pipefail

HELPER="scripts/assert-android-app-production-unchanged.sh"
BUILD_WF=".github/workflows/android-build.yml"
CONTRACT_WF=".github/workflows/android-contract-tests.yml"
fail(){ echo "FAIL: $1" >&2; exit 1; }

[ -f "$HELPER" ] || fail 'Produktionscode-Guard fehlt; test-only Änderungen können noch nicht von App-Code getrennt werden.'
grep -Fq -- "- 'scripts/assert-android-app-production-unchanged.sh'" "$BUILD_WF" || fail 'Änderungen am Guard müssen den APK-Build auslösen.'
grep -Fq -- "- 'scripts/assert-android-app-production-unchanged.sh'" "$CONTRACT_WF" || fail 'Änderungen am Guard müssen die Android-Contracts auslösen.'
grep -Fq -- "- '.github/workflows/android-build.yml'" "$CONTRACT_WF" || fail 'Änderungen am Publish-Workflow müssen die Android-Contracts auslösen.'

root="$(pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

cd "$tmp"
git init -q
git config user.email "ci@example.invalid"
git config user.name "CI"
mkdir -p android/app/src/main/java/example android/app/src/test/java/example android/app/src/androidTest/java/example scripts
cp "$root/$HELPER" scripts/assert-android-app-production-unchanged.sh
printf '%s\n' 'class Main' > android/app/src/main/java/example/Main.kt
printf '%s\n' 'class UnitTest' > android/app/src/test/java/example/MainTest.kt
printf '%s\n' 'class UiTest' > android/app/src/androidTest/java/example/MainUiTest.kt
printf '%s\n' 'plugins {}' > android/app/build.gradle.kts
git add .
git commit -qm base
git tag v-base

printf '%s\n' 'class UnitTestChanged' > android/app/src/test/java/example/MainTest.kt
printf '%s\n' 'class UiTestChanged' > android/app/src/androidTest/java/example/MainUiTest.kt
git add .
git commit -qm tests-only
bash scripts/assert-android-app-production-unchanged.sh v-base HEAD || fail 'Nur Testcode geändert: gleiche App-Version muss erlaubt bleiben.'

printf '%s\n' 'class MainChanged' > android/app/src/main/java/example/Main.kt
git add .
git commit -qm production-code
if bash scripts/assert-android-app-production-unchanged.sh v-base HEAD; then
  fail 'Produktionscode geändert: gleiche App-Version darf nicht erlaubt werden.'
fi

printf '%s\n' 'plugins { id("changed") }' > android/app/build.gradle.kts
git add .
git commit -qm production-config
if bash scripts/assert-android-app-production-unchanged.sh v-base HEAD; then
  fail 'App-Build-Konfiguration geändert: gleiche App-Version darf nicht erlaubt werden.'
fi

echo 'PASS: Release-Guard ignoriert nur Testcode, blockiert echten App-Code und bleibt in den CI-Gates.'
