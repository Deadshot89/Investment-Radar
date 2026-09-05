# Investment Radar 2.2 – Depot-Berater, tägliche Analyse und sichere Ausführung

## Ziel

Investment Radar 2.2 entwickelt die bestehende Android-App von einem Depot-Tracker zu einem aktiven Depot-Berater weiter. Die App bewertet alle bekannten Depotpositionen täglich anhand derselben fachlichen Kriterien, erzeugt klare Handlungssignale, informiert nur bei relevanten Änderungen und bindet fällige Sparpläne in denselben kontrollierten Entscheidungsfluss ein.

Der Nutzer soll sein Depot nicht permanent manuell pflegen müssen. Gleichzeitig darf die App keine Trade-Republic-Zugangsdaten abgreifen, keine inoffiziellen Login-Sessions auslesen und keine Wertpapieridentitäten oder Ausführungsdaten erfinden.

## Ausgangsstand

Der aktuelle veröffentlichte Android-Stand ist 2.1.4 / versionCode 57. Das Backend bleibt auf der bestehenden 2.1.0-Linie, sofern für 2.2 kein ausdrücklich notwendiger Backend-Vertrag entsteht.

Die App besitzt bereits getrennte Bausteine für Portfolio, Sparpläne, Forecasts, Alerts, Push/Messaging, Investment-Details und App-Updates. 2.2 erweitert diese vorhandenen Verantwortlichkeiten, statt einen zweiten parallelen Analyse- oder Benachrichtigungsstack aufzubauen.

## Verbindliche Produktregeln

### Handlungssignale und Bewertung

Jede bekannte Depotposition wird anhand derselben Grundlogik analysiert. Die prozentuale Depotgewichtung darf für keine Position allein eine Handlungsempfehlung auslösen oder verhindern. Das gilt auch für Meta.

Für jede bewertbare Position existiert genau eines der Signale `NACHKAUFEN`, `HALTEN`, `REDUZIEREN`, `VERKAUFEN` oder intern `KEINE_BELASTBARE_BEWERTUNG`. Fehlende oder veraltete Pflichtdaten dürfen niemals durch ein geratenes Signal ersetzt werden.

Die Bewertung kombiniert typgerecht Unternehmens-/Produktqualität, Bewertung, Wachstum bzw. Ertragsentwicklung, fundamentale Entwicklung, Momentum, Risiko, Prognose/Zielbereich sowie Datenfrische und Datenvollständigkeit. ETF- und Fixed-Income-Produkte erhalten keine unpassenden Einzelaktienmetriken.

Jedes belastbare Signal zeigt eine kurze Begründung, positive Faktoren, Risiken, einen belastbaren Zielbereich sofern berechenbar, Analysezeitpunkt und Datenfrische. Die Einschätzung ist eine datenbasierte Entscheidungshilfe und keine Garantie.

## Tägliche automatische Analyse und Push

Die App plant genau einen automatischen Depot-Analysezyklus pro Kalendertag. Mehrfachstarts am selben Tag sind idempotent. Die Analyse und die Prüfung fälliger Sparpläne dürfen nicht davon abhängen, dass ein bestimmter Screen geöffnet wurde.

Bei Netzwerk-, API- oder Datenfehlern bleibt die letzte belastbare Analyse erhalten und wird als veraltet markiert. Es entsteht kein neues geratenes Signal.

Es gibt keine tägliche Routine-Push-Nachricht. Push entsteht nur bei einer relevanten Signaländerung, beim erstmaligen Entstehen eines belastbaren Signals, beim entscheidungsrelevanten Verlust einer belastbaren Bewertung oder bei einem fälligen Sparplan. Identische Ergebnisse erzeugen keine erneute Benachrichtigung. Ein Tipp öffnet die passende Detail- oder Sparplanansicht.

## Sparpläne

Geplante Sparplanbeträge verändern das reale Depot nicht. Am Fälligkeitstag entsteht genau eine offene Ausführung pro Plan und Datum. Der Nutzer bestätigt `Ausgeführt` oder `Nicht ausgeführt`; nur `Ausgeführt` erzeugt eine Depottransaktion.

Bei `Ausgeführt` nutzt die App den besten verfügbaren belastbaren Marktpreis aus der bestehenden Kursquelle. Fehlt ein belastbarer Preis, bleibt die Ausführung offen und das Depot unverändert.

