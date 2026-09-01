# Investment Radar 1.1.18

## Änderung
- USD/EUR-Umrechnung nutzt Twelve Data weiterhin als erste Quelle.
- Wenn Twelve Data keinen FX-Kurs liefert oder kein API-Key vorhanden ist, nutzt das Backend automatisch den kostenlosen ECB-Referenzkurs.
- Der ECB-Kurs wird von EUR-Basis korrekt in Währung→EUR umgerechnet.
- Erfolgreiche FX-Kurse werden in Azure Storage als `fx-cache.json` gespeichert.
- Bei einem temporären Provider-Ausfall wird der letzte erfolgreiche FX-Kurs aus dem Cache genutzt.
- Die App zeigt die verwendete FX-Quelle transparent an, z. B. `FX ECB · Tageskurs` oder `FX Cache · ECB · verzögert`.
- Dadurch können aktueller EUR-Positionswert sowie Gewinn/Verlust auch bei fehlendem Twelve-Data-FX-Kurs berechnet werden.
- Keine zusätzlichen API-Keys und keine kostenpflichtigen EU-Marktdaten erforderlich.

## Version
- versionCode: 19
- versionName: 1.1.18

## Deployment
1. Dateien in das bestehende Repository übernehmen.
2. `Deploy Backend` ausführen.
3. `Build Android APK` ausführen.
4. In Investment Radar auf `Update` drücken und 1.1.18 installieren.
