# Investment Radar Android App

**Version:** 2.5.24  
**versionCode:** 94

## Technik

- Kotlin
- Jetpack Compose
- minSdk 23
- targetSdk 36
- compileSdk 36
- Java 17
- Gradle 8.13
- Kotlin 2.3.21
- Compose BOM 2026.06.00
- Firebase Cloud Messaging
- WorkManager
- signierte Release-APK mit dauerhaftem Keystore

## Laufzeitverhalten

- Live-Daten werden beim Start geladen.
- Solange die App im Vordergrund aktiv ist, wird ungefähr alle 60 Sekunden aktualisiert.
- Parallele Refreshes werden verhindert.
- Schlägt ein Refresh fehl und sind bereits Daten geladen, bleiben die vorhandenen Daten sichtbar und es erscheint ein Aktualisierungshinweis.
- Depotdaten werden ausschließlich aus den gespeicherten Nutzerdaten geladen; es werden keine fest eingebauten Altbestände automatisch erzeugt.

## Budget und Geldverwaltung

- Monatsbudget und Gesamt-Cash sind getrennte Größen.
- Kaufempfehlungen dürfen nur das noch freie Monatsbudget verwenden.
- Bestätigte Käufe reduzieren das Monatsbudget.
- Verkaufserlöse werden erfasst, aber privat verwendet und erhöhen App-Cash sowie Kaufbudget nicht.
- Der Geldbedarf-Flow kann vorhandenes App-Cash und private Verkaufserlöse getrennt auf einen Bedarf anrechnen.

## Empfehlungen

Empfehlungszeilen zeigen Instrument, Signal und Betrag getrennt. Über **Bearbeiten** wird die bestehende Transaktionsmaske geöffnet:

- empfohlener Betrag wird übernommen
- Stückzahl wird bei vorhandenem EUR-Kurs vorbefüllt
- Kauf/Verkauf wird passend zur Empfehlung geöffnet
- der Nutzer kann die Werte vor der Bestätigung ändern

## Push

Firebase Push wird über das Backend ausgelöst. Ohne vollständige Firebase-Konfiguration kann die App starten, Push ist dann jedoch nicht produktiv verfügbar.

## Build und Release

Der GitHub-Workflow **Build Android APK** prüft vor einer produktiven Veröffentlichung:

- ausgewählte Regressionstests
- Build- und Signierkonfiguration
- JVM Unit Tests
- signierte Release-APK
- APK-Signatur
- Live-Backend-Kompatibilität
- In-App-Release auf `main`

Zusätzlich existieren getrennte Contract- und instrumentierte UI-Gates.

## Signierung

GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Der Produktions-Keystore darf nicht ersetzt werden, wenn bestehende Installationen updatefähig bleiben sollen.