Die zwei Private-Equity-Sparpläne bleiben getrennt. Solange ihre realen Instrumente nicht eindeutig verifiziert sind, bleibt die Ausführung gesperrt; Ticker, ISIN oder Depotposition werden nicht erfunden.

## Depotpflege und Trade Republic

2.2 implementiert keinen inoffiziellen Trade-Republic-Login, kein Session-Scraping, keine PIN-Abfrage und keine Nachbildung privater Schnittstellen. Der bestehende Depotstand bleibt die lokale Quelle der Wahrheit. Reguläre Sparpläne werden mit einem Bestätigungsschritt fortgeschrieben; Sonderkäufe und Verkäufe bleiben über den vorhandenen Depotfluss erfassbar.

Ein vollständiger automatischer Trade-Republic-Depotabgleich gehört nicht zu 2.2, solange keine geeignete offizielle Schnittstelle existiert.

Beim Öffnen eines Instruments versucht Android zuerst einen verifizierten Trade-Republic-App-/Deep-Link. Nur verifizierte Ziele werden verwendet. Ein Browser-Fallback darf nicht als erfolgreicher App-Start ausgegeben werden. Für Instrumente ohne verifizierte Zielkennung wird keine Kennung erfunden. Fehler beim externen Öffnen dürfen die App nicht verlassen oder zum Absturz bringen.

## Advisor-Architektur

Die fachliche Kernkomponente ist eine reine testbare Advisor Engine ohne Android-UI-, Notification- oder SharedPreferences-Abhängigkeiten. Unterschiedliche Instrumenttypen werden vor der Bewertung typgerecht normalisiert. Fehlende Pflichtdaten führen intern zu `KEINE_BELASTBARE_BEWERTUNG` statt zu erfundenen Defaultwerten.

Die App speichert pro Instrument mindestens das letzte belastbare Ergebnis und das unmittelbar vorherige Ergebnis, damit echte Signaländerungen erkannt werden können. Eine kleine begrenzte lokale Historie genügt.

## UI und verständliche Zustände

Die Depotübersicht zeigt pro Position kompakt das aktuelle Beratersignal; Signal und Depot-G/V bleiben getrennte Informationen. Die Detailansicht zeigt Handlungssignal, Begründung, positive Faktoren, Risiken, Zielbereich/Prognose, Zeitpunkt der letzten belastbaren Analyse und Datenstatus.

### Kein sichtbares „Nicht verfügbar“

Die Formulierungen `Nicht verfügbar`, `nicht verfügbar` und sinngleiche pauschale Platzhalter dürfen in der sichtbaren App-Oberfläche nicht verwendet werden. Die interne technische Zustandsbezeichnung `KEINE_BELASTBARE_BEWERTUNG` bleibt erlaubt, wird aber nicht wörtlich als generischer UI-Platzhalter angezeigt.

Stattdessen nennt die UI den tatsächlichen Zustand konkret, beispielsweise `Keine aktuellen Daten`, `Noch keine Analyse`, `Nicht im aktuellen Radar enthalten`, `Keine verifizierte Trade-Republic-Zuordnung` oder `Verbindung konnte nicht hergestellt werden`. Die Formulierung muss zur tatsächlichen Ursache passen und darf keine Verfügbarkeit vortäuschen.

## Android-Zurück-Navigation

Die Android-System-/Gesten-Zurück-Taste navigiert innerhalb der App durch den tatsächlich sichtbaren UI-Zustand und darf eine Unterseite nicht unmittelbar durch Beenden der Activity verlassen.

Die Priorität lautet:

1. Offener Dialog, Drawer oder Overlay wird geschlossen.
2. Eine Investment-Detailansicht kehrt zur vorherigen Liste bzw. Depotansicht zurück.
3. Sparplan-, Alarm- und andere Unterseiten kehren zu ihrer aufrufenden Hauptansicht zurück.
4. Erst auf der obersten Startansicht ohne offenen untergeordneten Zustand darf Android-Zurück die App verlassen.

