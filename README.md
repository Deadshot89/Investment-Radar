# Investment Radar Live 1.1.0

Android-Live-App mit Push-Benachrichtigungen fuer Aktien und ETFs.

## Enthalten

- Android-App (Kotlin + Jetpack Compose)
- Live-Dashboard mit Marktampel, Top-Pick und 100-EUR-Plan
- Radar fuer Aktien + ETFs
- Reiter **Depot**: gekaufte Positionen markieren
- Verkaufs-/Pruef-Pushs nur fuer markierte Depotpositionen
- Live-Kurse ueber einen serverseitigen Marktdaten-Provider
- Trade-Republic-Suchname und ISIN
- FCM Push-Benachrichtigungen
- Alarmverlauf auf dem Geraet
- Azure Functions Backend mit 15-Minuten-Pruefung
- Google-Sheet-Synchronisation fuer Empfehlungen und Alarmstatus
- GitHub Actions zum APK-Build und Backend-Deploy

## Architektur

Google Investment-Radar -> Azure Functions -> Android App
                           -> Twelve Data Kurse
                           -> Firebase Cloud Messaging -> Depot-Push
                           -> Azure Blob Alarmzustand

Geheime API-Schluessel liegen nur im Backend bzw. als GitHub/Azure Secrets.

## Schnellstart

1. `START_HIER.md` lesen.
2. Firebase-Projekt anlegen.
3. Azure Function App anlegen und Settings setzen.
4. Repo nach GitHub hochladen.
5. Backend-Deploy ausfuehren.
6. Android-APK bauen und installieren.
7. In der App gekaufte Werte mit **Als gekauft markieren** ins Depot aufnehmen.

## Wichtiger Hinweis

Die App fuehrt keine Orders aus. Signale sind Entscheidungshilfen und Warnungen, keine Renditegarantie und keine automatische Anlageberatung.
