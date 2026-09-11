# Investment Budget Ledger Design

## Ziel
Das bisherige statische Monatsbudget wird durch einen echten Geldkreislauf ersetzt. Ein bestätigter Kauf reduziert verfügbares Budget; ein Verkauf erhöht es wieder; zusätzliche Einzahlungen wie Wechselgeld erhöhen das verfügbare Kapital. Empfehlungen reservieren Geld, verändern den Ist-Bestand aber erst nach Bestätigung.

## Architekturentscheidung nach Bestandsanalyse
Die App besitzt bereits ein funktionierendes Depot-Ledger in `PortfolioPosition`/`PortfolioStore`: Käufe und Verkäufe werden einzeln gespeichert, gewichteter Einstand, Reststückzahl und realisierter Gewinn werden daraus berechnet. Dieses Depot-Ledger bleibt die **einzige Quelle für Stückzahlen und Einstandswerte**. Es wird kein zweites dauerhaftes Trade-Ledger daneben aufgebaut.

Neu kommt ein **Budget-Journal** hinzu. Jede budgetrelevante Bewegung verweist über eine Ereignis-ID auf den ausgeführten Portfolio-Kauf/-Verkauf. Dadurch bleiben Geldverwaltung und Depotbestand synchron, ohne dieselbe Transaktion doppelt fachlich zu berechnen.

## Fachmodell
- **Budgetkonto**: monatliche Einzahlung, zusätzliche Einzahlungen, verfügbare Mittel, reservierte Mittel.
- **Depot-Ledger (bestehend)**: `PortfolioPurchase`/`PortfolioSale` bleibt Source of Truth für Stückzahl, Cost Basis und realisierten G/V.
- **Budget-Journal (neu)**: Einzahlungen sowie Kauf-Abbuchungen und Verkauf-Gutschriften mit stabiler Event-ID und Quelle (`MANUAL`, `SPARPLAN`, `WECHSELGELD`, `EMPFEHLUNG`).
- **Empfehlung**: kann `KAUFEN`, `NACHKAUFEN`, `HALTEN`, `REDUZIEREN`, `VERKAUFEN` auslösen und optional einen Betrag reservieren.
- **Ausführung**: erst `Kauf ausgeführt` bzw. `Verkauf ausgeführt` schreibt Portfolio-Transaktion und die korrespondierende Budgetbewegung.

## Regeln
1. Monatsbudget wird nicht nach jedem Start neu als komplett verfügbar dargestellt.
2. Kauf reduziert `availableEur`; Verkaufserlös erhöht `availableEur`.
3. Wechselgeld/Bonus ist eine zusätzliche Einzahlung und erhöht das Budget unabhängig vom Monatsbudget.
4. Empfehlung reserviert optional Geld, verändert aber investiertes Kapital erst nach Ausführung.
5. Eine Kaufbestätigung erzeugt genau einen Portfolio-Kauf und genau eine verknüpfte Budget-Abbuchung; gleiche Event-ID verhindert Doppelbuchungen.
6. Teilverkäufe bleiben über das bestehende Portfolio-Ledger erlaubt; Überverkäufe werden dort abgewiesen.
7. Ein Wertpapier bleibt über seine bestehende stabile Asset-ID eine Position; bekannte Instrumente dürfen bei Nachkäufen nicht dupliziert werden.
8. Aktueller Marktwert bleibt kursabhängig; Einstand und Stückzahl stammen ausschließlich aus `PortfolioPosition`.
9. Das Budget-Journal darf historische Depotkäufe vor Aktivierung der Budgetverwaltung nicht rückwirkend vom neuen Monatsbudget abziehen.

## MSCI-Referenzfall
Bestehende SPYI-Position: 4.339524 Anteile. Wechselgeldkauf am 09.09.2026: 0.560698 Anteile. Nach `upsertPurchase` muss dieselbe Position `spyi` 4.900222 Anteile enthalten. Der Wechselgeldbetrag wird zusätzlich als Budget-Einzahlung erfasst und der Kauf anschließend als Budget-Abbuchung; dadurch belastet er nicht das normale Monatsbudget.

## UI-Ziel
Neue Budget-Zusammenfassung: `Monatsbudget`, `Zusätzlich`, `Investiert`, `Verfügbar`, `Reserviert`. Empfehlungen erhalten Aktionen `Kauf ausgeführt` / `Verkauf ausgeführt`; zusätzlich gibt es eine Budget-Historie für Einzahlungen, Käufe und Verkäufe. `Investiert` wird aus den aktiven Portfolio-Cost-Bases berechnet, nicht aus der Summe aller Budget-Abbuchungen.

## Migration
Vorhandene Depot-Snapshots und Käufe/Verkäufe bleiben unverändert lesbar. Die Budgetverwaltung beginnt mit einem definierten Startsaldo/Monatsbudget und erfasst ab dann neue Ausführungen. Bestehende Nutzer verlieren keine gespeicherten Positionen und bereits vorhandene Portfolio-Transaktionen werden nicht dupliziert.
