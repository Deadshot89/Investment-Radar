# Investment Radar Android App

**Version:** 2.5.51  
**versionCode:** 121

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
- Der zurückgezogene Legacy-iBond `custom-ibonds-dec-2026-usd` wird beim Laden aus Custom-Instrumenten, Depot und Watchlist entfernt; alte lokale Daten oder wiederhergestellte Backups können ihn dadurch nicht erneut in die aktive Depotbewertung bringen.

## Budget und Geldverwaltung

- Monatsbudget und Gesamt-Cash sind getrennte Größen.
- Kaufempfehlungen dürfen nur das noch freie Monatsbudget verwenden.
- Bestätigte Käufe reduzieren das Monatsbudget.
- Aktive Sparpläne werden vor zusätzlichen Käufen aus dem Monatsbudget reserviert und nicht als freies Cash ausgewiesen.
- Sparpläne mit REDUZIEREN-/VERKAUFEN-Konflikt werden zur Prüfung markiert und nicht still vom zusätzlichen Kaufbudget abgezogen.
- Bestehende Positionen ab 40 % Anteil am vollständig bewertbaren Depot erhalten kein zusätzliches Advisor-Kaufbudget; bei unvollständigen Depotwerten wird keine Konzentration angenommen.
- Manuell gesetzte Monatsbudgets werden centgenau gespeichert und in Folgemonate übernommen.
- Verkaufserlöse werden erfasst, aber privat verwendet und erhöhen App-Cash sowie Kaufbudget nicht.
- Der Geldbedarf-Flow kann vorhandenes App-Cash und private Verkaufserlöse getrennt auf einen Bedarf anrechnen.
- Geldaktions-Karten stapeln auf kompakten Displays Instrument/Begründung und Cash-Wirkung, damit lange Werte die Inhalte nicht zusammendrücken.

## Empfehlungen und Dezimaleingaben

### Änderung in 2.5.51

Depotpositionen können direkt und nach Bestätigung als verkauft markiert werden; dabei bleiben Käufe, Verkäufe und Einstand erhalten, ohne einen Verkaufserlös oder App-Cash zu erfinden. Die Markierung lässt sich direkt wieder aufheben. Die endgültige Depotentfernung oder Löschung eigener Werte erfordert nun eine zusätzliche Bestätigung, die den Verlust lokal gespeicherter Positionshistorie offenlegt.

### Änderung in 2.5.50

Bei mehreren Depot-Screenshots werden widersprüchliche Gewinn-/Verlust-, Prozent- und Stückzahlangaben auch dann als Konflikt angezeigt, wenn im ersten Screenshot der jeweilige Wert fehlt. Solche Werte werden nicht ungeprüft übernommen.

### Änderung in 2.5.49

Der Depotabgleich kann jetzt mehrere Trade-Republic-Screenshots lokal auf dem Android-Gerät auswerten. Die gebündelte ML-Kit-OCR sendet weder Screenshots noch OCR-Rohtext an das Backend. Vor der Übernahme werden Zuordnung, Konflikte, neue und fehlende Positionen geprüft. Fehlende Positionen können beibehalten, ohne erfundenen Verkaufserlös als verkauft markiert oder ausdrücklich gelöscht werden. Verkaufte Positionen bleiben historisch nachvollziehbar, zählen aber nicht mehr zum aktiven Depot.


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

### Änderung in 2.5.40

Der Portfolio-Advisor berücksichtigt jetzt die bestehende Depotkonzentration bei zusätzlichen Kaufempfehlungen. Eine Position mit mindestens 40 % Anteil am vollständig bewertbaren Depot erhält kein weiteres Monatsbudget. Fehlt für mindestens einen berücksichtigten Bestand ein belastbarer Wert, bleibt die Konzentrationssperre aus, statt eine Gewichtung zu erfinden.

### Änderung in 2.5.41

Die kompakte Depotkarte auf dem Dashboard zeigt bei einem unvollständig bewertbaren Depot keine technischen Custom-/iBond-IDs und keine globale rote „Prüfen“-Warnung mehr. Der Gesamtwert bleibt korrekt als unvollständig gekennzeichnet und ein berechenbarer Teil bleibt sichtbar; die konkrete Datenlücke wird weiterhin direkt bei der betroffenen Depotposition angezeigt.

### Änderung in 2.5.42

Die Konzentrationsberechnung verwendet für vollständig bewertbare Depots den vollständigen Depotwert als Nenner, damit einzelne Positionsgewichte nicht durch einen zu kleinen Teilwert künstlich aufgebläht werden.

### Änderung in 2.5.43

Der alte iBond-Datensatz `custom-ibonds-dec-2026-usd` wird nicht mehr nur im Dashboard verborgen, sondern beim Laden physisch aus den persistenten Custom-Instrumenten, Depot-IDs und Watchlist-Daten entfernt. Speichern bzw. erneutes Hinzufügen dieser zurückgezogenen ID wird blockiert. Damit greift die Bereinigung auch bei bestehenden Installationen und nach einer Wiederherstellung alter Backups.

### Änderung in 2.5.44

Aktien- und ETF-Positionen zeigen den aktuell für die Depotbewertung verwendeten EUR-Stückkurs jetzt direkt im Portfolio-Dashboard und in der kompakten Depotkarte. Der Kurs wird aus derselben belastbaren Quelle übernommen, die bereits für die Positionsbewertung verwendet wird; Festzins-Werte bleiben unverändert als Anlagewert dargestellt und bei fehlendem Kurs wird kein Wert erfunden.

### Änderung in 2.5.48

Importierte Depot-Snapshots unterscheiden jetzt sauber zwischen fehlendem aktuellem Kurs und fehlender Stückzahl. Ist ein belastbarer EUR-Kurs vorhanden, aber noch keine Stückzahl erfasst, zeigt das Depot ausdrücklich „Stückzahl fehlt“ statt fälschlich „Kurs fehlt“. Die Kennzahl für fehlende Kurse zählt nur noch tatsächlich fehlende Kurse; die Vollständigkeit des Depotwerts bleibt separat davon abhängig, ob ein aktueller Wert berechenbar ist. Historische Snapshotwerte werden weiterhin nicht als heutiger Depotwert verwendet.

### Änderung in 2.5.47

Live-Dashboard und heutige Empfehlung bewerten Depotpositionen jetzt mit demselben EUR-vergleichbaren Kurs wie die Portfolio-Berechnung. Bei Fremdwährungswerten wird der native USD-/sonstige Kurs nicht mehr fälschlich als Euro verwendet; fehlt eine belastbare EUR-Umrechnung, wird kein EUR-Depotwert erfunden.

### Änderung in 2.5.46

Unzuverlässige Advisor-Daten werden nun auch in Portfolio, Radar sowie Detail- und Historienansicht nicht mehr als „Halten · Daten prüfen“ ausgegeben. Diese Flächen zeigen stattdessen ausdrücklich „Keine belastbare Bewertung“. Damit sind die sichtbare UI und die bereits in 2.5.45 korrigierte Benachrichtigungslogik fachlich konsistent.

### Änderung in 2.5.45

Benachrichtigungen des Advisors geben bei fehlender oder unzuverlässiger Datenbasis nicht mehr fälschlich „HALTEN“ als Entscheidung aus. Stattdessen erscheint ausdrücklich „KEINE BELASTBARE BEWERTUNG“ mit Datenwarnung. Echte, belastbare HALTEN-, NACHKAUFEN- und VERKAUFEN-Signale bleiben unverändert.

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
