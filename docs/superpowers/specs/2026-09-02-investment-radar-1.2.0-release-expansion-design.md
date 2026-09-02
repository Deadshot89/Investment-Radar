# Investment Radar 1.2.0 – Release Expansion Design

Date: 2026-09-02
Status: approved in chat on 2026-09-02
Target: Android app + existing Azure Functions backend
Release target: Android 1.2.0 / versionCode 31, Backend 1.2.0

## 1. Goal

Investment Radar 1.2.0 soll nicht als kleines Funktionsupdate veröffentlicht werden, sondern als klar erkennbares Großrelease. Die bereits fertiggestellten Analysis-V2-Funktionen bleiben unverändert die Basis. Vor Freigabe werden zusätzlich Suche/Filter, eine vollständige Wertpapier-Detailansicht, ein stärkeres Portfolio-Dashboard, Datenalter/-quellen und direkte Alarm-Navigation ergänzt.

Das Produkt bleibt reine Entscheidungshilfe. Es führt keine Orders oder Verkäufe automatisch aus.

## 2. Bestehende 1.2.0-Basis

Diese bereits implementierten Funktionen bleiben Bestandteil des Releases:
- Recommendation Engine V2 mit Quality, Valuation, Growth, Momentum und Risk
- 40 kuratierte Aktien/ETFs
- Fundamental- und historische Marktdaten mit Cache/Fallback
- 1D/1M/3M/6M/12M-Momentum
- BUY/WATCH/NO_BUY/REVIEW-Klassifizierung
- portfolioabhängige Monatsbudget-Verteilung
- Konzentrationsschutz
- REVIEW-/Sell-Check-Logik V2
- Firebase Data Messages und lokale Alarm-Einstellungen
- Alarmcenter mit Read/Unread, Filtern, Löschen und Tombstones
- Score-Aufschlüsselung
- Trade-Republic-Navigator
- manuelle Updateprüfung

## 3. Erweiterung A – Radar-Suche, Filter und Sortierung

### 3.1 Suche

Der Radar erhält eine lokale Volltextsuche über:
- Name
- Ticker/Symbol
- ISIN
- Typ (Aktie/ETF)

Die Suche arbeitet ausschließlich auf den bereits geladenen 40 Radar-Werten und benutzerdefinierten lokalen Investments. Es wird keine unbegrenzte externe Wertpapiersuche eingeführt.

### 3.2 Filter

Unterstützte Filter:
- Empfehlung: BUY / WATCH / NO_BUY / REVIEW
- Typ: Aktie / ETF
- Depotstatus: gehalten / nicht gehalten
- Watchlist: nur Watchlist
- Datenqualität: vollständig / reduziert / unzureichend
- Risiko: niedrig bis hoch anhand vorhandener Risk-Klassifizierung

Mehrere Filter dürfen gleichzeitig aktiv sein.

### 3.3 Sortierung

Sortieroptionen:
- Gesamtscore absteigend
- persönliche Monatsallokation absteigend
- Momentum 6M absteigend
- Tagesveränderung auf/absteigend
- Name A–Z

Default ist Gesamtscore absteigend. Filter- und Sortierzustand dürfen während einer laufenden App-Sitzung erhalten bleiben; eine dauerhafte Speicherung über App-Neustarts ist für 1.2.0 nicht erforderlich.

## 4. Erweiterung B – Wertpapier-Detailansicht

Jeder Radar-, Portfolio- und Alarm-Eintrag kann eine gemeinsame Detailansicht öffnen.

### 4.1 Kopfbereich

Die Ansicht zeigt:
- Name
- Symbol
- ISIN
- Aktie/ETF
- aktueller Kurs und Währung
- Tagesveränderung
- objektive Empfehlung
- Gesamtscore 0–100
- persönliche Monatsallokation, falls vorhanden
- Depotstatus und aktuelle Depotgewichtung

### 4.2 Score-Aufschlüsselung

Separate Darstellung für:
- Quality
- Valuation
- Growth
- Momentum
- Risk
- Coverage/Datenabdeckung

Fehlende Werte werden ausdrücklich als nicht verfügbar dargestellt und niemals als 0 interpretiert.

### 4.3 Momentum

Anzeige der vorhandenen Zeiträume:
- 1D
- 1M
- 3M
- 6M
- 12M

Zusätzlich ein kompakter Trendstatus aus dem bestehenden Analysemodell. In 1.2.0 wird kein komplexes interaktives Trading-Chart eingeführt. Ziel ist eine klare Vergleichsansicht der vorhandenen Renditehorizonte.

### 4.4 Fundamentaldaten

