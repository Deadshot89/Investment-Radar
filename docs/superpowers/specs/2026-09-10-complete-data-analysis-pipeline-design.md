# Investment Radar – vollständige Daten- und Analyse-Pipeline

Datum: 2026-09-10
Status: zur Nutzerfreigabe
Branch: `complete-data-analysis-pipeline`

## 1. Ziel

Investment Radar soll für handelbare und im Depot relevante Titel möglichst vollständig belastbare Kurs-, Historien-, Fundamental-, Score- und Prognosedaten liefern. Fehlende Werte werden nicht erfunden oder als Nullwert getarnt. Das Backend nutzt mehrere reale Quellen, normalisiert sie in ein gemeinsames Modell, bewertet Vollständigkeit und Aktualität und verwendet nur belegte Daten für Empfehlungen, Prognosen und Alarme.

Die aktuell sichtbaren Fälle mit sehr niedriger Datenabdeckung, z. B. 15 %, sollen deutlich reduziert werden. Ein Titel bleibt nur dann unvollständig, wenn nach allen vorgesehenen Quellen tatsächlich entscheidende Daten fehlen.

## 2. Aktuelle Schwächen

- Fundamentaldaten hängen primär an Twelve Data `statistics`.
- Radar lädt Historie/Fundamentals teilweise mit `refresh: false`; ein leerer Cache kann dadurch zu lang unvollständig bleiben.
- Historie hat bereits Yahoo-Fallback, Fundamentals bislang nicht gleichwertig.
- Prognosen können nur die tatsächlich gelieferten Scores, Historie und Fundamentals nutzen.
- Datenqualität ist für den Nutzer nicht fein genug nach Datenblock aufgeschlüsselt.
- Empfehlung und Datenqualität können sich widersprechen, z. B. „Kauf bestätigt“ bei gleichzeitig unvollständiger Datenbasis.

## 3. Architekturentscheidung

Gewählt: **Multi-Source + zentrale Normalisierung + Qualitäts-Gate**.

Verworfen:
- nur eine Datenquelle: zu anfällig für Tarif-/Provider-/Symbolprobleme;
- lokale Schätzung fehlender Finanzkennzahlen: erzeugt Scheingenauigkeit und ist ausgeschlossen.

## 4. Ziel-Datenmodell je Instrument

Identität:
- ID, Name, Ticker, ISIN, Trade-Republic-Name, Typ, Land, Region, Sektor, Industrie, Provider-Symbole.

Kursdaten:
- aktueller Kurs, EUR-Kurs, Währung, Tagesänderung absolut/prozentual, Tageshoch/-tief falls verfügbar, 52W-Hoch/-Tief falls verfügbar, Datenzeitpunkt, Quelle, delayed/live.

Historie/Momentum:
- mindestens 1 Jahr Tageshistorie wenn verfügbar;
- 1M, 3M, 6M, 12M, Abstand 52W-Hoch, Trendstatus, Volatilität, 50/200-Tage-Trend nur bei ausreichenden Datenpunkten.

Fundamentals Aktien:
- P/E, Price-to-Sales, EV/EBITDA, Free-Cashflow-Yield, Umsatzwachstum, EPS-Wachstum, operative Marge, Nettomarge, ROE, ROIC, Debt-to-Equity, Marktkapitalisierung soweit verfügbar.

ETFs:
- keine simulierten Aktien-Fundamentals;
- Qualitätsbewertung zunächst aus Kurs, Historie, Risiko, Produktklassifikation und vorhandenen Metadaten.

## 5. Provider-Strategie

### 5.1 Kurse

Bestehende Marktlogik und Caches bleiben erhalten. Ein Providerfehler darf nie den gesamten Radar blockieren.

### 5.2 Historie

Reihenfolge:
1. Twelve Data bei vorhandenem API-Key und brauchbarem Symbol.
2. Yahoo Finance Chart API als Fallback.
3. gültiger Cache innerhalb der Stale-Grenze.
4. fehlend mit konkretem Fehlergrund.

### 5.3 Fundamentals

