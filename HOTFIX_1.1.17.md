# Investment Radar 1.1.17

- Kein kostenpflichtiger EU-Marktdatenzugang erforderlich.
- Xetra-ETFs nutzen weiterhin den kostenlosen Yahoo-Fallback in EUR.
- Yahoo-Fallback probiert `query1` und `query2` sowie Intraday- und Tagesintervalle, bevor ein Kurs als fehlend gilt.
- Jeder erfolgreiche Kurs wird serverseitig im vorhandenen Azure Storage als letzter erfolgreicher Kurs gespeichert.
- Fällt der Datenanbieter später kurzfristig aus, wird der letzte gespeicherte Kurs verwendet statt wieder `–` anzuzeigen.
- Cache-Kurse sind immer als `verzögert` und als `Cache` gekennzeichnet; sie werden niemals als Live-Kurs ausgegeben.
- US-Aktien über Twelve Data werden in der App ausdrücklich als `Live` gekennzeichnet, sofern der Provider den aktuellen Kurs liefert.
- Portfolio-Wert und Gewinn/Verlust können mit dem letzten verfügbaren EUR-ETF-Kurs weiter berechnet werden.
- Version: 1.1.17 / versionCode 18.

## Deployment
1. Inhalt dieses Update-Pakets in das bestehende Repository übernehmen.
2. GitHub Actions: `Deploy Backend` ausführen.
3. GitHub Actions: `Build Android APK` ausführen.
4. In der installierten App 1.1.16 auf `Update` drücken und 1.1.17 installieren.
