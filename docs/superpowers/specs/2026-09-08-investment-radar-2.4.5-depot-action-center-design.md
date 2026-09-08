# Investment Radar 2.4.5 – Depot-Aktionscenter

## Ziel

Investment Radar 2.4.5 macht aus den in 2.4.4 bereits vorhandenen Einzelhinweisen eine zentrale, depotweite Arbeitsliste: **„Das solltest du jetzt mit deinem Depot machen.“** Der Nutzer soll nicht mehrere Alarme einzeln interpretieren müssen, sondern eine priorisierte, budgetkonsistente Reihenfolge konkreter Maßnahmen erhalten.

Die fachliche Entscheidungsquelle bleibt der bestehende `ActionPlanEngine`. Es wird **keine zweite Empfehlungslogik** im UI, im Alert-Center oder in einem neuen Service aufgebaut. Das Depot-Aktionscenter liest den zuletzt belastbaren Plan, ordnet dessen Schritte verständlich, ergänzt vorhandene Depot- und Analysekennzahlen und führt den Nutzer zu den bestehenden Ausführungs-/Buchungsflüssen.

Der Nutzer bleibt jederzeit der Ausführende. Es werden keine Orders automatisch an Trade Republic gesendet und ein App-Klick gilt niemals als ausgeführter Trade.

## Produktprinzip

Der Hauptfluss lautet:

**Depot + Monatsbudget + Sparpläne + belastbare Advisor-Daten + verifizierte Ereignisse → `ActionPlanEngine` → depotweit priorisierte Arbeitsliste → Nutzer öffnet Aktie/Depotbuchung → tatsächliche Ausführung wird separat erfasst.**

Die Arbeitsliste darf das verfügbare Kapital niemals mehrfach verplanen. Bestehende Depotrisiken und Verkauf-/Reduzieren-Aktionen stehen vor optionalen Käufen.

## 1. Zentrale Arbeitsliste

Im bestehenden Alarm-/Aktionsbereich wird oberhalb der Einzelalarme ein Abschnitt mit der Überschrift **„Das solltest du jetzt mit deinem Depot machen“** eingeführt.

Die Liste enthält nur fachlich relevante, aktuelle Schritte aus dem `ActionPlanEngine`. Für jeden Schritt werden mindestens angezeigt:

- Instrumentname
- klare Aktion
- konkreter Euro-Betrag, falls kapitalwirksam
- Priorität
- Kurzbegründung
- Depotstatus: bereits im Depot oder neue Position
- Depotwert, sofern vorhanden
- Einstand/G&V, sofern vorhanden
- Score
- Risiko
- Datenabdeckung
- 12-Monats-Prognoserichtung, sofern belastbar
- Datenqualität bzw. Sperrgrund

Die vorhandenen Einzelalarme bleiben sichtbar, werden aber als Detail-/Ereignisebene unterhalb des depotweiten Aktionsplans behandelt.

## 2. Zulässige Aktionsdarstellung

Die vorhandenen `ActionPlanStepType`-Werte werden in eindeutige deutsche Nutzertexte übersetzt:

- `SELL` → „Position verkaufen“ bzw. „Verkauf von ca. X € prüfen“
- `REDUCE` → „Position reduzieren“ bzw. „Reduzierung um ca. X € prüfen“
- `REVIEW_SAVINGS_PLAN` → „Sparplan prüfen“
- `KEEP_SAVINGS_PLAN` → „Sparplan X € beibehalten“
- `BUY_MORE` → „Position um ca. X € erhöhen“
- `OPEN_POSITION` → „Neue Position mit ca. X € eröffnen“
- `HOLD_CASH` → „X € als Cash halten“

Es werden keine Beträge aus UI-Schätzungen erfunden. Der Betrag stammt ausschließlich aus dem Aktionsplan. Fehlt ein belastbarer Betrag, wird keine scheinbar genaue Euro-Empfehlung angezeigt.

## 3. Priorisierung

Die Liste ist nach depotweiter Dringlichkeit sortiert. Verbindliche Reihenfolge:

1. `SELL`
2. `REDUCE`
3. `REVIEW_SAVINGS_PLAN`
4. `BUY_MORE`
5. `OPEN_POSITION`
6. `KEEP_SAVINGS_PLAN`
7. `HOLD_CASH`

