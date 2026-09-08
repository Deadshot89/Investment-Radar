# Investment Radar 2.4 – Ereignis-, Aktions- und Ausführungsberater

## Ziel

Investment Radar 2.4 erweitert den in 2.2 und 2.3 aufgebauten Depot-Berater zu einem durchgängigen Entscheidungs- und Ausführungsfluss. Die App soll nicht nur sagen, ob eine Position nachgekauft, gehalten, reduziert oder verkauft werden sollte, sondern nachvollziehbar erklären, **welches neue Ereignis die Bewertung verändert hat, welche konkrete Handlung jetzt sinnvoll ist, wie eine Umschichtung aussehen soll und ob der Nutzer die vorgeschlagene Aktion tatsächlich ausgeführt hat**.

Der Nutzer bleibt jederzeit der Ausführende. Investment Radar erteilt keine automatische Order an Trade Republic, greift keine Zugangsdaten, PINs oder privaten Schnittstellen ab und erfindet keine Kurse, Ticker, ISINs, Produkt-IDs, Zielkurse, Nachrichten oder Ausführungsdaten.

2.4 baut auf dem veröffentlichten 2.3-Stand auf. Die fachlichen Regeln aus 2.2 und 2.3 bleiben verbindlich, insbesondere typgerechte Bewertung, 1/3/6/12-Monats-Prognosen, Cash-Rückhaltung, Sparplan-Berücksichtigung, konservative Umschichtungen, stabile tägliche Signale, sichere Trade-Republic-Navigation und zentrale Android-Zurück-Navigation.

## Produktprinzip

Der Hauptfluss lautet:

**Marktdaten + verifizierte Ereignisse → Bewertung → Aktionsplan → Nutzer führt extern aus → Trade wird bestätigt → Depot, Einstand und G/V werden aktualisiert.**

Jeder Schritt besitzt eine klare Quelle und einen nachvollziehbaren Status. Analyse, Empfehlung und tatsächliche Ausführung dürfen niemals vermischt werden.

## 1. MarketEventEngine

### Aufgabe

Die `MarketEventEngine` sammelt und normalisiert entscheidungsrelevante Ereignisse zu bekannten Depotpositionen und Radar-Kandidaten. Sie ergänzt die bisherige kurs- und kennzahlenbasierte Analyse um belegte Ereignisse, ohne Schlagzeilen oder Gerüchte direkt in Kauf-/Verkaufssignale umzuwandeln.

### Ereignistypen

Mindestens folgende Ereignisgruppen werden unterstützt:

- Quartals-/Jahreszahlen und Guidance
- Gewinnwarnung oder Prognoseanhebung
- Dividendenänderung
- Kapitalmaßnahme, Aktienrückkauf, Kapitalerhöhung
- Managementwechsel
- Übernahme, Abspaltung oder strategische Transaktion
- wesentliche Produkt-, Zulassungs- oder Vertragsmeldung
- regulatorische oder juristische Entscheidung
- Rating-/Bonitätsänderung, sofern für das Instrument relevant
- makroökonomisches Ereignis, wenn ein klarer Instrumentbezug existiert
- ETF-/Fonds-spezifische Änderungen wie Index-, Kosten- oder Strukturänderungen

### Ereignismodell

Ein normalisiertes Ereignis enthält mindestens:

- stabile `eventId`
- Instrumentidentität
- Ereignistyp
- Titel und kurze sachliche Zusammenfassung
- Zeitpunkt des Ereignisses und Veröffentlichungszeitpunkt
- Quelle bzw. Quellen
- Verifizierungsstatus
- Richtung des möglichen Einflusses: positiv, negativ, gemischt oder neutral
- erwarteter Zeithorizont: kurzfristig, mittelfristig, langfristig
- Relevanz/Materialität
- Konfidenz
- maschinenlesbare Tags
- stabilen Fingerprint zur Dublettenvermeidung

### Verifizierung

Nur verifizierte Ereignisse dürfen eine konkrete Handlungsänderung auslösen.

Quellen werden bevorzugt in dieser Reihenfolge behandelt:

1. Primärquelle / regulierte Veröffentlichung, z. B. Börsenmitteilung, regulatorisches Filing, Emittenten-IR.
2. Offizielle Unternehmens- oder Fondsmitteilung.
3. Hochwertige, nachvollziehbare Sekundärquelle.

Bei rein sekundären Meldungen muss die Aussage ausreichend belegbar sein; unbestätigte Gerüchte, Social-Media-Posts oder aggregierte Schlagzeilen ohne belastbare Quelle ändern keine Empfehlung. Die App darf solche Inhalte höchstens als noch nicht entscheidungsrelevant markieren.

### Ereigniswirkung

Die `MarketEventEngine` berechnet nicht selbst die finale Kauf-/Verkaufsaktion. Sie liefert ein strukturiertes Ereignissignal an den Advisor.

Normale Nachrichten wirken als zusätzlicher Faktor innerhalb der bestehenden Qualität-, Bewertung-, Wachstum-, Momentum-, Risiko- und Prognoselogik. Eine einzelne schwache Meldung darf eine langfristig intakte These nicht automatisch auf `VERKAUFEN` drehen.

Ein **kritisches verifiziertes Ereignis** darf die normale Mehrtages-Bestätigung einer Signalverschlechterung umgehen, wenn es die Investmentthese unmittelbar verändert, z. B. eine klare Gewinnwarnung, existenzielle regulatorische Entscheidung oder belastbare fundamentale Verschlechterung. Diese Ausnahme muss im Ergebnis explizit als ereignisgetriebene Sofortreaktion erkennbar sein.

## 2. Advisor-Integration

Der bestehende Advisor bleibt die fachliche Bewertungsinstanz. 2.4 ergänzt den AdvisorInput um verifizierte Ereignissignale und deren Datenqualität.

Für jede bewertbare Position bleibt genau eine strategische Handlung gültig:

- `NACHKAUFEN`
- `HALTEN`
- `REDUZIEREN`
- `VERKAUFEN`
- intern `KEINE_BELASTBARE_BEWERTUNG`

Für neue Radar-Kandidaten:

- `NEU_AUFNEHMEN`
- `NICHT_AUFNEHMEN`

Die 6- und 12-Monats-Sicht bleibt dominant für die strategische Handlung. 1 und 3 Monate beeinflussen primär Timing und Größenordnung. Verifizierte Ereignisse dürfen diese Gewichtung nur dann deutlich übersteuern, wenn ihre Materialität und der betroffene Zeithorizont dies rechtfertigen.

Jede geänderte Empfehlung zeigt:

- vorherige Handlung
- neue Handlung
- Zeitpunkt der Änderung
- wichtigste Gründe
- relevante verifizierte Ereignisse
- wichtigste Risiken
- Datenfrische
- Konfidenz
- 1/3/6/12-Monats-Prognosen, sofern belastbar

## 3. ActionPlanEngine

### Aufgabe

Die `ActionPlanEngine` übersetzt Advisor-Ergebnisse in einen **konkreten, ausführbaren Plan**, ohne selbst Orders auszuführen.

Die zentrale Frage lautet nicht nur „Wie ist die Aktie bewertet?“, sondern **„Was soll ich jetzt konkret tun?“**.

### Aktionsarten

Ein Aktionsplan darf folgende Schritte enthalten:

- bestehenden Sparplan unverändert weiterlaufen lassen
- Sparplan prüfen/pausieren/ändern empfehlen
- einmalig nachkaufen
- neue Position aufnehmen
- Position teilweise reduzieren
- Position vollständig verkaufen
- Erlös in eine oder mehrere bessere Chancen umschichten
- Betrag bewusst als Cash halten
- keine Aktion

### Konkrete Beträge

Jede kapitalwirksame Empfehlung enthält einen konkreten Euro-Betrag. Sofern ein aktueller belastbarer Kurs verfügbar ist, kann zusätzlich eine rechnerische Stückzahl bzw. Teilstückzahl angezeigt werden. Die App darf keine scheinbar ausführbare Stückzahl aus einem veralteten oder fehlenden Kurs ableiten.

Der Plan berücksichtigt gemeinsam:

- frei verfügbares Monatsbudget
- bereits geplante Sparplanraten
- vorhandenes Cash, sofern in der App als verfügbar bekannt
- mögliche Erlöse aus empfohlenen Reduzierungen/Verkäufen
- Mindestabstand zwischen Kandidatenbewertungen für eine Umschichtung
- Risiko und Datenqualität
- bestehende Depotpositionen
- neue Radar-Chancen

Die Summe der geplanten Kapitalverwendung darf die bekannte verfügbare Summe niemals überschreiten.

### Vollständiger Umschichtungsplan

Eine Umschichtung ist kein abstrakter Hinweis. Sie muss Quelle und Ziel vollständig zeigen, z. B. sinngemäß:

- Position A um 200 € reduzieren
- davon 120 € in Kandidat B investieren
- 50 € in Kandidat C investieren
- 30 € Cash halten

Die tatsächlich angezeigten Werte werden ausschließlich aus den realen aktuellen Depot-, Kurs- und Advisor-Daten berechnet. Es werden keine Beispielbeträge als echte Empfehlung ausgegeben.

Eine Umschichtung wird nur erzeugt, wenn die Quellposition belastbar schwächer geworden ist und mindestens eine deutlich bessere, belastbare Alternative existiert. Kleine Score-Unterschiede genügen weiterhin nicht.

### Sparplankonflikte

Wenn eine Position auf `REDUZIEREN` oder `VERKAUFEN` steht und gleichzeitig ein aktiver Sparplan Kapital zuführen würde, muss der Aktionsplan den Konflikt sichtbar machen. Der Sparplan wird niemals automatisch geändert.

Ein bereits geplanter Sparplanbetrag darf nicht zusätzlich als freies Monatsbudget doppelt verplant werden.

### Priorisierung

Aktionen werden nach Dringlichkeit und Materialität priorisiert. Eine belastbare ereignisgetriebene Verkauf-/Reduzieren-Entscheidung steht vor optionalem Nachkauf. Reine Beobachtung oder unverändertes Halten steht weiter unten.

## 4. Aktionscenter

2.4 erhält ein zentrales **Aktionscenter** als verbindliche Arbeitsliste des Nutzers.

Jede offene Aktion zeigt mindestens:

- Instrument
- Aktion
- konkreten Betrag
- optional berechnete Stückzahl bei belastbarem Kurs
- Priorität
- Grund
- relevante Ereignisse
- Konfidenz
- Erstellungszeitpunkt
- Status

Statuswerte:

- `OFFEN`
- `IN_BEARBEITUNG`
- `TEILWEISE_AUSGEFUEHRT`
- `AUSGEFUEHRT`
- `NICHT_AUSGEFUEHRT`
- `VERALTET`
- `ERSETZT`

Ein neuer Advisor-Lauf darf einen bestehenden offenen Plan nicht still überschreiben. Wenn sich die Empfehlung wesentlich ändert, wird die alte Aktion als `VERALTET` oder `ERSETZT` markiert und die neue Aktion nachvollziehbar verknüpft.

## 5. TradeExecutionLedger

### Ziel

Der `TradeExecutionLedger` bildet die **tatsächlich vom Nutzer ausgeführten Käufe und Verkäufe** ab. Er ist von Empfehlung und Aktionsplan getrennt.

Ein Klick auf „Bei Trade Republic öffnen“ gilt niemals als ausgeführter Trade.

### Erfassung

Nach externer Ausführung kann der Nutzer in Investment Radar einen Trade bestätigen. Mindestens erfasst werden:

- stabile `executionId`
- optional zugehörige `actionId`
- Instrumentidentität
- Kauf oder Verkauf
- Ausführungsdatum/-zeit
- tatsächlich ausgeführte Stückzahl
- tatsächlicher Ausführungspreis
- optionale Gebühren
- optional bekannte Steuern/Abzüge
- Notiz
- Erfassungsquelle: manuell, bestätigter Sparplan oder anderer verifizierter interner Ursprung
- Status

Die App übernimmt niemals automatisch angenommene Trade-Republic-Ausführungsdaten aus einem bloßen Linkstart.

### Teil-Ausführungen

Eine Aktion kann in mehreren Trades ausgeführt werden. Der Ledger summiert die tatsächlichen Ausführungen gegen die geplante Aktion.

