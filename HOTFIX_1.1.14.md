# Investment Radar 1.1.14

## Portfolio-Tracking

- Investierten Euro-Betrag pro Position erfassen und jederzeit ändern.
- Stückzahl/Anteile pro Position erfassen und jederzeit ändern.
- Durchschnittlichen Einstandskurs automatisch aus Betrag und Stückzahl berechnen.
- Aktuellen Positionswert bei verfügbarem EUR-Livekurs berechnen.
- Gewinn/Verlust in Euro und Prozent berechnen.
- Portfolio-Summe für investiertes Kapital, aktuellen Wert und Gewinn/Verlust.
- Beim ersten „Als gekauft markieren“ öffnet sich die Investitionserfassung.
- Im Radar und Portfolio gibt es „Investition bearbeiten“.
- Vorhandene `holding_ids` werden übernommen; keine bestehenden Portfolio-Markierungen gehen verloren.
- Portfolio-Daten bleiben bei normalen App-Updates erhalten.
- Bei fehlendem Livekurs bleibt die Performance auf „–“ statt falsche Werte zu berechnen.
- Bei Nicht-EUR-Kursen wird Gewinn/Verlust bewusst nicht mit dem EUR-Investitionsbetrag verrechnet.

## Unverändert

- Dark Design.
- Trade-Republic-App-Öffnung + ISIN-Zwischenablage aus 1.1.13.
- Timeout-/Retry-Fix aus 1.1.10.
- Firebase/Push und Backend-Schnittstellen.

Version: `1.1.14`, `versionCode 15`.