Feste Reihenfolge:
1. Twelve Data `statistics` als Primärquelle.
2. Yahoo Finance als schlüsselloser sekundärer Fundamentalprovider für tatsächlich verfügbare Felder, insbesondere Bewertung/Marktkapitalisierung und weitere eindeutig lieferbare Kennzahlen.
3. SEC Companyfacts als ergänzende Quelle ausschließlich für eindeutig zuordenbare US-Unternehmen, wenn ISIN/Symbol/Issuer-Zuordnung sicher ist.
4. Zusammenführung komplementärer Werte aus diesen Quellen.
5. gültiger Cache innerhalb der Stale-Grenze.
6. fehlend mit konkretem Fehlergrund.

Für Yahoo wird ein eigener Adapter gebaut; für SEC ebenfalls ein gekapselter Adapter. Das restliche System sieht nur das kanonische Fundamentalmodell.

Keine Quelle darf einen vorhandenen Wert blind überschreiben. Auswahlregel je Feld:
1. eindeutig zugeordnet,
2. aktuellster nicht-staler Wert,
3. Primärquelle bei gleicher Aktualität,
4. Konfliktmarkierung, wenn zwei aktuelle Werte stark voneinander abweichen.

„Stark abweichend“ bedeutet bei Ratios/Wachstumswerten > 20 % relative Abweichung; bei Margen/ROE/ROIC > 5 Prozentpunkte absolute Abweichung. Bei Konflikt wird der Wert für BUY-Gates nicht als vollständig gewertet.

## 6. Zentrale Normalisierung

Neue Komponente: `backend/src/lib/analysisDataNormalizer.mjs`.

Sie vereinheitlicht:
- Zahlenformate,
- Prozent-/Ratio-Einheiten,
- `null` für ungültige/leere Werte,
- Quelle pro Feld,
- asOf/Stale-Status,
- Konflikte,
- fehlende Felder.

Keine `0` darf als Ersatz für „unbekannt“ verwendet werden.

## 7. Datenqualität

Neue Komponente: `backend/src/lib/dataQuality.mjs`.

Teilwerte:
- `quoteCoverage`
- `historyCoverage`
- `fundamentalCoverage`
- `forecastInputCoverage`
- `overallCoverage`

### 7.1 Gewichte Aktien

`overallCoverage`:
- Quote: 20 %
- Historie/Momentum: 30 %
- Fundamentals: 50 %

### 7.2 Gewichte ETFs

- Quote: 35 %
- Historie/Momentum: 45 %
- Produkt-/Risikometadaten: 20 %

ETFs werden nicht wegen fehlender Aktien-Fundamentals abgewertet.

### 7.3 Qualitätsklassen

- HOCH: >= 85 % und kein kritischer Block fehlt
- GUT: 70–84 %
- EINGESCHRÄNKT: 50–69 %
- UNVOLLSTÄNDIG: < 50 % oder kritischer Block fehlt

### 7.4 BUY-Gate

Eine echte BUY-Empfehlung ist nur zulässig, wenn gleichzeitig:
- `quoteCoverage = 100 %`,
- `historyCoverage >= 70 %`,
- bei Aktien `fundamentalCoverage >= 60 %`,
- `forecastInputCoverage >= 70 %`,
- `overallCoverage >= 70 %`,
- keine kritischen Providerkonflikte,
- Trade-Republic-Handelbarkeit bestätigt,
- Instrument aktiv und nicht `portfolioOnly`.

Ein berechnetes BUY, das diese Qualitätsregeln nicht erfüllt, wird zu WATCH; bei fehlender Handelbarkeitsbestätigung oder kritischem Datenfehler zu REVIEW.

Damit darf die UI nie gleichzeitig „Kauf bestätigt“ und „Datenbasis unvollständig“ anzeigen.

## 8. Score-System

Die fünf Säulen bleiben:
- Qualität
- Bewertung
- Wachstum
- Momentum
- Risiko

Jede Säule liefert:
- Score oder `null`,
- verwendete Faktoren,
- eigene Abdeckung,
- Kurzbegründung,
- Quellen.

Fehlende Daten dürfen nicht wie schlechte Daten bewertet werden. Ein Gesamtscore wird nur als belastbarer Score markiert, wenn `overallCoverage >= 50 %`; darunter wird er als vorläufig/nicht belastbar gekennzeichnet.

## 9. Prognose

