#!/usr/bin/env bash
set -euo pipefail
UI="android/app/src/main/java/de/tobias/investmentradar/AlertsScreen.kt"
STATE="android/app/src/main/java/de/tobias/investmentradar/AlertCenterState.kt"

require_literal() {
  local needle="$1"
  local file="$2"
  local label="$3"
  if ! grep -Fq "$needle" "$file"; then
    echo "FAIL: $label fehlt: $needle" >&2
    exit 1
  fi
}

require_literal 'ALL("Alle")' "$STATE" 'Filter Alle'
require_literal 'BUY("Kauf")' "$STATE" 'Filter Kauf'
require_literal 'REVIEW("Prüfen")' "$STATE" 'Filter Prüfen'
require_literal 'SELL("Verkauf")' "$STATE" 'Filter Verkauf'
require_literal 'level.equals("THRESHOLD"' "$STATE" 'Threshold-Filter'
require_literal 'fun visible(' "$STATE" 'Sichtbarkeitslogik'
require_literal 'AlertCenterState.visible(' "$UI" 'State-Wiring'
require_literal 'Text("Nur Depot")' "$UI" 'Depotfilter'
require_literal 'Keine passenden Alarme für dein Depot.' "$UI" 'Depot-Leerzustand'
require_literal 'Alle gelesen' "$UI" 'Gelesen-Aktion'
require_literal 'Alarmeinstellungen' "$UI" 'Alarmeinstellungen'
require_literal 'onDelete' "$UI" 'Löschen-Wiring'
require_literal 'onClear' "$UI" 'Leeren-Wiring'
require_literal 'private fun alertBadgeLabel' "$UI" 'Alarm-Badge'
require_literal '"PROGNOSE"' "$UI" 'Prognose-Badge'
require_literal 'private fun alertAccentColor' "$UI" 'Alarmfarbe'
require_literal 'private fun formatAlertTimestamp' "$UI" 'Zeitformatierung'
require_literal 'HorizontalDivider' "$UI" 'Kartentrenner'
require_literal 'Text("Warum der Radar reagiert"' "$UI" 'Begründungsabschnitt'
require_literal 'Text("NEU"' "$UI" 'Neu-Kennzeichnung'
require_literal 'contentDescription = "Alarm löschen"' "$UI" 'Löschen-Icon'

# Prognose-Alarme werden vollständig auf Deutsch strukturiert dargestellt.
require_literal 'private fun ForecastAlertSummary' "$UI" 'Prognose-Zusammenfassung'
require_literal '"PROGNOSE · 12 MONATE"' "$UI" 'Prognose-Zeitraum'
require_literal 'Text("Vorher:' "$UI" 'Vorher-Wert'
require_literal '"Pessimistisch"' "$UI" 'Pessimistisches Szenario'
require_literal '"Erwartet"' "$UI" 'Erwartetes Szenario'
require_literal '"Optimistisch"' "$UI" 'Optimistisches Szenario'
require_literal '"AUFWÄRTS"' "$UI" 'Aufwärtsrichtung'
require_literal '"ABWÄRTS"' "$UI" 'Abwärtsrichtung'
require_literal '"SEITWÄRTS"' "$UI" 'Seitwärtsrichtung'

# Aktionscenter: jeder Alarm zeigt Handlungsstatus und konkrete nächste Aktion.
require_literal 'Text("Was jetzt tun?"' "$UI" 'Aktionsüberschrift'
require_literal 'private fun alertActionGuidance' "$UI" 'Aktionslogik'
require_literal '"JETZT HANDELN"' "$UI" 'Sofort-Handlungsstatus'
require_literal '"BEOBACHTEN"' "$UI" 'Beobachten-Status'
require_literal '"DATEN PRÜFEN"' "$UI" 'Datenstatus'
require_literal '"Verkauf jetzt prüfen"' "$UI" 'Verkaufsaktion'
require_literal '"Position und Schwellenwert prüfen"' "$UI" 'Schwellenaktion'
require_literal '"Kaufchance beobachten"' "$UI" 'Kaufaktion'
require_literal '"Datenbasis unvollständig – noch keine Entscheidung"' "$UI" 'Datenlückenaktion'

# 2.4.2: bestätigte Käufe, WATCH-Kandidaten und Datenqualität müssen sichtbar getrennt sein.
require_literal '"KAUF BESTÄTIGT"' "$UI" 'Bestätigter Kauf-Badge'
require_literal '"WATCH · NICHT BESTÄTIGT"' "$UI" 'WATCH-Badge'
require_literal 'private fun alertDataQuality' "$UI" 'Datenqualitätslogik'
require_literal 'Text("Datenqualität"' "$UI" 'Datenqualitätsüberschrift'
require_literal '"UNVOLLSTÄNDIG"' "$UI" 'Unvollständige Daten'
require_literal '"AUSREICHEND"' "$UI" 'Ausreichende Daten'
require_literal '"Keine Kaufentscheidung bei unvollständigen Daten"' "$UI" 'Datenqualitäts-Sperrhinweis'

# 2.4.4: Alarmcenter wird mit dem echten Aktionsplan und Depotkennzahlen verknüpft.
require_literal 'actionPlan: ActionPlan' "$UI" 'ActionPlan-Parameter'
require_literal 'advisorById: Map<String, PortfolioAdvisorCandidate>' "$UI" 'Advisor-Parameter'
require_literal 'itemsById: Map<String, InvestmentItem>' "$UI" 'Investmentdaten-Parameter'
require_literal 'positions: Map<String, PortfolioPosition>' "$UI" 'Positions-Parameter'
require_literal 'private fun plannedActionForAlert' "$UI" 'Aktionsplan-Zuordnung'
require_literal 'Text("Konkreter Plan"' "$UI" 'Konkreter-Plan-Überschrift'
require_literal '"Position um ca.' "$UI" 'Nachkaufbetrag'
require_literal '"Verkauf von ca.' "$UI" 'Verkaufsbetrag'
require_literal '"Sparplan' "$UI" 'Sparplanaktion'
require_literal 'Text("Depotwert"' "$UI" 'Depotwert-Kennzahl'
require_literal 'Text("Einstand / G/V"' "$UI" 'Einstand-GV-Kennzahl'
require_literal 'Text("Score"' "$UI" 'Score-Kennzahl'
require_literal 'Text("Risiko"' "$UI" 'Risiko-Kennzahl'
require_literal 'Text("Datenabdeckung"' "$UI" 'Datenabdeckung-Kennzahl'

echo 'PASS Alarmcenter mit Depotfilter, Prognose, Datenqualität und konkreten Handlungshinweisen'
