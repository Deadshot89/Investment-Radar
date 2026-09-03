# Investment Radar 2.0 Design

## Ziel
Investment Radar 2.0 erweitert das bisher kleine feste Analyseuniversum auf etwa 1.000 fuer den Nutzer relevante, bei Trade Republic handelbare Aktien und ETFs. Das System soll aus diesem groesseren Universum robuste Kaufkandidaten, Beobachtungswerte und Portfolio-Ergaenzungen ableiten, ohne die Android-App mit 1.000 Vollanalysen gleichzeitig zu belasten.

## Produktprinzipien
- Ein grosses Update statt vieler Mini-Releases: Investment Radar 2.0 wird als zusammenhaengender Major-Release umgesetzt.
- Zieluniversum: ca. 1.000 Aktien und ETFs, fokussiert auf Trade-Republic-Handelbarkeit.
- Keine Hebelprodukte, Optionsscheine, Zertifikate, Penny Stocks oder bewusst illiquide Nischenwerte im Standarduniversum.
- Portfolio-only-Werte bleiben fuer Depotbewertung, Gewichtung, Konzentration und Detailansicht verfuegbar, sind aber niemals Teil der automatischen Neukauf-Auswahl.
- Prognosen bleiben Modell-/Szenario-Schaetzungen und werden nicht als Garantie dargestellt.

## Architekturentscheidung: Hybrides Universum
Wir verwenden ein hybrides Modell aus kuratiertem Grunduniversum und automatischer Qualitaetspruefung. Das Universum liegt serverseitig als kanonische Instrumentenliste vor und enthaelt stabile Identitaeten wie interne ID, Name, Ticker, ISIN, Instrumenttyp, Region, Branche und Kursdaten-Symbol. Regelmaessige Backend-Pruefungen validieren Datenverfuegbarkeit, Identitaet, Dubletten, Mindestdatenqualitaet und Handelbarkeitsstatus.

Das Backend ist die Autoritaet fuer das investierbare Universum. Die Android-App erhaelt kompakte Radar-Ergebnisse und laedt schwere Detaildaten erst bei Bedarf. Dadurch bleibt die App schnell, auch wenn das Backend etwa 1.000 Instrumente verwaltet.

## Universumszusammensetzung
Das Ziel ist kein mathematisch gleich verteiltes Weltportfolio, sondern ein fuer Trade Republic nuetzliches Analyseuniversum. Schwerpunkt:
- USA: grosse und mittlere Qualitaetsunternehmen aus allen wichtigen Branchen.
- Deutschland und Europa: liquide Large- und Mid-Caps mit vernuenftiger Datenabdeckung.
- Internationale Kernmaerkte: ausgewaehlte grosse Unternehmen aus Japan, Taiwan, Suedkorea, Kanada, Schweiz, Skandinavien und weiteren relevanten Maerkten, sofern Daten und Handelbarkeit belastbar sind.
- ETFs: breite Welt-/Regionen-ETFs, Faktor-ETFs, Branchen-ETFs und einige sinnvolle Anleihen-/Defensivbausteine, sofern bei Trade Republic handelbar.

Die Zielgroesse ist ca. 1.000 aktive Radar-Instrumente. Nicht valide oder nicht mehr handelbare Titel werden deaktiviert statt historisch geloescht.

## Datenmodell
Jedes Instrument bekommt zusaetzlich zu den bestehenden Feldern strukturierte Metadaten fuer die neue Navigation:
- `region`
- `country`
- `sector`
- `industry`
- `marketCapBucket` (`LARGE`, `MID`, optional `SMALL` nur bei ausreichender Liquiditaet)
- `tradeRepublicEligible`
- `universeActive`
- `portfolioOnly`
- `dataQualityTier`

Bestehende Felder wie `id`, `type`, `name`, `ticker`, `isin`, `tradeRepublicName`, Kursdaten, Scores, Recommendation und Forecast-Daten bleiben kompatibel.

## Backend-Auswertung
Die Analyse wird zweistufig aufgebaut:

### 1. Universe Screening
Fuer alle ca. 1.000 Instrumente werden kompakte Kennzahlen berechnet bzw. gecacht:
- aktueller Kurs / EUR-Kurs
- Tagesveraenderung
- Momentum
- Quality-Score
- Valuation-Score
- Growth-Score
- Risk-Score
- Gesamtscore
- Datenabdeckung
- objektive Empfehlung (`BUY`, `WATCH`, `NO_BUY`, `REVIEW`)

### 2. Detailanalyse on demand
Schwere Detaildaten und ausfuehrliche Forecast-Erklaerungen werden fuer Top-Kandidaten gecacht und ansonsten beim Oeffnen einer Detailseite geladen bzw. aktualisiert. Dadurch muss die App nicht 1.000 vollstaendige Fundamental- und Forecast-Objekte auf einmal laden.

## API-Konzept
Der bisherige Dashboard-Endpunkt bleibt fuer Startseite und Kompatibilitaet erhalten, wird aber nicht mehr als alleinige Quelle fuer das komplette Radar verwendet.

Neue bzw. erweiterte Backend-Funktionen:
- Radar-Summary mit paginierten/limitierten Ergebnissen, Filtern und Sortierung.
- Instrument-Detail nach ID.
- Universe-Metadaten fuer Filterwerte und Zaehler.
- Bestehende Portfolio-Kursauflosung bleibt mit dem vollen Backend-Instrumentensatz kompatibel.