Die vorhandene 12M-Engine wird erweitert, nicht ersetzt.

Ausgabe je ausreichend analysierbarem Titel:
- erwartete 12M-Veränderung,
- Basisszenario,
- Bull-Szenario,
- Bear-Szenario,
- Richtung Aufwärts/Seitwärts/Abwärts,
- Prognosequalität,
- mindestens zwei Treiber soweit ableitbar,
- mindestens ein Risikofaktor soweit ableitbar,
- verwendete Datenblöcke,
- Prognosezeitpunkt.

### 9.1 Prognosequalität

- HOCH: `forecastInputCoverage >= 85 %`
- MITTEL: 70–84 %
- NIEDRIG: 50–69 %
- NICHT_BELASTBAR: < 50 % oder kritischer Kurs-/Historienblock fehlt

Bei `NICHT_BELASTBAR` wird keine scheinpräzise Prozentprognose ausgegeben. Stattdessen nennt die App die konkret fehlenden Eingaben.

## 10. App-Auswertung

Radar-Detail, Depot und Alarmcenter verwenden dasselbe aktuelle Analyseobjekt.

Pflichtanzeige:
- Kurs/Tagesbewegung,
- Gesamt-Datenqualität,
- Teilabdeckung Quote/Historie/Fundamentals/Forecast,
- Gesamtscore,
- Qualität, Bewertung, Wachstum, Momentum, Risiko,
- relevante Fundamentalkennzahlen,
- 12M Basis/Bull/Bear,
- Prognosequalität,
- Treiber,
- Risiken,
- konkrete Handlungsempfehlung,
- nächster Prüfzeitpunkt/-bedingung.

Alarmcenter behält:
1. Was ist passiert?
2. Warum ist das wichtig?
3. Was sollst du jetzt tun?
4. Wann wieder prüfen?

Alle vier Texte müssen aus derselben aktuellen Analyse abgeleitet werden.

## 11. Aktualisierung und Cache

- frischer Cache wird sofort angezeigt;
- fehlende/veraltete Blöcke werden im Hintergrund neu geladen;
- expliziter Refresh erzwingt Providerabfrage;
- separate TTLs je Datenart;
- stale-while-revalidate bei temporären Ausfällen;
- Depotpositionen und offene BUY/WATCH-Kandidaten haben höhere Refresh-Priorität;
- nie 2000 Einzel-Fundamentalabfragen bei jedem normalen App-Start.

Wenn ein Analyseblock keinen Cache hat, darf `refresh: false` nicht zu einem dauerhaften Leerzustand führen: der Backendpfad stößt kontrolliert eine Befüllung an und liefert bis dahin einen klaren `loading/missing`-Status.

## 12. Diagnose

Neue Komponente: `backend/src/lib/analysisDiagnostics.mjs`.

Pro Titel werden intern erfasst:
- angefragte Provider,
- Erfolg je Datenblock,
- Cache-Nutzung,
- Datenalter,
- Fehlercode/-grund,
- fehlende Felder,
- Providerkonflikte.

Nutzer sehen verständliche Meldungen; technische Rohdetails bleiben Diagnose-/Backenddaten.

## 13. Performance

- begrenzte Parallelität je Provider,
- Bulk-Abfragen wenn möglich,
- Provider- und Analyse-Caches,
- sichtbare Radar-Seite/Depot zuerst,
- Hintergrundbefüllung für restliches Universum,
- Einzelfehler isolieren statt Gesamtanfrage abbrechen.

## 14. Komponenten

Backend erweitern:
- `radar.mjs`
- `fundamentals.mjs`
- `history.mjs`
- `scoring.mjs`
- `forecast12m.mjs`
- `analysisCache.mjs`
- `radarAnalysisCache.mjs`

Backend neu:
- `analysisDataNormalizer.mjs`
- `dataQuality.mjs`
- `yahooFundamentals.mjs`
- `secCompanyFacts.mjs`
- `analysisDiagnostics.mjs`

Android erweitern:
- `ApiClient.kt`
- `RadarModels.kt`
- Forecast-Darstellung/Engine
- `RadarScreen.kt`
- `InvestmentDetailScreen.kt`
- `PortfolioDashboard.kt`
- `AlertsScreen.kt`
- `DepotActionCenterMapper.kt`