Beispiel: Geplant sind 300 € Reduzierung, der Nutzer verkauft zunächst nur einen Teil. Die Aktion wird `TEILWEISE_AUSGEFUEHRT`; Restbetrag und Reststückzahl werden anhand der echten Ledger-Einträge nachvollziehbar angezeigt.

Eine Aktion wird erst `AUSGEFUEHRT`, wenn der Nutzer sie ausdrücklich abschließt oder die erfassten Ausführungen den Plan innerhalb definierter Rundungs-/Kursschwankungstoleranzen vollständig abdecken.

### Korrekturen

Fehlerhafte Trades dürfen nicht durch stilles Überschreiben der Historie verschwinden. Korrekturen werden als nachvollziehbare Änderung bzw. ersetzender Ledger-Eintrag gespeichert. Die ursprüngliche Erfassung bleibt auditierbar.

Doppelte Ausführungen mit derselben stabilen Identität dürfen nicht zweimal auf das Depot wirken.

## 6. Depotbestand, Einstand und Gewinn/Verlust

### Quelle der Wahrheit

Der reale Depotbestand ergibt sich aus verifizierten/importierten Ausgangsbeständen plus bestätigten Ledger-Transaktionen. Vorschläge, offene Aktionen und geplante Sparpläne verändern den Bestand nicht.

Importierte Depot-Snapshots müssen ihre Herkunft behalten. Wo ein Snapshot einen belastbaren Einstand enthält, wird dieser nicht durch einen geratenen Wert ersetzt. Wo Einstandsdaten fehlen, zeigt die App den tatsächlichen Zustand konkret statt `0` als scheinbar gültigen Wert.

### Käufe

Bestätigte Käufe erhöhen Stückzahl und investierten Einstand um den tatsächlichen Ausführungswert zuzüglich der als einstandsrelevant erfassten Gebühren.

Der angezeigte durchschnittliche Einstand wird aus den bekannten, belastbaren Bestands-/Ledger-Daten berechnet. Es dürfen keine fehlenden historischen Kaufpreise rekonstruiert oder erfunden werden.

### Verkäufe

Bestätigte Verkäufe reduzieren die Stückzahl. Realisierter Gewinn/Verlust wird nur berechnet, wenn die dafür benötigte Kostenbasis belastbar vorhanden ist.

Die App trennt mindestens:

- aktueller Marktwert
- bekannter Einstand
- unrealisierter G/V in € und %
- realisierter G/V aus erfassten Verkäufen
- Gesamt-G/V, soweit aus belastbaren Daten berechenbar

Die Darstellung ist eine Depot-/Performancehilfe und keine steuerliche Abrechnung. Fehlende historische Kostenbasis darf nicht als steuerlich korrekter Wert ausgegeben werden.

### Verkauf über vorhandenen Bestand hinaus

Ein Verkauf darf den bekannten Bestand nicht negativ machen. Eine Korrektur oder ein fehlender Import muss zuerst geklärt werden.

## 7. Verifizierte News und Ereignisse in der Oberfläche

News werden nicht als endloser Feed zum Selbstzweck dargestellt. Sichtbar sind primär Ereignisse, die eine bestehende Position, einen Radar-Kandidaten oder eine Empfehlung wirklich betreffen.

Jede relevante Meldung zeigt:

- sachliche Kurzbeschreibung
- Quelle
- Zeitpunkt
- Verifizierungsstatus
- betroffene Position
- erwartete Bedeutung
- ob und wie sich die Empfehlung geändert hat

Ein Ereignis ohne Handlungswirkung darf als Information erscheinen, erzeugt aber keine künstliche Aktion.

## 8. Dashboard „Was soll ich jetzt tun?“

Die zentrale 2.3-Karte wird zum primären Einstieg in 2.4 ausgebaut.

Sie zeigt den aktuellen Gesamtplan, z. B. in der Struktur:

- jetzt investieren
- umschichten
- Cash halten
- Sparplan prüfen
- dringende ereignisbedingte Aktion

Darunter stehen die wichtigsten offenen Aktionen in Prioritätsreihenfolge.

Ein Tap auf eine Aktion öffnet das Aktionsdetail, nicht direkt Trade Republic. Im Aktionsdetail sieht der Nutzer Analyse, Gründe, Ereignisse, Betrag, Prognosen und erst danach die kontrollierte externe Ausführungsmöglichkeit.

