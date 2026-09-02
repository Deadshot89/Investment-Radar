# Investment Radar 1.2.0

Großes Funktionsrelease für datengetriebene Empfehlungen, Portfolio-Intelligenz und Alarme.

## Analyse V2
- objektiver Gesamtscore aus Qualität, Bewertung, Wachstum, Momentum und Risiko
- BUY / WATCH / NO_BUY / REVIEW statt fest verdrahteter Kaufstatus
- Datenabdeckung und Begründungen je Wertpapier
- 1D-, 1M-, 3M-, 6M- und 12M-Momentum
- Fundamentaldaten mit Cache, Stale-Kennzeichnung und sauberem Fallback ohne erfundene Werte
- 40 kuratierte Aktien und ETFs

## Persönlicher Monatsplan
- Monatsbudget bleibt frei einstellbar
- Depotgewichtung beeinflusst die persönliche Zuteilung
- Konzentrationsschutz ab 20 %, starke Reduktion ab 30 %, normaler Neukauf ab 40 % blockiert
- eigene Werte zählen bei der Konzentrationsberechnung mit
- Risiko 4–5 reduziert Positionsgrößen
- Euro-Zuteilungen werden auf ganze Beträge verteilt
- wenn kein sinnvoller BUY-Kandidat übrig bleibt, wird Cash gehalten

## Review- und Alarm-Logik
- Score-Abfall, schwacher Gesamtscore, Trendbruch, Kursschwellen und ungewöhnliche Tagesverluste
- fundamentale Verschlechterung nur bei ausreichend abgedeckten und nicht veralteten Fundamentaldaten
- REVIEW ist ein Prüfsignal und kein automatischer Verkauf
- Firebase Data Messages erlauben lokale Benachrichtigungsfilter
- Alarm-Einstellungen für BUY, REVIEW, SELL und Schwellenwerte
- Alarmcenter mit ungelesen/gelesen, Filtern, Löschen und Tombstones gegen sofortiges Wiedererscheinen

## Bedienung
- neues Radar mit Suche, Filtern und Sortierung
- neue Investment-Detailansicht mit Score-Aufschlüsselung, Datenstatus, Momentum und Fundamentaldaten
- neues Portfolio-Dashboard
- stabilerer Trade-Republic-Navigator mit direktem HTTPS-Handoff und sicheren Fallbacks
- manuelle Updateprüfung bestätigt nun auch explizit, wenn bereits die aktuelle Version installiert ist, oder zeigt einen verständlichen Fehler

## Release-Sicherheit
- Android 1.2.0 / versionCode 31
- Backend 1.2.0 / Analysis Model V2
- Feature-Branches dürfen weder Azure deployen noch Update-APK veröffentlichen
- Android-Veröffentlichung auf main wird blockiert, bis das Live-Backend 1.2.0 bestätigt ist
- keine automatische Orderausführung und keine automatischen Verkäufe
- Portfolio-Daten bleiben lokal auf dem Gerät
