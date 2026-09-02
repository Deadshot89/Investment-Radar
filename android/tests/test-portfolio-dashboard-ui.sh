#!/usr/bin/env bash
set -euo pipefail
SRC="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
test -f "$SRC"
grep -q 'Depotwert' "$SRC"
grep -q 'Einstand' "$SRC"
grep -q 'Gewinn / Verlust' "$SRC"
grep -q 'Größte Position' "$SRC"
grep -q 'Teilwert' "$SRC"
grep -q 'Kurs fehlt' "$SRC"
grep -q 'onOpenDetail' "$SRC"