Aktien zeigen nur tatsächlich vorhandene normalisierte Fundamentaldaten. ETFs zeigen nur für ETFs sinnvolle vorhandene Struktur-/Exposure-Daten. Fehlende Daten erscheinen als „Nicht verfügbar“.

### 4.5 Gründe und Aktionen

Die Top-Gründe der Recommendation Engine werden sichtbar dargestellt.

Aktionen:
- zur Watchlist hinzufügen/entfernen
- als gekauft markieren bzw. Portfolio öffnen
- Trade Republic öffnen
- bei gehaltenen Positionen Käufe/Verkäufe verwalten

## 5. Erweiterung C – Portfolio-Dashboard V2

Das Portfolio soll nicht nur Positionen listen, sondern auf einen Blick beantworten: Wie groß ist mein Depot, wie ist es verteilt und wo besteht Konzentrationsrisiko?

### 5.1 Portfolio-KPIs

Oben werden angezeigt:
- aktueller Gesamtwert
- investierter Einstandswert
- absoluter Gewinn/Verlust
- prozentualer Gewinn/Verlust
- Anzahl gehaltener Positionen
- größte Position mit Gewichtung

Wenn für einzelne Positionen kein verwertbarer Kurs vorhanden ist, muss das Dashboard klar kennzeichnen, dass Gesamtwert und Performance unvollständig sind. Fehlende Kurse dürfen nicht als 0 in Performance-Kennzahlen eingehen.

### 5.2 Positionsübersicht

Je Position:
- aktueller Wert
- Einstandswert
- Gewinn/Verlust absolut und Prozent
- Depotgewichtung
- objektiver Score/Empfehlung
- persönliche Kaufempfehlung für das aktuelle Monatsbudget
- Konzentrationsstatus

### 5.3 Konzentrationsübersicht

Zusätzliche Übersicht der größten Positionen und Warnung bei hoher Konzentration. Die bestehende 40%-Logik bleibt verbindlich; die bereits definierte Ausnahme bei fehlenden weniger konzentrierten BUY-Kandidaten bleibt erhalten.

### 5.4 Keine Server-Synchronisierung

Alle Depotdaten bleiben lokal auf Android. Für dieses Release wird kein Benutzerkonto und keine serverseitige Portfolio-Speicherung eingeführt.

## 6. Erweiterung D – Datenalter und Datenquellen

### 6.1 Ziel

Der Nutzer soll erkennen können, ob eine Empfehlung auf frischen, gecachten oder teilweise fehlenden Daten basiert.

### 6.2 Anzeige

Wertpapier-Detail und bei Bedarf Radar-Karte zeigen kompakt:
- Analysezeitpunkt (`analysisAsOf`)
- Kursquelle
- History-/Momentum-Quelle
- Fundamentaldaten-Quelle, falls vorhanden
- Coverage
- Status: aktuell / gecacht / teilweise verfügbar / veraltet

### 6.3 Regeln

Die UI darf keine Quelle erfinden. Wenn der Backend-Datensatz für einen Teil keine Quelle kennt, wird „Quelle nicht verfügbar“ angezeigt.

Ein veralteter Datensatz darf weiterhin angezeigt werden, muss aber sichtbar als veraltet markiert werden. Eine veraltete oder niedrige Datenabdeckung darf niemals optisch wie ein voll belastbares BUY-Signal aussehen.

## 7. Erweiterung E – Alarm-Direktnavigation

Jeder Alarm mit `itemId` öffnet beim Antippen direkt die gemeinsame Wertpapier-Detailansicht des betroffenen Werts.

Verhalten:
- Alarm wird beim Öffnen als gelesen markiert.
- Existiert der Wert im aktuellen Radar, wird die normale Detailansicht verwendet.
- Bei einem lokal benutzerdefinierten Investment wird dessen lokale Detaildarstellung verwendet.
- Ist der Wert nicht mehr vorhanden, bleibt der Alarm lesbar und es erscheint eine klare Meldung statt eines Absturzes.

BUY-Alarme dürfen weiterhin global sein. REVIEW/SELL/THRESHOLD-Alarme bleiben depotbezogen gemäß der bereits implementierten Holding-Filterung.

## 8. Navigation und Komponenten

Neue Logik wird nicht zurück in die große `MainActivity.kt` eingebaut.

Vorgesehene getrennte Verantwortlichkeiten:
- `RadarFilterState` / Radar-Filterlogik
- `InvestmentDetailScreen` für die gemeinsame Wertpapieransicht
- `PortfolioDashboard` bzw. fokussierte Portfolio-KPI-Logik
- `DataFreshness` für Datenalter/-quellen-Aufbereitung
- bestehende `AlertCenterState`, `RecommendationEngine` und `TradeRepublicNavigator` bleiben getrennt

