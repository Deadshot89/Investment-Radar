# Investment Radar 2.2 – Depot-Berater, tägliche Analyse und sichere Ausführung

## Ziel

Investment Radar 2.2 entwickelt die bestehende Android-App von einem Depot-Tracker zu einem aktiven Depot-Berater weiter. Die App bewertet alle bekannten Depotpositionen täglich anhand derselben fachlichen Kriterien, erzeugt klare Handlungssignale, informiert nur bei relevanten Änderungen und bindet fällige Sparpläne in denselben kontrollierten Entscheidungsfluss ein.

Der Nutzer soll sein Depot nicht permanent manuell pflegen müssen. Gleichzeitig darf die App keine Trade-Republic-Zugangsdaten abgreifen, keine inoffiziellen Login-Sessions auslesen und keine Wertpapieridentitäten oder Ausführungsdaten erfinden.

## Ausgangsstand

Der aktuelle veröffentlichte Android-Stand ist 2.1.4 / versionCode 57. Das Backend bleibt auf der bestehenden 2.1.0-Linie, sofern für 2.2 kein ausdrücklich notwendiger Backend-Vertrag entsteht.

Die App besitzt bereits getrennte Bausteine für Portfolio, Sparpläne, Forecasts, Alerts, Push/Messaging, Investment-Details und App-Updates. 2.2 erweitert diese vorhandenen Verantwortlichkeiten, statt einen zweiten parallelen Analyse- oder Benachrichtigungsstack aufzubauen.

## Verbindliche Produktregeln

### 1. Alle Depotpositionen werden gleich behandelt

Jede bekannte Depotposition wird anhand derselben Bewertungslogik analysiert. Die prozentuale Depotgewichtung darf für keine Position allein eine Handlungsempfehlung auslösen oder verhindern.

Das gilt ausdrücklich auch für Meta. Meta wird vollständig geprüft, erhält aber weder einen Bonus noch einen Malus aufgrund seiner aktuellen Depotgröße. Dasselbe gilt für Microsoft, Alphabet, Nel, ETFs und alle weiteren Positionen.

Die Depotzusammensetzung darf als Kontext angezeigt werden, beeinflusst aber nicht direkt das Handlungssignal.

### 2. Handlungssignale

Für jede bewertbare Position existiert genau eines der folgenden Signale:

- `NACHKAUFEN`
- `HALTEN`
- `REDUZIEREN`
- `VERKAUFEN`
- `KEINE_BELASTBARE_BEWERTUNG`

`KEINE_BELASTBARE_BEWERTUNG` ist zwingend, wenn die Datengrundlage nicht ausreichend oder veraltet ist. Fehlende Daten dürfen niemals durch ein geratenes Kauf-, Halte- oder Verkaufssignal ersetzt werden.

### 3. Bewertungsgrundlage

Das Signal basiert auf einer nachvollziehbaren Kombination aus:

- Unternehmens-/Produktqualität
- Bewertung
- Wachstum bzw. Ertragsentwicklung
- fundamentaler Entwicklung
- Momentum
- Risiko
- Prognose und Zielbereich
- Datenfrische und Datenvollständigkeit

Bei ETFs oder festverzinslichen Produkten werden nur passende Kriterien angewendet. Nicht sinnvolle Einzelaktienmetriken dürfen nicht künstlich auf Fonds oder Anleihen übertragen werden.

Die Gewichtung des Instruments im Nutzerdepot ist kein Signal-Faktor.

### 4. Erklärbarkeit

Jedes belastbare Signal zeigt mindestens:

- aktuelles Signal
- kurze Begründung
- wesentliche positive Faktoren
- wesentliche Risiken
- Prognose-/Zielbereich, sofern belastbar berechenbar
- Zeitstempel der zugrunde liegenden Analyse
- Hinweis auf Datenfrische

Die App muss deutlich machen, dass die Einschätzung eine datenbasierte Entscheidungshilfe und keine Garantie für zukünftige Kursentwicklung ist.

