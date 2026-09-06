# Investment Radar 2.3 – Portfolio-Berater Design

## Ziel
Investment Radar 2.3 erweitert den bestehenden 2.2-Advisor zu einem handlungsorientierten Portfolio-Berater. Er bewertet bestehende Depotpositionen und neue Radar-Chancen gemeinsam, verteilt ein monatliches Budget von 100 € nur auf belastbare Chancen, darf Cash zurückhalten und darf begründete Umschichtungen empfehlen. Orders werden niemals automatisch ausgeführt.

## Festgelegtes Profil
- Auswahluniversum: bestehendes Depot + neue Werte aus dem Radar.
- Risikoprofil: ausgewogen.
- Monatsbudget: 100 € als Standardwert; es muss nicht vollständig investiert werden.
- Cash darf zurückgehalten werden, wenn Chancen nicht ausreichend stark oder Daten nicht belastbar sind.
- Bestehende Positionen dürfen zusätzlich mit Reduzieren/Verkaufen zur Umschichtung vorgeschlagen werden.
- Depotgewicht allein bestimmt niemals die Handlungsempfehlung und erzeugt insbesondere keinen automatischen Verkauf.

## Beraterbewertung
Jeder investierbare Kandidat wird aus Qualität, Bewertung, Wachstum, Momentum, Prognose, Risiko und Datenqualität beurteilt. Bestehende Depotwerte und neue Radar-Kandidaten verwenden dieselbe Grundbewertung. Instrumenttypen bleiben typgerecht: Aktienmetriken dürfen nicht als erfundene ETF- oder Anleihemetriken eingesetzt werden.

Für bestehende Positionen sind die Aktionen Nachkaufen, Halten, Reduzieren und Verkaufen zulässig. Neue Radar-Kandidaten erhalten Neu aufnehmen oder Nicht aufnehmen. Jede belastbare Empfehlung zeigt die wichtigsten Gründe und Risiken sowie eine Konfidenz. Bei unzureichender Datenqualität wird keine künstlich genaue Empfehlung erzeugt.

## Prognosen
Für jeden geeigneten Kandidaten werden 1-, 3-, 6- und 12-Monats-Horizonte dargestellt. Jeder belastbare Horizont enthält einen Zielbereich statt eines scheinpräzisen Einzelziels, die erwartete Veränderung, Konfidenz und zentrale Treiber. Reicht die Datenbasis nicht, wird kein Zielbereich erfunden.

6 und 12 Monate tragen die strategische Handlung. 1 und 3 Monate wirken nur moderat als Timing-Faktor auf die Höhe einer Investition. Kurzfristige Schwäche darf daher beispielsweise einen Nachkauf verkleinern, aber nicht allein eine langfristig belastbare Investmentthese in Verkaufen drehen.

## Monatsbudget und Cash
Der Budgetplan betrachtet vorhandene Depotwerte und neue Radar-Chancen gemeinsam. Sehr starke belastbare Chancen erhalten höhere Beträge, gute Chancen kleinere Beträge. Halten erhält grundsätzlich kein neues Kapital, außer der Kandidat liegt knapp unter Nachkaufen und es fehlen bessere belastbare Alternativen. Reduzieren, Verkaufen und unzuverlässige Kandidaten erhalten kein neues Kapital.

Der Plan darf jeden Restbetrag als Cash ausweisen. Die Summe aus vorgeschlagenen Käufen und Cash muss exakt dem Monatsbudget entsprechen. Beträge werden in verständlichen Euro-Schritten ausgegeben und dürfen das Budget niemals überschreiten.

Bereits geplante Sparplanraten werden in die Monatsallokation einbezogen. Der Berater darf nicht gleichzeitig einen Wert reduzieren/verkaufen und zusätzlich einen fortlaufenden Sparplan darauf als sinnvolle Kapitalzufuhr behandeln. In einem solchen Konflikt wird der Nutzer sichtbar darauf hingewiesen, den Sparplan zu prüfen; die App ändert den Sparplan nicht selbstständig.

## Umschichtungen
Eine Umschichtung wird nur vorgeschlagen, wenn eine bestehende Position belastbar schwächer geworden ist und eine deutlich bessere, belastbare Alternative existiert. Kleine Score-Unterschiede reichen nicht.

