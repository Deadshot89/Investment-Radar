# Investment Radar – vollständige Daten- und Analyse-Pipeline

Datum: 2026-09-10
Status: freizugebender Architekturentwurf
Branch: `complete-data-analysis-pipeline`

## 1. Ziel

Investment Radar soll für alle handelbaren und im Depot relevanten Titel möglichst vollständig belastbare Markt-, Historien-, Fundamental- und Prognosedaten liefern. Die App darf fehlende Daten nicht durch erfundene Kennzahlen ersetzen. Stattdessen werden mehrere reale Datenquellen in einer klaren Fallback-Kette genutzt, normalisiert, hinsichtlich Aktualität und Vollständigkeit bewertet und anschließend für Score, Empfehlung, Prognose und Alarmierung verwendet.

Ziel ist insbesondere, die aktuell sichtbaren Fälle mit sehr niedriger Datenabdeckung – z. B. 15 % – deutlich zu reduzieren. Ein Titel soll nur dann als unvollständig gelten, wenn nach allen vorgesehenen Quellen tatsächlich noch entscheidende Daten fehlen.

## 2. Problem im aktuellen Stand

Die aktuelle Pipeline lädt bereits Kurse, Historie und Fundamentaldaten. Die wesentlichen Schwächen sind:

- Fundamentaldaten hängen im Normalfall an Twelve Data `statistics`; bei fehlendem Tarifzugriff oder unvollständigen Antworten bleibt der Fundamentalblock leer.
- Die Radar-Analyse lädt Historie und Fundamentaldaten mit `refresh: false`. Ohne bereits gefüllten Cache kann dadurch zunächst nur ein Lade-/Fallbackzustand entstehen.
- Historie hat bereits einen Yahoo-Fallback, Fundamentaldaten jedoch keinen gleichwertigen zweiten Anbieter.
- Die 12-Monats-Prognose kann nur die gelieferten Scores, Historie und Fundamentaldaten verwenden. Fehlende Eingaben führen zu einer schwachen oder wenig erklärenden Prognose.
- Datenqualität wird derzeit im Wesentlichen zu einem Gesamtwert verdichtet. Für Nutzer ist nicht transparent genug, welcher konkrete Datenblock fehlt.
- Empfehlung, Alarmtext und Datenqualität können sich widersprechen, wenn ein älterer Empfehlungszustand bestehen bleibt, während die aktuell geladene Datenbasis unvollständig ist.

## 3. Gewählte Architektur

Gewählt wird die Architektur **Multi-Source mit Qualitäts-Gate**.

### Verworfene Alternative A: Eine einzige Datenquelle erzwingen

Vorteil: geringe Komplexität.
Nachteil: Ausfälle, Tarifgrenzen und länderspezifische Abdeckung führen weiterhin zu großen Datenlücken. Diese Variante löst das aktuelle Problem nicht robust genug.

### Verworfene Alternative B: Fehlende Kennzahlen lokal schätzen

Vorteil: scheinbar hohe Datenabdeckung.
Nachteil: erzeugt Scheingenauigkeit und kann zu falschen Anlageentscheidungen führen. Diese Variante ist für Investment Radar ausgeschlossen.

### Gewählte Alternative C: Multi-Source + Normalisierung + Datenqualitäts-Gate

Vorteile:
- echte Daten statt Schätzwerte,
- Fallback bei Anbieter- oder Symbolproblemen,
- nachvollziehbare Herkunft pro Datenblock,
- belastbarere Scores und Prognosen,
- klare Trennung zwischen vollständig, eingeschränkt und unzureichend analysierbaren Titeln.

## 4. Ziel-Datenmodell pro Instrument

Jeder analysierte Titel soll ein einheitliches Analyseobjekt erhalten.

### 4.1 Identität

- interne ID
- Name
- Ticker
- ISIN
- Trade-Republic-Name
- Typ Aktie/ETF
- Land
- Region
- Sektor
- Industrie
- Handels-/Provider-Symbole

### 4.2 Markt-/Kursdaten