## 9. Detailansicht: Analyse, Empfehlung, Ausführung strikt trennen

Jede Investment-Detailansicht besitzt drei logisch getrennte Ebenen:

### Analyse

- aktueller Kurs und Datenfrische
- relevante Kennzahlen
- 1/3/6/12-Monats-Prognosen
- verifizierte Ereignisse
- Risiken

### Empfehlung

- strategische Handlung
- Konfidenz
- konkrete vorgeschlagene Aktion/Betrag
- Umschichtungsquelle/-ziel, wenn vorhanden
- Sparplankonflikt, wenn vorhanden

### Ausführung

- Trade Republic sicher öffnen
- anschließend tatsächliche Ausführung erfassen
- geplante vs. tatsächlich ausgeführte Menge/Betrag anzeigen
- Ausführungshistorie anzeigen

Das Öffnen des externen Brokers ändert keinen internen Ausführungsstatus.

## 10. Navigation, Deep Links und Android-Zurück

Die in 2.2 definierte zentrale Back-Logik bleibt verbindlich und wird um Aktionscenter und Ledger erweitert.

Priorität bei Android-Zurück:

1. Dialog/Overlay/Drawer schließen.
2. Trade-Erfassungsdialog schließen und zum Aktionsdetail zurückkehren.
3. Aktionsdetail zur aufrufenden Liste/Depotansicht zurückführen.
4. Investment-Detail zur aufrufenden Depot-/Radar-/Aktionsansicht zurückführen.
5. Aktionscenter/Sparplan/Alarm/sonstige Unterseite zur passenden Hauptansicht zurückführen.
6. Erst auf der obersten Startansicht darf Zurück die Activity verlassen.

Notification-Deep-Links öffnen die konkrete betroffene Aktion oder das Investment. Von dort muss ein sinnvoller Rückweg zum Aktionscenter bzw. zur Depotübersicht existieren.

Externe Trade-Republic-/Browser-Starts dürfen den internen Back-Stack nicht beschädigen.

## 11. Push-Deduplizierung

2.4 verschärft die Benachrichtigungslogik. Push ist eine Ausnahme für entscheidungsrelevante Änderungen, keine tägliche Zusammenfassung.

Push-Auslöser können sein:

- neue belastbare starke Kauf-/Neuaufnahmechance
- Wechsel zu `REDUZIEREN` oder `VERKAUFEN`
- kritisches verifiziertes Ereignis mit unmittelbarer Handlungsauswirkung
- wesentliche Änderung eines konkreten Umschichtungsplans
- Verlust der Belastbarkeit einer zuvor aktiven Empfehlung
- fälliger Sparplan bzw. offener Ausführungsschritt nach bestehender Sparplanlogik

Jede Notification erhält einen stabilen Ereignis-Fingerprint. Gleiche fachliche Ursache + gleiche Handlung + gleiche relevante Datenlage erzeugt keine neue Notification, auch wenn ein Hintergrundlauf erneut stattfindet.

Eine neue Nachricht ist erst zulässig, wenn sich mindestens eine entscheidungsrelevante Dimension materiell geändert hat, z. B. Handlung, Priorität, Betrag außerhalb einer definierten Toleranz, kritisches Ereignis oder Belastbarkeitsstatus.

Notification-Deduplizierung darf nicht nur auf Uhrzeit oder Tagesdatum beruhen.

## 12. Persistenz und Identitäten

Mindestens folgende stabilen Identitäten werden verwendet:

- `eventId` / Event-Fingerprint
- Advisor-Ergebnis pro Instrument und Analysestand
- `actionPlanId`
- `actionId`
- `executionId`
- Notification-Fingerprint

Wiederholte Hintergrundläufe müssen idempotent sein. Derselbe Event-Fingerprint darf nicht mehrfach als neues Ereignis gespeichert werden. Dieselbe `executionId` darf das Depot nicht zweimal verändern.

Historien werden begrenzt/archiviert, aber nicht so aggressiv gekürzt, dass aktuelle Empfehlungs- oder Trade-Änderungen nicht mehr nachvollziehbar sind.

## 13. Fehler- und Sicherheitsregeln

