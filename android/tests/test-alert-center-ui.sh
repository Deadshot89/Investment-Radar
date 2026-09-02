#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt"
grep -q 'Alle' "$SRC"
grep -q 'Kauf' "$SRC"
grep -q 'Prüfen' "$SRC"
grep -q 'Verkauf' "$SRC"
grep -q 'Alle gelesen' "$SRC"
grep -q 'Alarmeinstellungen' "$SRC"
grep -q 'onDelete' "$SRC"
grep -q 'onClear' "$SRC"
