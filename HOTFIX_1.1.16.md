# Investment Radar 1.1.16

- Portfolio-Performance wird jetzt in EUR berechnet.
- USD-Kurse werden serverseitig über den aktuellen USD/EUR-Wechselkurs umgerechnet.
- Originalkurs und Originalwährung bleiben sichtbar; Verkaufssignale arbeiten weiterhin mit dem unveränderten Originalkurs.
- Xetra-ETFs SPYI, IS3S und IS3Q erhalten bei fehlender Twelve-Data-Abdeckung einen `.DE`-Kursfallback in EUR.
- Verzögerte ETF-Kurse werden in der App als verzögert gekennzeichnet.
- Aktueller Positionswert sowie Gewinn/Verlust in € und % werden automatisch gefüllt, sobald Kursdaten vorhanden sind.
- Fehlende Kurs- oder FX-Daten werden mit konkreter Ursache angezeigt.
- Version: 1.1.16 / versionCode 17.

## Deployment
1. Inhalt dieses Update-Pakets in das Repository übernehmen.
2. GitHub Actions: `Deploy Backend` ausführen, weil Backend-Dateien geändert wurden.
3. GitHub Actions: `Build Android APK` ausführen.
4. In App 1.1.15 anschließend `Update` drücken, um 1.1.16 zu installieren (sofern der Self-Updater auf das Release zugreifen kann).