Pflichtfelder soweit verfügbar:
- aktueller Kurs
- Kurs in EUR
- Währung
- absolute Tagesänderung
- prozentuale Tagesänderung
- Tageshoch/-tief, sofern Quelle verfügbar
- 52-Wochen-Hoch/-Tief, sofern verfügbar
- Zeitstempel der Kursdaten
- Quelle
- delayed/live-Kennzeichnung

### 4.3 Historie / Momentum

Mindestens 1 Jahr Tageshistorie, wenn für den Titel verfügbar.
Daraus werden normalisiert berechnet:
- 1M
- 3M
- 6M
- 12M
- Abstand zum 52W-Hoch
- Trendstatus
- Volatilitätsmaß
- optional einfacher gleitender Trendindikator, wenn genügend Datenpunkte vorhanden sind

Keine Momentum-Kennzahl darf als echter Wert ausgegeben werden, wenn die zugrunde liegende Historie nicht ausreichend ist.

### 4.4 Fundamentaldaten Aktien

Zielkennzahlen:
- KGV / P/E
- Price-to-Sales
- EV/EBITDA
- Free-Cashflow-Yield
- Umsatzwachstum
- EPS-/Gewinnwachstum
- operative Marge
- Nettomarge
- ROE
- ROIC
- Debt-to-Equity
- Marktkapitalisierung, sofern verfügbar

Zusätzlich:
- Quelle pro Provider
- asOf-Zeitpunkt
- stale-Kennzeichnung
- Fehler-/Fallbackinformation

### 4.5 ETF-Daten

ETFs dürfen nicht künstlich nach Aktien-Fundamentals bewertet werden. Für ETFs gilt eine eigene Datenlogik. Wenn im aktuellen Datenbestand keine ausreichenden ETF-spezifischen Kennzahlen vorhanden sind, werden Aktien-Fundamentalwerte nicht simuliert. ETF-Qualität basiert dann zunächst auf Preis-/Historienabdeckung, Risiko, Produktklassifikation und den bereits vorhandenen konfigurierten Metadaten.

## 5. Provider-Strategie

### 5.1 Kurse

Bestehende Marktquellen bleiben erhalten. Der Kursprovider soll weiterhin über die vorhandene Marktlogik und Caches laufen. Providerfehler dürfen nicht den gesamten Radar blockieren.

### 5.2 Historie

Reihenfolge:
1. Twelve Data, wenn API-Key und verwendbares Symbol vorhanden sind.
2. Yahoo Finance als Fallback.
3. gültiger Cache innerhalb der festgelegten Stale-Grenze.
4. fehlend mit konkretem Fehlergrund.

Die aktuelle Yahoo-Fallback-Logik bleibt erhalten, wird aber durch bessere Symbolprüfung und Diagnosedaten ergänzt.

### 5.3 Fundamentaldaten

Reihenfolge:
1. Twelve Data `statistics`, wenn verfügbar.
2. zusätzlicher realer Fundamental-Fallbackprovider.
3. Zusammenführung komplementärer Werte aus mehreren Providern, sofern die Werte eindeutig denselben Instrumenten zugeordnet werden können.
4. gültiger Cache innerhalb der Stale-Grenze.
5. fehlend mit konkretem Fehlergrund.

Der zweite Provider wird in einer eigenen Adapterdatei gekapselt. Das restliche System arbeitet nur mit dem kanonischen Fundamentalmodell und kennt keine providerspezifischen Feldnamen.

Es werden keine Werte überschrieben, nur weil eine zweite Quelle existiert. Priorisierung erfolgt nach Aktualität, Eindeutigkeit und Datenqualität. Konflikte zwischen stark abweichenden Providerwerten werden als Konflikt markiert und nicht blind aufgelöst.

## 6. Zentrale Normalisierung

Provider-Rohdaten werden vor Score/Forecast in ein gemeinsames Datenmodell überführt.

Neue zentrale Komponente: `analysisDataNormalizer`.

Aufgaben:
- Zahlenformate vereinheitlichen,
- Prozent-/Ratio-Einheiten vereinheitlichen,
- leere oder ungültige Werte zu `null` normalisieren,
- Providerquellen je Feld dokumentieren,
- Stale-Status je Block übernehmen,
- Konflikte markieren,
- keine Null-/Defaultwerte als echte Messwerte ausgeben.

## 7. Datenqualität