Filter sollen serverseitig unterstuetzt werden, damit die App nicht dauerhaft 1.000 Vollobjekte im Speicher halten muss.

## Radar 2.0 UI
Der Radar wird von einer langen flachen Liste zu einer echten Discovery-Oberflaeche.

### Startbereiche
- Top Chancen
- Neu im Radar
- Starkes Momentum
- Attraktive Bewertung
- Qualitaetsaktien
- ETFs
- Depot-Ergaenzungen

### Vollstaendige Suche und Filter
- Freitextsuche nach Name, Ticker und ISIN
- Aktien / ETFs
- Region / Land
- Branche
- Risiko
- Datenqualitaet
- Score-Bereich
- Bewertung
- Momentum
- Empfehlung
- Im Depot / nicht im Depot
- Watchlist

Die App zeigt Ergebnisanzahl und aktive Filter klar sichtbar. Paging bzw. inkrementelles Nachladen verhindert UI-Last durch 1.000 Karten gleichzeitig.

## Kaufempfehlung fuer 100 EUR Monatsbudget
Die automatische Monatskauf-Auswahl arbeitet auf dem investierbaren 1.000er-Universum, nicht nur auf einer kleinen Vorauswahl. Vor der Zuteilung gelten weiterhin:
- objektives BUY-Signal als harte Grundvoraussetzung fuer echte Kaufzuteilung
- Mindestscore und Datenqualitaet
- Depotgewicht und Konzentrationsbremse
- Risikoanpassung
- `portfolioOnly == true` ist immer ausgeschlossen
- nicht valide oder nicht aktive Universumswerte sind ausgeschlossen

Das bestehende Konzentrationsverhalten fuer bereits sehr grosse Positionen bleibt erhalten.

## Portfolio
Das Portfolio behaelt Zugriff auf alle bekannten Instrumente, auch wenn diese nicht aktiv im Neukaufuniversum sind. Live-Kurs, Wertbasis, Depotgewicht und Konzentrationsanalyse werden nicht vom Radar-Filter abgeschnitten.

## Performance und Caching
- Serverseitige Batch-Analyse und Cache statt 1.000 sequenzieller App-Aufrufe.
- Kompakte Radar-Summaries statt Vollobjekte.
- Detaildaten lazy/on-demand.
- Zeitstempel und Datenqualitaet werden sichtbar mitgefuehrt.
- Fehler einzelner Symbole duerfen nicht das gesamte Universum blockieren.
- Backend-Verarbeitung wird in kontrollierte Batches zerlegt, um Provider-Limits und Timeouts zu vermeiden.

## Datenqualitaet und Sicherheit gegen Fehlzuordnungen
- ISIN ist bevorzugte stabile Identitaet, sofern vorhanden.
- Ticker-/Symbol-Mapping muss eindeutig sein; keine stille Migration bei Mehrdeutigkeit.
- Dubletten werden ueber interne ID plus normalisierte ISIN/Ticker-Regeln erkannt.
- Ein Instrument mit unzureichender Kurs- oder Analysedatenqualitaet kann sichtbar bleiben, aber kein BUY erhalten.
- Neue Instrumente werden erst nach erfolgreicher Validierung `universeActive`.

## Trade-Republic-Fokus
Trade-Republic-Handelbarkeit ist ein Produktkriterium, aber keine blind angenommene Eigenschaft. Das Datenmodell trennt `tradeRepublicEligible` von der eigentlichen Markt-/Kursidentitaet. Wo die Handelbarkeit nicht belastbar verifiziert werden kann, wird der Titel nicht als aktiver Standard-Kaufkandidat markiert.

## Migration
- Die bestehenden ca. 43 Kerninstrumente bleiben mit ihren IDs erhalten, um Portfolio, Watchlist, Alarme und historische Daten nicht zu brechen.
- Neue Instrumente werden additiv aufgenommen.
- Bestehende `portfolioOnly`-Logik bleibt erhalten.
- Bestehende Portfolio-Snapshot- und trackedShares-Semantik bleibt unveraendert.

## Tests und Release-Gates
Investment Radar 2.0 wird nicht veroeffentlicht, bevor mindestens folgende Gates gruen sind:
- Universe-Schema und Identitaetsregeln
- Mindestgroesse des aktiven Universums nahe 1.000
- keine Dubletten nach ID/ISIN
- `portfolioOnly` niemals im Neukauf-Radar oder Monatskauf
- serverseitige Filter/Sortierung/Paging
- Recommendation Engine gegen grosses Universum
- Portfolio bleibt mit Radar-Ausblendungen korrekt
- Android Radar 2.0 UI und Filter
- JVM-Tests
- Backend-Tests
- Android Contract Tests
- signierter Release-Build
- Live-Backend-Gate vor In-App-Publish

## Release
Der Zielrelease ist **Investment Radar 2.0.0**. Kleine Zwischenversionen sollen nicht als Nutzer-Releases veroeffentlicht werden. Implementierungscommits und CI-Zwischenstaende sind erlaubt, aber die naechste geplante Nutzer-Version ist der zusammenhaengende Major-Release 2.0.0.

## Nicht Bestandteil von 2.0.0
- automatische Orderausfuehrung bei Trade Republic
- Derivate/Optionsscheine/Hebelprodukte
- Garantie auf Kursentwicklung oder Forecast-Trefferquote
- unkontrolliertes Laden aller bei Trade Republic existierenden Produkte ohne Qualitaetsfilter