## Tägliche automatische Analyse

### Ausführung

Die App plant genau einen automatischen Depot-Analysezyklus pro Kalendertag. Android-seitig wird dafür die vorhandene Hintergrundarbeits-Infrastruktur genutzt oder, falls noch nicht vorhanden, WorkManager als einzige periodische Scheduling-Schicht eingeführt.

Mehrfachstarts am selben Tag müssen idempotent sein. Ein zweiter Lauf darf keine doppelten Benachrichtigungen oder doppelten Analysehistorien erzeugen.

### Ohne App-Öffnung

Die tägliche Analyse darf nicht davon abhängen, dass der Nutzer zuerst den Sparplan- oder Depot-Screen öffnet. Fällige Sparpläne und relevante Signaländerungen müssen im Hintergrund erkannt werden können, soweit Android die geplante Arbeit ausführen kann.

### Fehlerfall

Bei Netzwerkfehlern, API-Fehlern oder unzureichenden Daten bleibt die letzte belastbare Analyse erhalten, wird aber als veraltet markiert. Es wird kein neues Handlungssignal aus unvollständigen Daten erzeugt.

## Signaländerungen und Push-Benachrichtigungen

### Benachrichtigungsprinzip

Es gibt keine tägliche Routine-Push-Nachricht nur weil eine Analyse stattgefunden hat.

Eine Push-Benachrichtigung wird erzeugt, wenn mindestens einer dieser Fälle eintritt:

- ein belastbares Handlungssignal ändert sich
- ein zuvor nicht belastbar bewertbares Instrument erhält erstmals ein belastbares Signal
- ein belastbares Signal fällt wegen unzureichender Daten auf `KEINE_BELASTBARE_BEWERTUNG`, sofern dies für die Entscheidung relevant ist
- ein Sparplan ist zur Bestätigung fällig

Wiederholte identische Ergebnisse erzeugen keine erneute Push-Benachrichtigung.

### Signalwechsel

Die Benachrichtigung nennt mindestens Instrument, altes Signal, neues Signal und eine kurze Hauptbegründung. Ein Tipp auf die Benachrichtigung öffnet die passende Investment-Detailansicht oder den Sparplan-Bestätigungsfluss.

## Sparpläne

### Bestehende Regeln bleiben erhalten

Geplante Sparplanbeträge verändern das reale Depot nicht.

Am Fälligkeitstag entsteht genau eine offene Ausführung pro Plan und geplantem Datum. Der Nutzer bestätigt nur:

- `Ausgeführt`
- `Nicht ausgeführt`

Nur `Ausgeführt` darf eine echte Depottransaktion erzeugen.

### Automatischer Preis

Bei `Ausgeführt` verwendet die App den besten verfügbaren belastbaren Marktpreis aus der bestehenden Kursquelle. Es gibt keine manuelle Preiseingabe im Standardfluss.

Wenn kein belastbarer Preis verfügbar ist, bleibt die Ausführung offen und das Depot unverändert.

### Private Equity

Die zwei vorhandenen Private-Equity-Sparpläne bleiben getrennt.

Solange die zugrunde liegenden Produkte nicht eindeutig über eine reale, vom Nutzer gelieferte oder bereits verifizierte Instrument-ID identifiziert sind, bleibt `Ausgeführt` für diese Pläne gesperrt. Die App darf weder Ticker noch ISIN noch Depotposition erraten.

## Depotpflege ohne inoffiziellen Trade-Republic-Zugriff

Investment Radar 2.2 implementiert keinen inoffiziellen Trade-Republic-Login, kein Session-Scraping, keine PIN-Abfrage und keine automatisierte Nachbildung privater App-Schnittstellen.

Der bestehende Depotstand bleibt die lokale Quelle der Wahrheit. Reguläre Sparplanausführungen können mit einem einzigen Bestätigungsschritt fortgeschrieben werden. Sonderkäufe und Verkäufe bleiben über den vorhandenen Depotfluss erfassbar.