Reduzieren bedeutet: Investmentthese noch intakt, aber Chance/Risiko ist schwächer und ein Teil des Kapitals kann sinnvoller eingesetzt werden. Verkaufen bedeutet: Investmentthese deutlich verschlechtert oder Risiken überwiegen. Die App zeigt Quelle, vorgeschlagenen Betrag, Ziel und Begründung. Zusätzlich kann sie eine nicht zwingende Alternative wie Halten, aber kein weiteres Kapital anzeigen.

Keine Order und kein Sparplan wird automatisch ausgeführt oder verändert.

## Stabilität und Benachrichtigungen
Normale Score-Schwankungen müssen sich bestätigen, bevor sie eine stärkere Aktion oder Umschichtung auslösen. Eine klar belegte fundamentale Verschlechterung darf unmittelbar reagieren. Die bestehende 2.2-Änderungslogik wird erweitert statt durch tägliche Push-Flut ersetzt.

Benachrichtigungen entstehen nur bei materiellen Änderungen: relevante Handlungsänderung, neu belastbare starke Chance, belastbare Reduzieren-/Verkaufen-Empfehlung, wesentliche Umschichtung oder Verlust der Verlässlichkeit. Identische tägliche Ergebnisse erzeugen keine erneute Benachrichtigung.

## Oberfläche
Oben im Depot erscheint eine zentrale Karte „Was soll ich jetzt tun?“. Sie fasst den aktuellen Monatsplan zusammen, beispielsweise „55 € investieren · 25 € umschichten · 20 € Cash halten“, und listet die wichtigsten Aktionen priorisiert.

Depot- und Detailansichten zeigen Handlung, Konfidenz, Gründe, Risiken und Prognosen für 1/3/6/12 Monate. Neue Radar-Chancen werden als mögliche Neuaufnahme kenntlich gemacht.

Eine Änderungshistorie zeigt nachvollziehbar, wann sich eine Empfehlung geändert hat und welche belastbaren Gründe die Änderung ausgelöst haben. Die Historie ist begrenzt und darf nicht unkontrolliert wachsen.

## Daten- und Sicherheitsregeln
- Keine erfundenen Kurse, Zielkurse, Kennzahlen, Ticker, ISINs oder Trade-Republic-Produkt-IDs.
- Fehlende oder veraltete Daten führen zu einer klaren nicht-belastbaren Darstellung statt synthetischer Nullwerte.
- Private-Equity-Sparpläne ohne verifizierte Zuordnung bleiben für automatische Depotbuchung blockiert.
- Trade Republic wird nur über verifizierte/sichere Navigation geöffnet; keine private API, kein PIN/Login-Scraping.
- Der Berater ist eine Entscheidungsunterstützung; Ausführung bleibt beim Nutzer.

## Technische Richtung
Die bestehende 2.2-Pipeline bleibt Grundlage: AdvisorInputFactory, AdvisorEngine, ForecastEngine, AdvisorStore, DailyAnalysisCoordinator, SavingsPlanStore und lokale Benachrichtigungen werden erweitert. Neue Logik für Multi-Horizon-Prognosen, Kandidatenvergleich, Budgetallokation, Umschichtung und Stabilität wird als pure Kotlin-Domänenlogik implementiert und separat getestet. UI und WorkManager konsumieren diese Ergebnisse, statt Bewertungsregeln selbst zu duplizieren.

## Akzeptanzkriterien
1. Jede geeignete Depotposition und Radar-Chance kann typgerecht bewertet werden, ohne fehlende Werte zu erfinden.
2. 1/3/6/12-Monats-Prognosen werden nur bei belastbarer Datenbasis als Zielbereiche angezeigt.
3. 1/3 Monate beeinflussen Investitionshöhe moderat; 6/12 Monate dominieren die strategische Aktion.
4. Der 100-€-Plan überschreitet nie das Budget und darf einen Cash-Rest enthalten.
5. Sparplanraten werden bei der Budgetempfehlung berücksichtigt.
6. Reduzieren/Verkaufen erhält kein neues Kapital.
7. Umschichtung erfordert eine deutlich bessere belastbare Alternative und wird nie automatisch ausgeführt.
8. Kleine tägliche Schwankungen erzeugen weder hektische Aktionswechsel noch Push-Spam.
9. Die zentrale „Was soll ich jetzt tun?“-Karte und die Änderungshistorie sind im Depot sichtbar.
10. Bestehende 2.2-Funktionen, Navigation, Sparpläne, Release-Sicherheit und Trade-Republic-Sicherheitsregeln bleiben regressionsfrei.
