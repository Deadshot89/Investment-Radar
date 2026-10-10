# Investment Radar Android App

**Version:** 2.5.39  
**versionCode:** 109

Die verbindlichen Android-Versionsmetadaten werden in `android/app/build.gradle.kts` gepflegt. Für jede produktiv auszuliefernde App-Änderung müssen `versionName` und `versionCode` erhöht werden, damit bestehende Installationen das In-App-Update eindeutig als neuer erkennen.

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
- Aktive Sparpläne werden vor zusätzlichen Käufen aus dem Monatsbudget reserviert und nicht als freies Cash ausgewiesen.
- Sparpläne mit REDUZIEREN-/VERKAUFEN-Konflikt werden zur Prüfung markiert und nicht still vom zusätzlichen Kaufbudget abgezogen.
- Manuell gesetzte Monatsbudgets werden centgenau gespeichert und in Folgemonate übernommen.
- Verkaufserlöse werden erfasst, aber privat verwendet und erhöhen App-Cash sowie Kaufbudget nicht.
- Der Geldbedarf-Flow kann vorhandenes App-Cash und private Verkaufserlöse getrennt auf einen Bedarf anrechnen.
- Geldaktions-Karten stapeln auf kompakten Displays Instrument/Begründung und Cash-Wirkung, damit lange Werte die Inhalte nicht zusammendrücken.

## Empfehlungen und Dezimaleingaben

Empfehlungszeilen zeigen Instrument, Signal und Betrag getrennt. Über **Bearbeiten** wird die bestehende Transaktionsmaske geöffnet:

- empfohlener Betrag wird übernommen
- Stückzahl wird bei vorhandenem EUR-Kurs vorbefüllt
- Kauf/Verkauf wird passend zur Empfehlung geöffnet
- der Nutzer kann die Werte vor der Bestätigung ändern
- gruppierte Dezimaleingaben wie `1.000,50` und `1,000.50` werden ohne Größenordnungsfehler normalisiert
- manuell ergänzte Depot-Stückzahlen verwenden dieselbe locale-sichere Dezimallogik

### Änderung in 2.5.37

Der Stückzahl-Dialog im Depot verwendet jetzt ebenfalls den zentralen Dezimalparser. Gruppierte deutsche und internationale Eingaben werden damit konsistent zu den Geldfeldern verarbeitet.

### Änderung in 2.5.38

Manuell gespeicherte Trade-Republic-Links werden beim Speichern und Laden auf vertrauenswürdige HTTPS-Hosts von `traderepublic.com` begrenzt. Neue oder bearbeitete Custom-Werte werden anschließend aus dem sanitisierten Store neu in den UI-State geladen. Ungültige, unsichere oder fremde Links werden verworfen und nicht als Trade-Republic-Link im UI angeboten.

### Änderung in 2.5.39

Der Portfolio-Advisor behandelt geplante Sparpläne jetzt konsistent mit dem Action-Plan: beibehaltene Sparpläne reservieren Budget, tauchen aber nicht erneut als freies Cash auf. Sparpläne, die wegen REDUZIEREN oder VERKAUFEN geprüft werden müssen, blockieren dagegen nicht still das Budget einer gültigen zusätzlichen Kaufempfehlung.

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