- Keine erfundenen Kurse, Zielkurse, Ticker, ISINs, Trade-Republic-Produkt-IDs, News, Ereignisse oder Ausführungen.
- Unverifizierte Gerüchte ändern keine konkrete Handlung.
- Fehlende Daten erscheinen nicht als synthetische Nullwerte.
- Eine fehlgeschlagene Aktualisierung überschreibt keine letzte belastbare Analyse.
- Ein Broker-Linkstart gilt nie als Trade-Ausführung.
- Keine Depotänderung ohne bestätigten Ledger-Eintrag bzw. bestätigte Sparplanausführung.
- Keine doppelten Ledger-Buchungen, Aktionscenter-Einträge oder Pushes durch wiederholte Jobs.
- Kein Verkauf darf einen bekannten Bestand negativ machen.
- Keine automatische Änderung eines Sparplans.
- Keine automatische Orderausführung.
- Kein Trade-Republic-Login-/Session-/PIN-Scraping und keine private API-Nachbildung.
- Externe Navigation wird nur über verifizierte Ziele geöffnet und stürzt bei Fehlern nicht ab.
- Sichtbare Fehlertexte nennen die konkrete Ursache; generisches `Nicht verfügbar` bleibt verboten.
- Alte Empfehlungen werden bei neuer Datenlage nicht still umgeschrieben, sondern nachvollziehbar ersetzt/veraltet.

## 14. Technische Architektur

Die Domänenlogik bleibt UI-unabhängig und testbar.

Bestehende Komponenten werden weiterverwendet und erweitert:

- `AdvisorInputFactory`
- `AdvisorEngine`
- `ForecastEngine`
- `AdvisorStore`
- `DailyAnalysisCoordinator`
- `SavingsPlanStore`
- bestehende Portfolio-/Depot-Persistenz
- bestehende Notification-Pipeline

Neue Kernkomponenten:

### `MarketEventEngine`

Normalisiert/verifiziert Ereignisse, dedupliziert sie und erzeugt strukturierte Event-Impacts.

### `ActionPlanEngine`

Erzeugt aus Advisor-Ergebnissen, Sparplänen, Budget, Cash, Depot und Event-Impacts einen konsistenten priorisierten Aktionsplan.

### `TradeExecutionLedger`

Speichert tatsächliche Ausführungen, Teil-Ausführungen und Korrekturen und liefert die bestätigten Bestandsänderungen an die Depotberechnung.

### Portfolio-/Performance-Rechner

Berechnet aus belastbaren Bestands- und Ledger-Daten Stückzahl, Einstand, realisierten und unrealisierten G/V. UI-Komponenten dürfen diese Regeln nicht separat nachbauen.

UI, WorkManager und Notifications konsumieren diese Domänenergebnisse. Fachliche Bewertung, Aktionsplanung und G/V-Berechnung dürfen nicht in Composables/Activities dupliziert werden.

## 15. Versionierung

2.4 folgt auf den 2.3-Android-Stand 2.1.6 / versionCode 59. Der vorgesehene nächste Android-Release ist **2.1.7 / versionCode 60**, sofern die Implementierung keinen separaten Backend-Vertrag erfordert.

Das Backend bleibt auf der bestehenden 2.1.0-Vertragslinie, solange alle benötigten Funktionen lokal bzw. mit den vorhandenen APIs umsetzbar sind. Eine unvermeidbare Backend-Vertragsänderung muss vor Integration separat geprüft werden.

## 16. Teststrategie / Akzeptanzkriterien

Die Umsetzung folgt TDD. Mindestens folgende Fälle müssen automatisiert geprüft werden:

1. `MarketEventEngine` dedupliziert dieselbe Meldung über stabile Fingerprints.
2. Unverifizierte Meldungen verändern keine Handlungsempfehlung.
3. Verifizierte normale Ereignisse wirken als Faktor, lösen aber nicht automatisch einen Verkauf aus.
4. Ein kritisches verifiziertes fundamentales Ereignis kann die normale Mehrtages-Bestätigung nachvollziehbar umgehen.
5. 1/3/6/12-Monats-Horizonte bleiben vorhanden; 6/12 Monate tragen weiterhin die strategische Grundhandlung.
6. `ActionPlanEngine` überschreitet nie das verfügbare Budget/Cash.
7. Bereits geplante Sparplanbeträge werden nicht doppelt als freies Budget verwendet.
8. `REDUZIEREN`/`VERKAUFEN` plus aktiver Sparplan erzeugt sichtbar einen Sparplankonflikt statt zusätzliche Kapitalzufuhr zu empfehlen.
9. Umschichtung wird nur bei deutlich besserer belastbarer Alternative erzeugt.
10. Umschichtungspläne enthalten vollständige Quelle-Ziel-Cash-Aufteilung.
11. Ein offener Aktionsplan wird bei neuer Empfehlung nicht still überschrieben, sondern veraltet/ersetzt.
12. Ein Trade-Republic-Linkstart verändert weder Depotbestand noch Aktionsstatus zu `AUSGEFUEHRT`.
13. Bestätigter Kauf verändert den Bestand genau einmal.
14. Bestätigter Verkauf verändert den Bestand genau einmal und kann den Bestand nicht negativ machen.
15. Mehrere Teil-Ausführungen werden korrekt gegen eine Aktion aggregiert.
16. Eine Teil-Ausführung setzt den Aktionsstatus auf `TEILWEISE_AUSGEFUEHRT`.
17. Korrekturen bleiben auditierbar und erzeugen keine doppelte Bestandswirkung.
18. Einstand wird aus belastbaren Ausgangs-/Ledger-Daten berechnet; fehlende Kostenbasis wird nicht als 0 erfunden.
19. Unrealisierter G/V wird nur mit belastbarem Einstand und aktuellem belastbarem Kurs berechnet.
20. Realisierter G/V wird nur mit belastbarer Kostenbasis berechnet.
21. Importierte Snapshots behalten ihre Herkunft und werden nicht durch geratenen Einstand überschrieben.
22. Identische fachliche Notification-Ereignisse erzeugen keinen Push-Spam über wiederholte Hintergrundläufe.
23. Materielle Änderung von Handlung/Ereignis/Betrag kann genau eine neue Notification erzeugen.
24. Notification-Deep-Link öffnet die konkrete Aktion bzw. Position und besitzt einen sinnvollen Rückweg.
25. Android-Zurück schließt zuerst Dialoge, dann Aktions-/Investmentdetails, dann Unterseiten und verlässt erst die Startansicht.
26. Ein Back-Tastendruck nimmt genau eine Navigationsstufe zurück.
27. Externe Broker-/Browser-Navigation beschädigt den internen Back-State nicht.
28. Fehlgeschlagene Daten-/News-Aktualisierung überschreibt keine letzte belastbare Analyse mit Nullwerten.
29. Sichtbare UI enthält keinen generischen Platzhalter `Nicht verfügbar`/`nicht verfügbar`.
30. Bestehende 2.2-/2.3-Verträge für Portfolio, Depotimport, Sparpläne, Forecasts, Cash-Budget, Umschichtungen, Notifications, sichere Trade-Republic-Navigation und Release bleiben regressionsfrei.
31. Release-Verträge erwarten Android 2.1.7 / code 60 und weiterhin Backend-Vertrag 2.1.0, solange keine separat freigegebene Backendänderung erfolgt.

## 17. Definition of Done

Investment Radar 2.4 gilt erst als fertig, wenn:

- die neuen Domänenkomponenten mit RED→GREEN-TDD umgesetzt sind,
- alle neuen JVM-/Contract-Tests grün sind,
- alle bestehenden 2.2-/2.3-Regressionstests grün bleiben,
- Depotbestand, Einstand, realisierter/unrealisierter G/V und Teil-Ausführungen mit reproduzierbaren Tests belegt sind,
- Push-Deduplizierung und Deep-Link-/Back-Navigation frisch verifiziert sind,
- der signierte Android-Release-Build erfolgreich erzeugt und seine Signatur geprüft wurde,
- der Release-Workflow vollständig grün ist,
- erst danach der Implementierungsstand nach `main` integriert wird.

Die Spezifikation selbst verändert `main` nicht. Die Implementierung erfolgt isoliert auf einem eigenen Entwicklungszweig und wird erst nach vollständiger Verifikation integriert.