Ein vollständiger automatischer Trade-Republic-Depotabgleich gehört nicht zum 2.2-Lieferumfang, solange keine geeignete offizielle Schnittstelle zur Verfügung steht.

## Trade-Republic-Weiterleitung

### Ziel

Beim Öffnen eines Instruments versucht Android zuerst einen für das Instrument bekannten und sicheren Deep-/App-Link.

### Regeln

- Die App darf nur verifizierte Links verwenden.
- Ein erfolgreicher Browser-Fallback darf nicht als erfolgreicher Trade-Republic-App-Start ausgegeben werden.
- Wenn kein funktionierender App-Link bekannt ist, wird ein neutraler Browser-Fallback angeboten.
- Für Instrumente ohne verifizierte Trade-Republic-Zielkennung wird keine Kennung erfunden.
- Fehler beim externen Öffnen dürfen die Investment-Detailansicht nicht verlassen oder zum Absturz bringen.

## Analysearchitektur

### Advisor Engine

Die neue fachliche Kernkomponente erhält eine reine, testbare Bewertungsfunktion. Sie konsumiert normalisierte Markt-, Fundamental-, Forecast- und Datenfrischewerte und liefert ein `AdvisorResult`.

Empfohlene Domänenschnittstelle:

```kotlin
enum class AdvisorSignal {
    NACHKAUFEN,
    HALTEN,
    REDUZIEREN,
    VERKAUFEN,
    KEINE_BELASTBARE_BEWERTUNG
}

data class AdvisorResult(
    val itemId: String,
    val signal: AdvisorSignal,
    val rationale: String,
    val positives: List<String>,
    val risks: List<String>,
    val targetLowEur: Double?,
    val targetHighEur: Double?,
    val analyzedAtEpochMs: Long,
    val dataFreshness: AdvisorDataFreshness
)
```

Die Engine darf keine Android-UI-, Notification- oder SharedPreferences-Abhängigkeiten besitzen.

### Daten-Normalisierung

Vor der Bewertung werden unterschiedliche Instrumenttypen auf eine gemeinsame, aber typgerechte Eingabestruktur normalisiert. Einzelaktien, ETFs und festverzinsliche Produkte dürfen unterschiedliche Teilmetriken besitzen.

Fehlende Pflichtdaten führen zu `KEINE_BELASTBARE_BEWERTUNG` statt zu Defaultwerten, die wie echte Daten wirken.

### Analysehistorie

Die App speichert pro Instrument mindestens das letzte belastbare Ergebnis sowie das unmittelbar vorherige Ergebnis, damit echte Signaländerungen erkannt werden können.

Für 2.2 ist keine unbegrenzt wachsende Historien-Datenbank erforderlich. Eine kleine begrenzte lokale Historie genügt.

## UI

### Depotübersicht

Jede Depotposition zeigt kompakt das aktuelle Beratersignal. Signal und Depot-G/V bleiben visuell getrennte Informationen.

### Investment-Detailansicht

Die Detailansicht erweitert den bestehenden Forecast-Bereich um:

- Handlungssignal
- Begründung
- positive Faktoren
- Risiken
- Zielbereich/Prognose
- Zeitpunkt der letzten belastbaren Analyse
- Datenstatus

### Datenfehler

Bei veralteten oder unvollständigen Daten zeigt die UI keine scheinbar aktuelle Empfehlung. Das letzte belastbare Signal darf weiterhin sichtbar sein, muss aber eindeutig als veraltet markiert sein.

## Persistenz und Idempotenz

Analyseergebnisse erhalten eine stabile Identität aus Instrument und Analysetag. Signaländerungs-Pushes erhalten eine stabile Ereignis-ID aus Instrument, vorherigem Signal, neuem Signal und Analysetag.

Damit werden doppelte Benachrichtigungen bei wiederholter Hintergrundausführung verhindert.