Die Navigation verwendet eine zentrale Back-Entscheidung statt voneinander unabhängiger Back-Handler, damit ein Tastendruck genau einen Zustand zurücknimmt. Externe Browser-/Trade-Republic-Starts verändern den internen Back-State nicht. Notification-Deep-Links müssen einen sinnvollen Rückweg in die App besitzen.

## Persistenz und Idempotenz

Analyseergebnisse erhalten eine stabile Identität aus Instrument und Analysetag. Signaländerungs-Pushes erhalten eine stabile Ereignis-ID aus Instrument, vorherigem Signal, neuem Signal und Analysetag. Sparplanausführungen behalten ihre stabile Identität aus Plan und Fälligkeitsdatum.

## Fehler- und Sicherheitsregeln

- Keine erfundenen Ticker, ISINs, Kurse, Zielpreise oder fundamentalen Kennzahlen.
- Keine Handlungsempfehlung aus fehlenden Pflichtdaten.
- Keine Depotänderung ohne explizite Nutzerbestätigung einer Ausführung oder manuellen Transaktion.
- Keine doppelten Sparplanausführungen oder Signaländerungsbenachrichtigungen.
- Netzwerkfehler verändern bestehende Depotbestände nicht.
- Fehlgeschlagene Analysen überschreiben kein belastbares Ergebnis mit scheinbar gültigen Nullen.
- Externe Links werden defensiv geöffnet und verursachen keinen App-Absturz.
- Die sichtbare App enthält keinen generischen Text `Nicht verfügbar`/`nicht verfügbar`.
- Android-Zurück verlässt keine Unterseite direkt durch Beenden der Activity.

## Versionierung und Release

Der nächste Android-Release ist `2.1.5` mit `versionCode = 58`. Das Backend bleibt `2.1.0`, solange die Umsetzung mit dem vorhandenen API-Vertrag möglich ist. Falls eine unvermeidbare Backend-Vertragsänderung festgestellt wird, wird die Arbeit angehalten und erneut architektonisch freigegeben. Release 2.1.4 bleibt unverändert.

## Teststrategie

Die Umsetzung folgt TDD. Mindestens folgende Fälle werden automatisiert geprüft:

1. Signalberechnung ist unabhängig von Depotgewichtung.
2. Fehlende Pflichtdaten ergeben intern `KEINE_BELASTBARE_BEWERTUNG`.
3. Identische Tagessignale erzeugen keinen Push; echte Wechsel genau einen.
4. Tageslauf und Sparplanausführungen sind idempotent.
5. Fällige Sparpläne werden ohne Öffnen des Sparplan-Screens erkannt.
6. `Ausgeführt` verändert das Depot genau einmal; fehlender Preis gar nicht.
7. Nicht eindeutig zugeordnete Private-Equity-Pläne erzeugen keine Depotbuchung.
8. Veraltete Daten erhalten die letzte belastbare Analyse ohne erfundenes neues Signal.
9. Verifizierter Trade-Republic-App-Link wird bevorzugt; Browser-Fallback stürzt nicht ab.
10. Instrumente ohne verifizierte externe Zielkennung erzeugen keine erfundene Kennung.
11. UI-Contract-Test findet keine sichtbaren Vorkommen von `Nicht verfügbar`/`nicht verfügbar`.
12. Android-Zurück schließt zuerst Dialog/Overlay, dann Detailansicht, dann Unterseite und verlässt erst die Startansicht.
13. Ein Back-Tastendruck führt genau eine Navigationsstufe zurück.
14. Notification-Deep-Link in eine Detailansicht besitzt einen Rückweg zur passenden Hauptansicht.
15. Bestehende Portfolio-, Depotimport-, Sparplan- und Release-Verträge bleiben grün.
16. Release-Verträge erwarten Android 2.1.5 / code 58 und Backend 2.1.0.

## Definition of Done

2.2 gilt erst als fertig, wenn alle neuen JVM-/Contract-Tests und bestehenden Regressionstests grün sind, der signierte Android-Release-Build erfolgreich erstellt und die APK-Signatur geprüft wurde. Erst danach darf der Stand nach `main` integriert und als unveränderlicher Release 2.1.5 veröffentlicht werden.

Push, tägliche Analyse, Sparplan-Hintergrundprüfung, Textbereinigung, Android-Zurück-Navigation und Release-Build müssen durch frische Tests bzw. Workflow-Evidenz bestätigt sein.