`MainActivity` koordiniert Navigation und State, enthält aber keine neue Analyse- oder Filterberechnungslogik.

## 9. Datenfluss

1. Android lädt das additive 1.2.0-Dashboard vom bestehenden Backend.
2. Radar-Suche/Filter/Sortierung arbeiten lokal auf den geladenen Items.
3. Portfolio-KPIs werden lokal aus PortfolioPosition + aktuellen Kursen berechnet.
4. Detailansicht nutzt ausschließlich bereits geladene Backend-/lokale Daten; kein zusätzlicher Provider-Aufruf pro Öffnen.
5. Alarm mit itemId navigiert zur selben Detailansicht.
6. Trade-Republic-Handoff bleibt explizite Nutzeraktion.

Dadurch verursacht das größere UI keinen zusätzlichen Marktdaten-Request bei jedem Bildschirmwechsel.

## 10. Fehlerbehandlung

- fehlender Kurs: Detailansicht zeigt „Kurs nicht verfügbar“; Portfolio-Gesamtperformance wird als unvollständig markiert
- fehlende Fundamentaldaten: „Nicht verfügbar“, keine erfundenen Werte
- fehlende Momentum-Horizonte: nur vorhandene Zeiträume zeigen
- veraltete Daten: sichtbar kennzeichnen
- unbekannte Alarm-itemId: Alarm bleibt lesbar, Detailnavigation zeigt Hinweis
- Trade Republic nicht verfügbar: bestehende Browser/Browse/Clipboard-Fallbacks
- Backend nicht erreichbar: vorhandenes App-Fehlerverhalten bleibt; lokale Portfolio-/Alarmdaten dürfen nicht verloren gehen

## 11. Tests

### 11.1 Android Unit Tests

Ergänzen für:
- Volltextsuche Name/Symbol/ISIN
- kombinierte Filter
- Sortierreihenfolge
- fehlende Score-/Momentum-/Fundamentalwerte
- Portfolio-KPI-Berechnung
- fehlender Kurs wird nicht als 0 gewertet
- Konzentrationsübersicht
- Datenfrische-Klassifizierung
- Alarm-itemId-Navigation

### 11.2 Android Regression/Contract Tests

Absichern:
- Radar zeigt Such-/Filter-/Sortiersteuerung
- Detailansicht zeigt fünf Scores + Coverage
- Detailansicht zeigt 1D/1M/3M/6M/12M nur bei vorhandenen Daten
- Portfolio-Dashboard zeigt KPI-Bereich
- Datenquelle/-alter sichtbar
- Alarm öffnet Wertpapierdetail
- bestehende Trade-Republic- und Update-Flows bleiben erhalten

### 11.3 Backend

Keine neue externe Datenquelle ist für diese Erweiterung erforderlich. Backend-Tests müssen weiterhin vollständig grün bleiben. Falls für Quellen-/Frischeanzeige additive Felder fehlen, dürfen diese nur additiv ergänzt werden; 1.1.29-Kompatibilität bleibt bestehen.

## 12. Release Gate

1.2.0 wird erst veröffentlicht, wenn alle Punkte erfüllt sind:
- bestehende Backend-Suite grün
- neue Android Unit Tests grün
- alle Android Contract-/Regressionstests grün
- signierte Release-APK erfolgreich gebaut
- APK-Signatur erfolgreich verifiziert
- Feature-Branch-Publish bleibt gesperrt
- finaler PR-Diff auf temporäre Dateien geprüft
- PR gemergt
- Backend auf main erfolgreich deployed und `/health` bestätigt Version 1.2.0
- Live-Dashboard-Schema geprüft
- main-Android-Workflow erzeugt die signierte 1.2.0-APK
- Release `v1.2.0` enthält die korrekte APK

## 13. Nicht Bestandteil von 1.2.0

- keine automatische Orderausführung
- keine automatischen Verkäufe
- kein Brokerage-Login
- kein Trade-Republic-Scraping
- keine unbegrenzte externe Aktiensuche
- keine serverseitige Depotablage
- kein komplexes Candlestick-/Trading-Chart
- keine garantierten Rendite- oder Anlageversprechen

## 14. Erfolgsdefinition

1.2.0 ist freigabefähig, wenn ein Nutzer innerhalb der App ohne externe Hilfsmittel:
1. interessante Werte suchen, filtern und sortieren kann,
2. für jeden Wert die vollständige Analyse nachvollziehen kann,
3. sein Depot und Konzentrationsrisiko auf einen Blick versteht,
4. Datenqualität und Datenalter erkennt,
5. aus einem Alarm direkt zum betroffenen Wert gelangt,
6. und anschließend bewusst selbst entscheidet, ob er Trade Republic öffnet.
