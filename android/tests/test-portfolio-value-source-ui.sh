#!/usr/bin/env bash
set -euo pipefail

UI="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
LIVE="android/app/src/main/java/de/tobias/investmentradar/LivePortfolioCard.kt"

grep -q 'Historischer Import · kein aktueller Wert' "$UI"
grep -q 'Live-Kurs × Stückzahl' "$UI"
grep -q 'Live-Tracking aktiv · Einstand importiert' "$UI"
grep -q 'Historischer Depotwert gespeichert · Einstand importiert' "$UI"
grep -q 'Nicht vollständig berechenbar' "$UI"
grep -q 'Nicht vollständig berechenbar' "$LIVE"
grep -q 'Position(en) ohne belastbaren aktuellen Wert' "$LIVE"

echo 'PASS current portfolio values are explicitly live-derived or marked incomplete'