Innerhalb derselben Aktionsart werden bestehende Depotpositionen vor externen Kandidaten gezeigt. Danach entscheidet die Priorität des `ActionPlanStep`, anschließend ein stabiler Tie-Breaker über Instrument-ID/Aktions-ID.

Ein kritisches verifiziertes Ereignis behält seine bereits in 2.4 definierte Sofortpriorität.

## 4. Budgetkonsistenz

Der `ActionPlanEngine` bleibt für die Kapitalverteilung verantwortlich. Das UI darf Beträge weder addieren noch umverteilen noch eigenständig neue Kaufbeträge berechnen.

Folgende Invarianten müssen im 2.4.5-Vertrag abgesichert sein:

- Die Summe von `BUY_MORE` + `OPEN_POSITION` darf das vom Plan freigegebene Kaufbudget nicht überschreiten.
- Aktive Sparplanraten werden nicht ein zweites Mal als freies Monatsbudget verwendet.
- Verkauf-/Reduzierungserlöse werden nur dann als Quelle für Umschichtungen gezeigt, wenn sie im Aktionsplan ausdrücklich berücksichtigt sind.
- `HOLD_CASH` bleibt als bewusste Restposition sichtbar, wenn der Plan Kapital nicht investieren will.
- Keine UI-Komponente berechnet einen zusätzlichen Kaufbetrag außerhalb des `ActionPlanEngine`.

## 5. Datenqualitäts-Sperren

Die Kaufaktionen `BUY_MORE` und `OPEN_POSITION` dürfen nur als ausführbare Kaufempfehlung dargestellt werden, wenn die zugrunde liegende Advisor-Bewertung belastbar ist.

Bei fehlenden, veralteten oder unzureichenden Daten wird stattdessen sichtbar:

**„Nicht kaufen – Datenbasis unvollständig“**

Dabei bleibt das Instrument als Beobachtungs-/Prüfpunkt sichtbar, aber ohne ausführbaren Kaufbetrag und ohne Formulierung, die einen bestätigten Kauf suggeriert.

WATCH-Kandidaten bleiben ausdrücklich als **nicht bestätigt** markiert und dürfen nicht in eine bestätigte Kaufaktion umetikettiert werden.

## 6. Navigation und Ausführung

Jede instrumentbezogene Aktion erhält eine direkte Schaltfläche:

- bei `BUY_MORE` / `OPEN_POSITION`: bestehende Depot-/Kaufbuchung öffnen
- bei `SELL` / `REDUCE`: bestehende Verkaufs-/Transaktionsbuchung öffnen
- bei `REVIEW_SAVINGS_PLAN` / `KEEP_SAVINGS_PLAN`: bestehende Sparplanverwaltung öffnen, sofern für das Instrument vorhanden
- zusätzlich kann die vorhandene sichere Trade-Republic-Navigation angeboten werden

Die Navigation führt nur zum passenden bestehenden Flow. Sie markiert eine Aktion nicht automatisch als ausgeführt.

## 7. Status und Aktualität

Das Depot-Aktionscenter zeigt den zuletzt gespeicherten, belastbaren Advisor-/Action-Plan. Wenn noch kein Plan vorhanden ist, erscheint ein klarer Leerzustand statt erfundener Empfehlungen.

Wenn die App neue Depotdaten oder neue Advisor-Daten erhält, wird der Plan über den bestehenden Berechnungsweg aktualisiert. Ein UI-Neuaufbau darf den Plan nicht selbst verändern.

Die Einzelalarme und der depotweite Plan müssen dieselbe Instrumentidentität verwenden, damit keine Doppelpositionen oder widersprüchliche Empfehlungen durch unterschiedliche IDs entstehen.

## 8. UI-Struktur

Der neue Abschnitt soll kompakt und handlungsorientiert bleiben:

1. Überschrift: „Das solltest du jetzt mit deinem Depot machen“
2. kurze Zusammenfassung: Anzahl dringender Aktionen, geplantes Kaufbudget, bewusst gehaltenes Cash
3. priorisierte Aktionskarten
4. darunter bestehende Einzelalarme und Prognose-/Begründungsdetails