Android erfindet keine externen Finanzkennzahlen. Das Backend bleibt Quelle der Markt-/Analysedaten.

## 15. API-Erweiterung

Rückwärtskompatible Zusatzfelder:
- `dataQuality`
- `coverageBreakdown`
- `missingData`
- `providerStatus`
- `scoreBreakdown`
- `forecast`
- `analysisWarnings`

Bestehende Felder bleiben während der Umstellung erhalten.

## 16. Teststrategie

Umsetzung erfolgt testgetrieben.

Backend-Pflichttests:
- Twelve Fundamentals erfolgreich,
- Yahoo-Fallback bei Twelve-Ausfall,
- SEC-Ergänzung bei US-Unternehmen,
- Zusammenführung komplementärer Werte,
- Konflikterkennung,
- Historie Twelve -> Yahoo -> Cache,
- Leer-Cache löst Befüllung aus,
- Datenqualität Aktien/ETF,
- BUY-Gate blockiert unvollständige Daten,
- BUY bleibt bei vollständiger Datenbasis möglich,
- Forecast Basis/Bull/Bear,
- Forecast-Qualitätsstufen,
- einzelner Provider-/Instrumentfehler blockiert Radar nicht.

Android-Pflichttests:
- neue API-Felder parsebar,
- alte API-Antworten weiter parsebar,
- vollständige Detailauswertung,
- keine widersprüchliche Kaufbestätigung,
- Forecast Basis/Bull/Bear + Qualität,
- konkrete Missing-Data-Anzeige,
- Depot übernimmt aktualisierte Analyse.

End-to-End-Liveprüfung nach Deployment:
- Apple,
- Nel ASA,
- Samsung Electronics GDR.

Erfolg bedeutet: alle technisch verfügbaren Daten sind befüllt; verbleibende Lücken sind konkret begründet; keine falsche Kauf-/Prognoseaussage entsteht.

## 17. Release-Reihenfolge

1. Entwicklung auf `complete-data-analysis-pipeline`.
2. Backend rückwärtskompatibel erweitern.
3. Backend-Tests grün.
4. Backend deployen und Live-Health/Radar prüfen.
5. Android gegen reale API erweitern.
6. JVM-/Contract-/UI-Regressionstests grün.
7. Liveprüfung Apple/Nel/Samsung.
8. Android-Version monoton erhöhen.
9. Release-Build, Signatur, In-App-Publish prüfen.

## 18. Akzeptanzkriterien

1. Apple, Nel ASA und Samsung Electronics GDR hängen nicht pauschal bei 15 %, sofern Daten aus vorgesehenen Quellen verfügbar sind.
2. Die App zeigt pro Datenblock Vollständigkeit und verbleibende Lücken.
3. Fehlende Daten werden nicht als schlechte Werte oder `0` behandelt.
4. BUY ist nur oberhalb der festen Qualitäts-Gates möglich.
5. Prognosen enthalten Basis/Bull/Bear, Richtung, Qualität und Begründung.
6. Bei unzureichender Basis gibt es keine Scheingenauigkeit.
7. Radar, Detail, Depot und Alarmcenter nutzen dieselbe aktuelle Analyse.
8. Provider-/Cachefehler legen den Radar nicht global lahm.
9. Alle neuen Backend-, Android- und Contract-Tests sind grün.
10. Die drei Referenztitel werden nach Deployment live geprüft und dokumentiert.

## 19. Nicht im Umfang

- automatische Orderausführung,
- garantierte Kursziele,
- erfundene/ML-generierte Finanzkennzahlen ohne reale Datenbasis,
- kostenpflichtige Provider-Abonnements ohne separate Nutzerfreigabe,
- vollständige ETF-Fundamentalanalyse mit TER/Tracking-Difference/Fondsbestand, solange keine geeignete Quelle freigegeben ist.

## 20. Entscheidungsgrundsatz

Investment Radar zeigt lieber klar „nicht belastbar“ als eine präzise wirkende, aber unbelegte Kauf- oder Kursprognose. Gleichzeitig müssen alle vorgesehenen realen Quellen und gültigen Caches ausgeschöpft werden, bevor ein Titel als unvollständig eingestuft wird.