Sparplanausführungen behalten ihre bereits vorhandene stabile Identität aus Plan und Fälligkeitsdatum.

## Fehler- und Sicherheitsregeln

- Keine erfundenen Ticker, ISINs, Kurse, Zielpreise oder fundamentalen Kennzahlen.
- Keine Handlungsempfehlung aus fehlenden Pflichtdaten.
- Keine Depotänderung ohne explizite Nutzerbestätigung einer Ausführung oder einer manuellen Transaktion.
- Keine doppelte Sparplanausführung.
- Keine doppelte Signaländerungsbenachrichtigung.
- Netzwerkfehler verändern bestehende Depotbestände nicht.
- Fehlgeschlagene Analyse überschreibt kein zuletzt belastbares Ergebnis mit scheinbar gültigen Nullen.
- Externe Links werden defensiv geöffnet und verursachen keinen App-Absturz.

## Versionierung und Release

Der nächste Android-Release ist `2.1.5` mit `versionCode = 58`.

Die bestehende Backend-Version bleibt `2.1.0`, solange die Umsetzung vollständig mit dem vorhandenen API-Vertrag möglich ist. Falls während der Implementierung eine unvermeidbare Backend-Vertragsänderung festgestellt wird, wird die Arbeit angehalten und als Architekturänderung erneut freigegeben, bevor der Backend-Vertrag verändert wird.

Der veröffentlichte Release 2.1.4 bleibt unverändert.

## Teststrategie

Die Umsetzung folgt TDD. Für jeden fachlichen Block wird zuerst ein reproduzierbarer RED-Test erzeugt.

Mindestens folgende Fälle müssen automatisiert geprüft werden:

1. Alle Instrumente verwenden dieselbe Grund-Signallogik unabhängig von ihrer Depotgewichtung.
2. Meta erhält bei identischen Analysedaten dasselbe Signal wie ein gleich bewertetes anderes Instrument, unabhängig von Positionsgröße.
3. Fehlende Pflichtdaten ergeben `KEINE_BELASTBARE_BEWERTUNG`.
4. Ein identisches Tagessignal erzeugt keine Push-Benachrichtigung.
5. Ein echter Signalwechsel erzeugt genau eine Push-Benachrichtigung.
6. Wiederholte Ausführung desselben Tageslaufs ist idempotent.
7. Ein fälliger Sparplan wird auch ohne Öffnen des Sparplan-Screens als offen erkannt.
8. Bestätigung `Ausgeführt` verändert das Depot genau einmal.
9. Fehlender belastbarer Kurs lässt die Sparplanausführung offen und das Depot unverändert.
10. Nicht eindeutig zugeordnete Private-Equity-Pläne können keine Depotbuchung erzeugen.
11. Veraltete Daten markieren die letzte belastbare Analyse als veraltet, ohne ein neues erfundenes Signal zu erzeugen.
12. Trade-Republic-App-Link wird bevorzugt; bei Nichtverfügbarkeit greift der Browser-Fallback ohne Absturz.
13. Instrumente ohne verifizierte externe Zielkennung erzeugen keine erfundene Kennung.
14. Bestehende Portfolio-, Depotimport-, Sparplan- und Release-Verträge bleiben grün.
15. Release-Verträge erwarten Android 2.1.5 / code 58 und weiterhin Backend 2.1.0.

## Definition of Done

2.2 gilt erst als fertig, wenn alle neuen JVM- und Contract-Tests sowie die bestehenden Regressionstests grün sind, der signierte Android-Release-Build erfolgreich erstellt und die APK-Signatur geprüft wurde.

Erst danach darf der Feature-Stand nach `main` integriert und als neuer unveränderlicher Release 2.1.5 veröffentlicht werden.

Es wird nicht als fertig bezeichnet, solange Push, tägliche Analyse, Sparplan-Hintergrundprüfung oder Release-Build nur theoretisch implementiert, aber nicht durch frische Tests bzw. Workflow-Evidenz bestätigt sind.