Datenqualität wird nicht mehr nur als ein unspezifischer Gesamtwert betrachtet.

### 7.1 Teilabdeckungen

Jedes Analyseobjekt erhält:
- `quoteCoverage`
- `historyCoverage`
- `fundamentalCoverage`
- `forecastInputCoverage`
- `overallCoverage`

### 7.2 Qualitätsklassen

- **HOCH**: >= 85 % und keine kritischen Pflichtblöcke fehlen
- **GUT**: 70–84 %
- **EINGESCHRÄNKT**: 50–69 %
- **UNVOLLSTÄNDIG**: < 50 % oder kritischer Block fehlt

Die genauen Gewichte werden im Implementierungsplan testbar festgelegt. Fundamentaldaten dürfen für ETFs nicht als fehlender Aktien-Pflichtblock gewertet werden.

### 7.3 Kritische Gates

Eine echte Kaufempfehlung ist nur zulässig, wenn:
- aktueller verwertbarer Kurs vorhanden,
- genügend Historie für Momentum vorhanden,
- für Aktien genügend Fundamentalwerte vorhanden,
- Gesamt- und Forecast-Abdeckung oberhalb der Mindestschwelle liegen,
- keine kritischen Providerkonflikte bestehen,
- Trade-Republic-Handelbarkeit bestätigt ist.

Ein bestehendes `BUY` wird bei aktuell unzureichender Datenbasis zu `WATCH` bzw. `REVIEW` zurückgestuft. Die UI darf dann nicht gleichzeitig „Kauf bestätigt“ und „Datenbasis unvollständig“ zeigen.

## 8. Analyse und Score

Der bestehende Score bleibt in den fünf Säulen erhalten:
- Qualität
- Bewertung
- Wachstum
- Momentum
- Risiko

Er wird jedoch nur aus nachweislich vorhandenen Eingabewerten berechnet.

Zusätzlich muss jede Säule liefern:
- Score oder `null`,
- verwendete Eingabefaktoren,
- Abdeckung der Säule,
- kurze Begründung,
- Datenquelle bzw. Quellen.

Der Gesamtscore muss klar zwischen „schlechter Wert“ und „nicht ausreichend analysierbar“ unterscheiden. Fehlende Daten dürfen nicht automatisch wie schlechte Daten wirken.

## 9. Prognose

Die vorhandene 12M-Prognose bleibt Basis, wird aber zu einem vollständigen Prognoseobjekt ausgebaut.

Für jeden ausreichend analysierbaren Titel:
- erwartete 12M-Veränderung in %
- Basisszenario
- Bull-Szenario
- Bear-Szenario
- Richtung: Aufwärts / Seitwärts / Abwärts
- Konfidenz / Prognosequalität
- mindestens 2 konkrete Treiber, soweit Daten vorhanden
- mindestens 1 Risikofaktor, soweit ableitbar
- verwendete Datenblöcke
- Prognosezeitpunkt

### 9.1 Prognosequalität

Die Prognose bekommt eine eigene Qualitätsstufe:
- HOCH
- MITTEL
- NIEDRIG
- NICHT_BELASTBAR

`NICHT_BELASTBAR` bedeutet: keine Prozentprognose als scheinbar exakter Wert ausgeben. Stattdessen zeigt die App, welche Eingabedaten fehlen.

### 9.2 Prognosen im Depot und Alarmcenter

Für Depotpositionen und Alarme wird nicht nur „Seitwärts“ angezeigt. Wenn eine belastbare Prognose vorliegt, werden Basis/Bull/Bear und die wichtigsten Gründe sichtbar. Bei niedriger Prognosequalität wird die Unsicherheit ausdrücklich angezeigt.

## 10. Auswertung in der App

Die vollständige Auswertung soll in Radar-Detail, Depot und Alarmcenter konsistent erscheinen.

Pflichtbereiche:
- Kurs & Tagesbewegung
- Datenqualität
- Score-Gesamtwert
- Qualität
- Bewertung
- Wachstum
- Momentum
- Risiko
- relevante Fundamentalkennzahlen
- 12M-Prognose Basis/Bull/Bear
- Prognosequalität
- positive Treiber
- Risiken
- konkrete Handlungsempfehlung
- nächster Prüfzeitpunkt bzw. Prüfbedingung

