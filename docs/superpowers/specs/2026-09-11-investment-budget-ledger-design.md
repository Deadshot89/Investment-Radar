# Investment Budget Ledger Design

## Ziel
Das bisherige statische Monatsbudget wird durch einen echten Geldkreislauf ersetzt. Ein bestätigter Kauf reduziert verfügbares Budget; ein Verkauf erhöht es wieder; zusätzliche Einzahlungen wie Wechselgeld erhöhen das verfügbare Kapital. Empfehlungen reservieren Geld, verändern den Ist-Bestand aber erst nach Bestätigung.

## Fachmodell
- **Budgetkonto**: monatliche Einzahlung, zusätzliche Einzahlungen, verfügbare Mittel, reservierte Mittel.
- **Trade-Ledger**: jeder Kauf/Verkauf ist eine eigene Buchung mit Asset-ID/ISIN, Stückzahl, Preis, Betrag, Datum und Quelle (`MANUAL`, `SPARPLAN`, `WECHSELGELD`, `EMPFEHLUNG`).
- **Empfehlung**: kann `KAUFEN`, `NACHKAUFEN`, `HALTEN`, `REDUZIEREN`, `VERKAUFEN` auslösen und optional einen Betrag reservieren.
- **Ausführung**: erst `Kauf ausgeführt` bzw. `Verkauf ausgeführt` schreibt Trade und Budgetbewegung.
- **Depotposition**: wird aus Trades aggregiert; mehrere Käufe derselben ISIN bleiben eine Position.

## Regeln
1. Monatsbudget wird nicht nach jedem Start neu als komplett verfügbar dargestellt.
2. Kauf reduziert `availableEur`; Verkaufserlös erhöht `availableEur`.
3. Wechselgeld/Bonus ist eine zusätzliche Einzahlung und erhöht das Budget unabhängig vom Monatsbudget.
4. Empfehlung reserviert optional Geld, aber verändert den Ist-Kontostand erst nach Ausführung.
5. Eine Kaufbestätigung erzeugt genau eine Trade-Buchung und löst die Reservierung auf.
6. Teilverkäufe sind erlaubt; Verkäufe dürfen die gehaltene Stückzahl nicht überschreiten.
7. Aggregation erfolgt primär über ISIN, sonst stabile Asset-ID.
8. Aktueller Marktwert bleibt kursabhängig; investiertes Kapital und Buy-in stammen aus dem Ledger.

## MSCI-Referenzfall
Bestehender Kauf: 4.339524 Anteile. Wechselgeldkauf am 09.09.2026: 0.560698 Anteile. Aggregierte Stückzahl muss 4.900222 ergeben. Die App darf keine zweite MSCI-Position anlegen.

## UI-Ziel
Neue Budget-Zusammenfassung: `Monatsbudget`, `Zusätzlich`, `Investiert`, `Verfügbar`, `Reserviert`. Empfehlungen erhalten Aktionen `Kauf ausgeführt` / `Verkauf ausgeführt`; zusätzlich gibt es eine Budget-Historie für Einzahlungen, Käufe und Verkäufe.

## Migration
Vorhandene Depot-Snapshots bleiben lesbar. Ledger-Daten haben Vorrang, sobald für eine Position Trades existieren. Alte Nutzer verlieren keine gespeicherten Positionen.
