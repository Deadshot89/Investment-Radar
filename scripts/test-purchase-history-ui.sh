#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MAIN="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
VM="$ROOT/android/app/src/main/java/de/tobias/investmentradar/MainViewModel.kt"
STORE="$ROOT/android/app/src/main/java/de/tobias/investmentradar/PortfolioStore.kt"
GRADLE="$ROOT/android/app/build.gradle.kts"

grep -q 'Kaufhistorie' "$MAIN"
grep -q 'Nachkauf hinzufügen' "$MAIN"
grep -q 'Kaufdatum' "$MAIN"
grep -q 'Kauf aktualisieren' "$MAIN"
grep -q 'fun upsertPurchase' "$VM"
grep -q 'fun removePurchase' "$VM"
grep -q 'purchasesKey' "$STORE"
grep -q 'JSONArray' "$STORE"
grep -q 'versionCode = 25' "$GRADLE"
grep -q 'versionName = "1.1.24"' "$GRADLE"