Jede Aktionskarte zeigt zuerst die konkrete Handlung und den Betrag. Kennzahlen dienen als Begründung und stehen visuell darunter. Damit bleibt die wichtigste Frage „Was soll ich tun?“ vor „Warum?“.

## 9. Technische Integration

Bevorzugte Integration:

- `ActionPlanEngine.kt` bleibt zentrale Recheninstanz.
- `PortfolioAdvisorStore.kt` bleibt Quelle des zuletzt gespeicherten Advisor-Plans.
- Eine kleine Mapping-/View-State-Schicht erzeugt aus `ActionPlan`, `PortfolioPosition`, `PortfolioAdvisorCandidate` und `InvestmentItem` die darstellbaren Aktionskarten.
- `AlertsScreen.kt` rendert den neuen depotweiten Abschnitt und nutzt bestehende Callbacks für Navigation/Buchung.
- `MainActivity.kt` stellt die vorhandenen Portfolio-, Investment- und Navigationsdaten bereit; dort wird keine neue Empfehlungslogik eingebaut.

Die Mapping-Schicht muss als reine Kotlin-Logik JVM-testbar sein. Compose-Code darf nicht die Priorisierung oder Budgetlogik enthalten.

## 10. Fehler- und Leerzustände

Folgende Fälle müssen explizit behandelt werden:

- kein gespeicherter Advisor-/Action-Plan → „Noch kein belastbarer Depot-Aktionsplan verfügbar.“
- Plan ohne offene Kapitalaktion → Halten/Cash/Sparplanstatus sachlich anzeigen, keine künstliche Kaufempfehlung erzeugen
- Instrumentdaten fehlen → Aktion mit Instrument-ID und Datenhinweis anzeigen, niemals still verwerfen, sofern der Plan selbst gültig ist
- unzuverlässige Kaufdaten → Kauf blockieren und Datenqualitätsmeldung anzeigen
- Positionsdaten fehlen bei Verkauf/Reduzieren → Aktion sichtbar lassen, aber Depotwert/G&V als unbekannt kennzeichnen

## 11. Version und Release

Android-Version für diese Erweiterung:

- `versionName = "2.4.5"`
- `versionCode = 65`
- Backend-Vertrag bleibt `2.1.0`, sofern die Umsetzung keinen neuen Backend-Endpunkt benötigt.
- Umsetzung direkt auf `main`, wie vom Nutzer für dieses Projekt freigegeben.
- Bestehende Release-Tags bleiben unveränderlich; `v2.4.5` wird als neuer In-App-Update-Release erzeugt.

## 12. Test- und Freigabevertrag

Die Umsetzung erfolgt testgetrieben.

Mindestens abzudecken:

- RED: depotweites Aktionscenter ist vor Implementierung nicht vorhanden
- Priorisierung `SELL > REDUCE > REVIEW_SAVINGS_PLAN > BUY_MORE > OPEN_POSITION > KEEP_SAVINGS_PLAN > HOLD_CASH`
- bestehende Depotpositionen innerhalb gleicher Klasse zuerst
- Budget wird ausschließlich aus dem `ActionPlan` übernommen
- kein doppeltes Verplanen von Sparplanbudget
- BUY/WATCH-Sperre bei unvollständiger Datenbasis
- konkrete deutsche Aktionsformulierung mit Betrag
- Navigation zu Kauf-/Verkaufs-/Sparplanfluss ohne Auto-Ausführung
- Leerzustand ohne erfundene Empfehlung
- Versionsverträge 2.4.5 / Code 65

Vor Veröffentlichung müssen auf exakt demselben finalen `main`-SHA erfolgreich sein:

- Android Contract Tests
- Android JVM Tests
- signed Android release APK
- APK-Signaturprüfung
- Live-Backend-Gate
- In-App-Publish für `v2.4.5`

## Nicht-Ziele

2.4.5 führt ausdrücklich nicht ein:

- automatische Trade-Republic-Orders
- Broker-Zugangsdaten oder private Broker-APIs
- eine zweite Empfehlungs-/Budgetengine
- frei erfundene Kaufbeträge
- automatische Ausführungsbestätigung durch Öffnen eines Links
- neue kurzfristige Daytrading- oder Hebelstrategien