Im Alarmcenter bleibt die bestehende Struktur:
1. Was ist passiert?
2. Warum ist das wichtig?
3. Was sollst du jetzt tun?
4. Wann wieder prüfen?

Diese Texte müssen auf demselben aktuellen Analyseobjekt basieren wie Score und Prognose.

## 11. Datenaktualisierung

Die aktuelle Radar-Analyse darf nicht dauerhaft auf einem leeren Cache hängen bleiben.

Vorgesehen:
- schnelle Anzeige aus frischem Cache,
- Hintergrund-Refresh für veraltete oder fehlende Analyseblöcke,
- expliziter Refresh fordert Providerdaten neu an,
- Cache je Datenart mit eigenem TTL,
- Stale-while-revalidate für temporäre Providerausfälle,
- keine globalen Totalausfälle, wenn nur ein Provider oder ein Titel fehlschlägt.

Für Depotpositionen erhalten fehlende Analysedaten eine höhere Refresh-Priorität als beliebige nicht gehaltene Radarwerte.

## 12. Fehlerbehandlung und Diagnose

Pro Titel soll diagnostizierbar sein:
- welcher Provider abgefragt wurde,
- ob Quote erfolgreich war,
- ob Historie erfolgreich war,
- ob Fundamentals erfolgreich waren,
- ob Cache genutzt wurde,
- Alter der Daten,
- konkreter Fehlercode/-grund,
- welche Felder nach allen Fallbacks fehlen.

Technische Details bleiben intern bzw. in Diagnoseansichten; Nutzer sehen verständliche Aussagen wie „Fundamentaldaten fehlen“ statt Roh-HTTP-Fehlern.

## 13. Performance

Die Multi-Source-Lösung darf den Radar nicht blockieren.

Regeln:
- begrenzte Parallelität pro Provider,
- Bulk-Endpunkte nutzen, wenn verfügbar,
- Provider-Caches weiterverwenden,
- Analyse-Cache für Radar-Seiten nutzen,
- keine 2000 Einzel-Fundamentalabfragen bei jedem normalen Öffnen,
- vollständige Analyse bevorzugt für sichtbare Seite, Depotbestand und relevante BUY/WATCH-Kandidaten aktualisieren,
- Hintergrundbefüllung für restliches Universum.

## 14. Geplante Komponenten

Backend – bestehend zu erweitern:
- `backend/src/lib/radar.mjs`
- `backend/src/lib/fundamentals.mjs`
- `backend/src/lib/history.mjs`
- `backend/src/lib/scoring.mjs`
- `backend/src/lib/forecast12m.mjs`
- `backend/src/lib/analysisCache.mjs`
- `backend/src/lib/radarAnalysisCache.mjs`

Backend – neu:
- `backend/src/lib/analysisDataNormalizer.mjs`
- `backend/src/lib/dataQuality.mjs`
- `backend/src/lib/fundamentalFallbackProvider.mjs`
- optional `backend/src/lib/analysisDiagnostics.mjs`

Android – bestehend zu erweitern:
- `ApiClient.kt`
- `RadarModels.kt`
- `ForecastEngine.kt` bzw. Forecast-Darstellung
- `RadarScreen.kt`
- `InvestmentDetailScreen.kt`
- `PortfolioDashboard.kt`
- `AlertsScreen.kt`
- `DepotActionCenterMapper.kt`

Die Android-App berechnet keine fehlenden externen Finanzkennzahlen selbst. Das Backend bleibt Quelle der Markt-/Analysedaten; Android übernimmt Darstellung, lokale Portfolioinformationen und vorhandene clientseitige Entscheidungslogik.

## 15. API-Erweiterung

Radar- und Detailantworten werden rückwärtskompatibel erweitert.

Zusätzliche Felder je Instrument:
- `dataQuality`
- `coverageBreakdown`
- `missingData`
- `providerStatus`
- `scoreBreakdown`
- `forecast`
- `analysisWarnings`

Bestehende Felder bleiben während der Umstellung erhalten, damit ältere App-Versionen nicht brechen.

## 16. Tests

Umsetzung erfolgt testgetrieben.

