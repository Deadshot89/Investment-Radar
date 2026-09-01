# Hotfix 1.1.22

## Portfolio-Transaktionen
- Kaufhistorie bleibt vollständig erhalten.
- Verkäufe und Teilverkäufe können mit Datum, Erlös und Stückzahl erfasst werden.
- Verkaufspreis wird aus Erlös / Stückzahl automatisch berechnet.
- Verkäufe über den zum Verkaufsdatum vorhandenen Bestand werden blockiert.
- Änderungen/Löschen älterer Käufe werden blockiert, wenn dadurch ein späterer Verkauf ungedeckt würde.

## Performance
- verbleibende Stückzahl und verbleibende Kostenbasis nach gewichteter Durchschnittsmethode.
- realisierter Gewinn/Verlust aus Verkäufen.
- unrealisierter Gewinn/Verlust der offenen Position.
- Gesamt-G/V = realisiert + unrealisiert.
- Teilverkäufe und vollständig geschlossene Positionen werden unterstützt.

## Version
- Android versionCode 23
- Android versionName 1.1.22
- Backend unverändert: 1.1.20
