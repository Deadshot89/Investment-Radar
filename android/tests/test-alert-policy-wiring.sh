#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/InvestmentMessagingService.kt"
grep -q 'AlertPreferencesStore.read' "$SRC"
grep -q 'AlertPolicy.shouldStore' "$SRC"
grep -q 'AlertPolicy.shouldNotify' "$SRC"