Backend-Pflichttests:
- Fundamentals Primärprovider erfolgreich
- Fundamentals Fallbackprovider bei Primärfehler
- Zusammenführung komplementärer Fundamentalwerte
- Providerkonflikt wird markiert
- Historie Twelve -> Yahoo -> Cache
- leerer Cache löst nach Refresh echte Providerabfrage aus
- Datenqualität pro Block und gesamt
- ETF wird nicht wegen fehlender Aktien-Fundamentals bestraft
- BUY wird bei unzureichender Datenqualität blockiert
- BUY bleibt möglich bei vollständigen Daten
- Prognose Basis/Bull/Bear
- Prognose `NICHT_BELASTBAR` bei kritischen Lücken
- ein fehlerhafter Titel zerstört nicht den gesamten Radar
- Performance-/Parallelitätsgrenzen

Android-Pflichttests:
- neue API-Felder werden korrekt geparst
- fehlende Felder bleiben rückwärtskompatibel
- Detailansicht zeigt vollständige Auswertung
- Alarmcenter zeigt keine widersprüchliche Kaufbestätigung
- Prognose zeigt Basis/Bull/Bear und Qualität
- fehlende Prognose zeigt konkrete fehlende Daten
- Depotpositionen übernehmen aktualisierte Analyse

End-to-End-Verifikation:
- Apple
- Nel ASA
- Samsung Electronics GDR

Für diese drei Titel wird nach Deployment konkret geprüft, welche Daten real vom Live-Backend geliefert werden. Erfolg bedeutet nicht zwingend 100 %, sondern: alle technisch verfügbaren Daten sind befüllt, jede verbleibende Lücke ist begründet und keine falsche Kauf-/Prognoseaussage entsteht.

## 17. Release-Regeln

- Entwicklung auf eigenem Feature-Branch.
- Backend zuerst kompatibel erweitern und deployen.
- Live-Health- und Radar-Vertrag prüfen.
- Android anschließend gegen das reale Backend verifizieren.
- neue Android-Version nur veröffentlichen, wenn Unit-, Contract-, Backend- und Release-Tests grün sind.
- bestehende Releases werden nicht überschrieben; Version wird monoton erhöht.

## 18. Akzeptanzkriterien

Die Änderung ist fachlich abgeschlossen, wenn:

1. Apple, Nel ASA und Samsung Electronics GDR nicht pauschal bei 15 % hängen, sofern die Daten aus mindestens einem vorgesehenen Provider tatsächlich verfügbar sind.
2. Die App je Titel sichtbar macht, welcher Datenblock vollständig bzw. noch unvollständig ist.
3. Scores unterscheiden fehlende Daten von schlechten Kennzahlen.
4. Eine echte BUY-Empfehlung nur bei ausreichender Datenqualität möglich ist.
5. Prognosen Basis/Bull/Bear, Richtung, Qualität und Begründung enthalten, wenn die Datenbasis ausreichend ist.
6. Bei unzureichender Basis keine Scheingenauigkeit erzeugt wird.
7. Radar, Detail, Depot und Alarmcenter dieselbe aktuelle Analyse verwenden.
8. Provider- und Cachefehler nicht den gesamten Radar lahmlegen.
9. Alle neuen Backend-, Android- und Contract-Tests grün sind.
10. Nach Deployment das Live-Backend für die drei Referenztitel geprüft und das Ergebnis dokumentiert wurde.

## 19. Nicht im Umfang

- automatische Orderausführung
- garantierte Kursziele
- erfundene/ML-generierte Finanzkennzahlen ohne reale Datenbasis
- kostenpflichtige Provider-Abonnements ohne separate Freigabe
- vollständige fundamentale ETF-Analyse mit Fondsbestand-/TER-/Tracking-Difference-Daten, sofern dafür aktuell keine geeignete Quelle im Projekt vorhanden ist

## 20. Entscheidungsgrundsatz

Investment Radar soll lieber klar „nicht belastbar“ anzeigen als eine präzise wirkende, aber nicht ausreichend belegte Kauf- oder Kursprognose. Gleichzeitig muss das System alle realistisch verfügbaren Quellen ausschöpfen, bevor es eine Analyse als unvollständig einstuft.