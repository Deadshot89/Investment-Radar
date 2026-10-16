#!/usr/bin/env bash
set -euo pipefail
# Task 10 GREEN contract: all visible advisor surfaces share the same root plan.
MAIN="android/app/src/main/java/de/tobias/investmentradar/MainActivity.kt"
PORTFOLIO="android/app/src/main/java/de/tobias/investmentradar/PortfolioDashboard.kt"
RADAR="android/app/src/main/java/de/tobias/investmentradar/RadarScreen.kt"
DETAIL="android/app/src/main/java/de/tobias/investmentradar/InvestmentDetailScreen.kt"

for literal in \
  'Was soll ich jetzt tun?' \
  'Cash halten' \
  'Umschichten' \
  'Sparplan prüfen'; do
  grep -q "$literal" "$PORTFOLIO"
done

for literal in 'Neu aufnehmen' 'Verkaufen (Teilverkauf)' 'Verkaufen' 'Keine belastbare Bewertung'; do
  grep -q "$literal" "$RADAR"
done

# Unzuverlässige Daten dürfen auf keiner sichtbaren Advisor-Fläche als HALTEN erscheinen.
for surface in "$PORTFOLIO" "$RADAR" "$DETAIL"; do
  grep -Fq 'PortfolioAdvisorAction.KEINE_BELASTBARE_BEWERTUNG -> "Keine belastbare Bewertung"' "$surface"
  ! grep -Fq 'Halten · Daten prüfen' "$surface"
  ! grep -Fq -- '-> "Reduzieren"' "$surface"
  ! grep -Fq -- '-> "Bewertung prüfen"' "$surface"
done

grep -Fq 'AdvisorSignal.KEINE_BELASTBARE_BEWERTUNG -> "Keine belastbare Bewertung"' "$DETAIL"
grep -q 'Konfidenz' "$DETAIL"
grep -q 'Änderungshistorie' "$DETAIL"
for period in '1 Monat' '3 Monate' '6 Monate' '12 Monate'; do
  grep -q "$period" "$DETAIL"
done
grep -q 'Zielbereich' "$DETAIL"

grep -q 'advisorPlan: PortfolioAdvisorPlan' "$PORTFOLIO"
grep -q 'advisorById: Map<String, PortfolioAdvisorCandidate>' "$RADAR"
grep -q 'advisorCandidate: PortfolioAdvisorCandidate?' "$DETAIL"
grep -q 'advisorHistory: List<AdvisorHistoryEntry>' "$DETAIL"

# Navigation bleibt zentral: kein weiterer BackHandler in Kind-Screens.
! grep -q 'BackHandler' "$PORTFOLIO"
! grep -q 'BackHandler' "$RADAR"
! grep -q 'BackHandler' "$DETAIL"
grep -q 'BackHandler' "$MAIN"

echo "PASS actionable Investment Radar 2.3 advisor UI"
