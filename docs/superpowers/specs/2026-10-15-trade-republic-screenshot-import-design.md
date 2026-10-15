# Investment Radar – Trade-Republic-Screenshot-Import

**Datum:** 15.10.2026  
**Status:** Design zur Freigabe  
**Zielplattform:** Android  
**Scope:** ausschließlich Investment-Radar; keine Daten, Komponenten oder Logik aus KPI Logistik verwenden.

## 1. Ziel

Investment Radar erhält im Bereich **„Mein Depot“** einen lokalen Screenshot-Import für Trade Republic. Der Nutzer kann einen oder mehrere Trade-Republic-Screenshots auswählen, die App liest die sichtbaren Depotinformationen ausschließlich auf dem Smartphone aus, gleicht sie mit dem gespeicherten Investment-Radar-Depot ab und zeigt alle Änderungen vor dem Speichern zur Prüfung an.

Der Import ist ein **Depotabgleich**, kein Transaktionsimport. Ein Screenshot darf niemals automatisch einen Kauf, Verkauf, Cash-Zugang oder eine Budgetänderung erzeugen.

Erfolg bedeutet:

- mehrere Trade-Republic-Screenshots können in einem Aktualisierungsvorgang verarbeitet werden;
- Screenshots und OCR-Daten verlassen das Gerät nicht;
- bestehende Positionen können sicher aktualisiert werden;
- neue Positionen können nach manueller Bestätigung angelegt werden;
- nicht gefundene Positionen können behalten, als verkauft markiert oder gelöscht werden;
- Kauf-/Verkaufshistorien und Budgetdaten werden durch reine Snapshot-Aktualisierungen nicht verfälscht;
- erst nach einer finalen Bestätigung werden Daten gespeichert;
- ein fehlgeschlagener Speichervorgang hinterlässt keinen teilweise aktualisierten Depotstand.

## 2. Nicht-Ziele

Die erste Version unterstützt bewusst **nur Trade Republic**. Sie ist kein universeller Broker-Importer und kein PDF-/CSV-Importer.

Nicht Teil dieses Features:

- Server-OCR oder Upload von Depot-Screenshots an ein Backend;
- automatische Erzeugung realer Kauf- oder Verkaufstransaktionen aus einem Depot-Screenshot;
- Erfindung eines Verkaufspreises, Verkaufserlöses, Kaufpreises oder einer Stückzahl, wenn diese nicht eindeutig im Screenshot vorliegt;
- automatische Löschung oder automatische Verkaufsmarkierung, nur weil eine Position auf einem Screenshot fehlt;
- Änderung von Sparplänen, monatlichem Kaufbudget oder globalem Budget-Journal durch einen reinen Screenshot-Abgleich;
- Performanceberechnungen „seit Verkauf“, solange kein echter Verkaufskurs und kein echtes Verkaufsdatum vorhanden sind.

## 3. Einstieg und Benutzerfluss

Im bestehenden Portfolio-Dashboard erscheint oben die Aktion **„Depot aktualisieren“**.

Der Flow besteht aus vier Schritten:

### 3.1 Screenshots auswählen

- Android-Mehrfachauswahl für Bilder.
- Vorschau der ausgewählten Bilder und Anzahl.
- Einzelne Bilder können vor der Analyse wieder entfernt werden.
- Die App persistiert die ausgewählten Bilder nicht dauerhaft.

### 3.2 Lokal erkennen

- On-Device-Texterkennung mit einem gebündelten lokalen Text-Recognizer für lateinische Schrift.
- Kein eigener Server-Endpunkt für Bilder oder OCR-Text.
- Fortschrittsanzeige pro Bild, z. B. „3 von 5 Screenshots analysiert“.
- OCR-Fehler eines Bildes dürfen den gesamten Import nicht zerstören.

### 3.3 Abgleich prüfen

Jede erkannte oder erwartete Depotposition erhält genau einen Prüfstatus:

- **Sicher erkannt** – bestehende Position eindeutig zugeordnet und plausible Werte erkannt.
- **Zuordnung prüfen** – möglicher Treffer, aber nicht sicher genug für automatische Übernahme.
- **Neue Position** – auf Screenshot erkannt, aber noch nicht im Investment-Radar-Depot vorhanden.
- **Nicht gefunden** – im Investment-Radar aktiv, auf keinem ausgewählten Screenshot gefunden.
- **Konflikt** – dieselbe Position wurde mehrfach mit widersprüchlichen Werten erkannt.
- **Ungültig** – notwendige Werte fehlen oder sind unplausibel.

Der Nutzer kann vor dem Speichern Zuordnungen und erkannte Werte korrigieren.

### 3.4 Änderungen übernehmen

Vor dem finalen Speichern zeigt die App eine Zusammenfassung, z. B.:

- 6 Positionen aktualisieren
- 1 neue Position anlegen
- 1 Position als verkauft markieren
- 0 Positionen löschen
- 1 Position unverändert lassen

**„Änderungen übernehmen“** wird nur aktiviert, wenn alle Einträge entweder eindeutig gültig oder bewusst als „Behalten“/„Ignorieren“ entschieden wurden.

## 4. Lokale OCR und Trade-Republic-Parser

Die OCR-Schicht liefert ausschließlich Textblöcke inklusive Positionen im Bild. Die fachliche Interpretation erfolgt anschließend in einem eigenen, deterministischen `TradeRepublicScreenshotParser`.

Pipeline:

`Bild -> lokale OCR -> normalisierte Textblöcke -> TradeRepublicScreenshotParser -> erkannte Positionen -> PortfolioAssetMatcher -> Prüfmodell`

Der Parser ist auf Trade-Republic-Depotansichten beschränkt und erkennt nur Werte, die tatsächlich sichtbar sind, insbesondere:

- Wertpapiername;
- aktueller Positionswert;
- sichtbarer absoluter Gewinn/Verlust;
- sichtbarer prozentualer Gewinn/Verlust;
- Stückzahl nur, wenn sie in einem unterstützten Screenshot ausdrücklich sichtbar ist.

### 4.1 Zahlenformate

Der Parser muss mindestens unterstützen:

- `1.217,07 €`
- `+34,40 €`
- `-3,71 €`
- `+4,53 %`
- `-2,17 %`

Tausenderpunkt, Dezimalkomma, Vorzeichen und Währung werden normalisiert. Fehlende Werte werden `null`, niemals `0`.

### 4.2 Einstand aus sichtbarem G/V

Wenn ein Screenshot **aktuellen Positionswert und absoluten Gewinn/Verlust eindeutig für dieselbe Position** enthält, darf die App einen Snapshot-Einstand als

`Snapshot-Einstand = aktueller Positionswert - absoluter G/V`

berechnen. Dieser Wert wird ausdrücklich als **aus Screenshot abgeleitet** behandelt und vor Übernahme angezeigt. Daraus wird keine Kauftransaktion erzeugt.

Wenn die Zuordnung zwischen Wert und G/V nicht eindeutig ist, erfolgt keine Ableitung.

### 4.3 Mehrere Screenshots

- Identische Positionen werden anhand ihrer Asset-Zuordnung zusammengeführt.
- Identische Werte sind kein Fehler.
- Abweichende Werte derselben Position erzeugen `KONFLIKT` und müssen manuell geprüft werden.
- Überlappende Screenshots dürfen keine doppelten Depotpositionen erzeugen.

## 5. Matching mit Investment Radar

Die Asset-Zuordnung erfolgt in Stufen:

1. exakter normalisierter Name/Ticker gegen bekannte Investment-Radar-Assets;
2. bekannte Alias-/Namensvarianten;
3. eindeutiger fuzzy Kandidat oberhalb einer konservativen Schwelle;
4. sonst manuelle Auswahl durch den Nutzer.

Ein unsicheres Matching wird niemals automatisch gespeichert.

Neue Positionen werden erst nach manueller Bestätigung angelegt. Wenn das Wertpapier bereits als handelbares Investment-Radar-Asset bekannt ist, wird dessen bestehende `itemId` verwendet. Unbekannte Titel müssen vor dem Anlegen eindeutig einem unterstützten Asset zugeordnet oder bewusst als eigener Wert angelegt werden.

## 6. Datenmodell

### 6.1 Portfolio-Status

`PortfolioPosition` erhält einen expliziten Status:

```kotlin
enum class PortfolioLifecycleStatus {
    ACTIVE,
    SOLD_MARKED
}
```

Standard für bestehende Daten ist `ACTIVE`.

`isActiveHolding()` berücksichtigt den Status zentral:

- `SOLD_MARKED` -> immer `false` für aktive Depotanalysen;
- `ACTIVE` -> bisherige Bestandslogik bleibt erhalten.

Zusätzlich gilt für `SOLD_MARKED`:

- `currentValue(...)` liefert keinen aktiven Depotwert;
- `unrealizedProfitLoss(...)` liefert keine aktive unrealisierte Performance;
- der aktuelle Marktpreis wird für die Anzeige direkt aus den vorhandenen `InvestmentItem`-/Marktdaten gelesen, nicht aus `PortfolioPosition.currentValue(...)`.

Damit kann eine verkaufte Position weiterhin einen aktuellen **Marktpreis** anzeigen, ohne versehentlich wieder als Depotbestand zu zählen.

### 6.2 Snapshot-Daten

Bestehende Felder bleiben die Basis:

- `snapshotValueEur`
- `snapshotCostBasisEur`
- `trackedShares`
- `purchases`
- `sales`

Ergänzt wird:

- `snapshotCapturedAt` – Zeitpunkt des zuletzt bestätigten Screenshot-Snapshots.

Ein Screenshot-Update einer bestehenden Position darf standardmäßig nur Snapshot-Felder aktualisieren. `purchases` und `sales` bleiben unverändert.

Eine erkannte Stückzahl darf nur nach expliziter Bestätigung in `trackedShares` übernommen werden.

Wenn keine Stückzahl vorhanden ist, darf `snapshotValueEur` nicht als dauerhaft live fortgeschriebener Depotwert ausgegeben werden. Die UI zeigt stattdessen **„Letzter bestätigter Trade-Republic-Wert“** mit Zeitstempel. Sobald eine verlässliche Stückzahl vorhanden ist, kann der bestehende Live-Wert über Stückzahl × aktuellen Kurs berechnet werden.

### 6.3 Import-Metadaten

Für jeden bestätigten Import wird ein kleiner lokaler Metadatensatz gespeichert:

- Import-ID;
- Zeitpunkt;
- Anzahl verarbeiteter Screenshots;
- Anzahl aktualisierter/neuer/verkaufter/gelöschter Positionen;
- keine Bilddateien;
- kein vollständiger OCR-Rohtext.

Diese Historie dient Nachvollziehbarkeit und Vorher/Nachher-Anzeige, nicht der Rekonstruktion privater Screenshots.

## 7. Verhalten je Abgleichfall

### 7.1 Bestehende Position erkannt

Nach Bestätigung können aktualisiert werden:

- `snapshotValueEur`;
- `snapshotCapturedAt`;
- `snapshotCostBasisEur`, wenn eindeutig erkannt/abgeleitet und bestätigt;
- `trackedShares`, nur wenn explizit sichtbar und bestätigt.

Kauf- und Verkaufstransaktionen werden nicht geändert.

### 7.2 Neue Position erkannt

Die App zeigt:

- erkanntes Wertpapier;
- erkannte Werte;
- vorgeschlagene Investment-Radar-Zuordnung;
- manuelle Korrekturmöglichkeit.

Nach Bestätigung wird eine neue Position ohne erfundene Transaktion angelegt. Vorhandene Snapshot-Felder werden gesetzt; unbekannte Felder bleiben leer.

### 7.3 Bestehende Position nicht gefunden

Keine automatische Aktion. Der Nutzer entscheidet:

**Behalten**  
Keine Änderung.

**Als verkauft markieren**  
- setzt `lifecycleStatus = SOLD_MARKED`;
- erzeugt keine Verkaufstransaktion;
- erzeugt keinen Verkaufserlös;
- schreibt kein Cash und kein zusätzliches Kaufbudget gut;
- behält vorhandene Kauf-/Verkaufshistorie;
- Position zählt nicht mehr zu aktivem Depotwert, Gewichtung oder Advisor;
- aktueller Marktpreis kann weiterhin aus der bestehenden Kursversorgung angezeigt werden.

**Löschen**  
- deutliche Sicherheitsabfrage;
- entfernt die Position und ihre positionsbezogene Kauf-/Verkaufshistorie aus Investment Radar;
- entfernt zugehörige Snapshot-/Tracking-Daten;
- verändert nicht rückwirkend das globale Budget-Journal, damit historische Budgetrechnungen nicht still umgeschrieben werden.

### 7.4 Verkaufte Position wieder im Screenshot

Wenn ein `SOLD_MARKED`-Asset später wieder in Trade Republic erkannt wird, zeigt die App ausdrücklich:

**„Diese Position wurde zuvor als verkauft markiert. Wieder aktivieren?“**

Erst nach Bestätigung wird der Status wieder `ACTIVE`. Es wird keine neue Kauftransaktion erfunden.

## 8. Darstellung aktiver und verkaufter Positionen

### 8.1 Aktive Position ohne Stückzahl

Wenn ein Screenshot einen aktuellen Positionswert liefert, aber keine Stückzahl vorhanden ist, zeigt die App:

- **Letzter bestätigter Trade-Republic-Wert**;
- Zeitstempel des Imports;
- bekannten Einstand, falls vorhanden;
- aktuellen Einzelkurs des Wertpapiers aus der bestehenden Kursversorgung, falls verfügbar.

Der Screenshot-Wert wird nicht fälschlich als ständig aktueller Live-Wert bezeichnet.

### 8.2 Verkaufte Position

Verkaufte Positionen erscheinen getrennt von aktiven Depotpositionen unter **„Verkaufte Positionen“**.

Sie zeigen:

- Wertpapiername und Ticker;
- Status „Verkauft“;
- vorhandene historische Kauf-/Verkaufsdaten;
- aktuellen Marktpreis aus der bestehenden Kursversorgung, wenn verfügbar.

Sie gehen nicht in aktiven Depotwert, Gewichtung, freies Budget oder Kaufempfehlungen ein.

Der aktuelle Marktpreis ist ausdrücklich **nicht** der tatsächliche Verkaufskurs.

## 9. Speicherung, Backup und Atomizität

Vor jeder bestätigten Übernahme wird der aktuelle lokale Depotzustand als Sicherungspunkt serialisiert.

Die eigentliche Portfolioänderung wird nicht durch viele einzelne `PortfolioStore.save(...)`-Aufrufe durchgeführt. `PortfolioStore` erhält eine Import-Operation, die alle final bestätigten Positionsänderungen in einem vorbereiteten Gesamtzustand zusammenfasst und mit einem synchron bestätigten Persistenzschritt schreibt.

Ablauf:

1. aktuellen Zustand sichern;
2. alle validierten Änderungen auf einen neuen In-Memory-Zustand anwenden;
3. vollständige Änderung in einem Store-Commit persistieren;
4. Commit-Erfolg prüfen;
5. UI-State neu laden;
6. bei Fehler Sicherungspunkt wiederherstellen.

Es darf keinen Zustand geben, bei dem nur ein Teil des Abgleichs gespeichert wurde.

## 10. UI-Komponenten

Geplante Komponenten:

- `DepotScreenshotImportLauncher`
- `DepotScreenshotImportScreen`
- `ScreenshotSelectionStep`
- `ScreenshotRecognitionProgress`
- `DepotReconciliationList`
- `DepotReconciliationRow`
- `AssetMatchDialog`
- `MissingPositionActionDialog`
- `DeletePositionConfirmationDialog`
- `DepotImportSummary`

Die UI verwendet das bestehende Material-/Investment-Radar-Design. Kein neues visuelles Theme.

## 11. Technische Komponenten

### `TradeRepublicScreenshotImporter`
Koordiniert mehrere Bild-URIs und die lokale OCR-Verarbeitung.

### `TradeRepublicScreenshotParser`
Reine Parserlogik für OCR-Textblöcke. Keine Android-Speicherung.

### `PortfolioAssetMatcher`
Ordnet erkannte Namen bekannten Investment-Radar-Assets zu und liefert Konfidenz/Kandidaten.

### `ScreenshotDepotReconciliation`
Reine fachliche Abgleichschicht. Erzeugt Vorschläge und Konflikte, schreibt selbst nichts.

### `PortfolioImportApplier`
Validiert die final bestätigten Nutzerentscheidungen und erzeugt den neuen Portfoliozustand.

### `PortfolioStore`
Persistiert Status, Snapshot-Zeitpunkt, Import-Metadaten und Depotdaten. Bestehende Datensätze ohne Status werden automatisch als `ACTIVE` gelesen.

Diese Trennung hält OCR, Parser, Matching, Fachlogik und Speicherung unabhängig testbar.

## 12. Datenschutz und Sicherheit

- OCR läuft ausschließlich lokal auf dem Android-Gerät.
- Kein Screenshot-Upload in das bestehende Investment-Radar-Backend.
- Kein Telemetrie-Event enthält OCR-Rohtext oder Depotbilder.
- Screenshot-URIs werden nur für den laufenden Import verwendet.
- Nach Abschluss oder Abbruch werden keine eigenen Bildkopien behalten.
- Manuelle Nutzerbestätigung bleibt die letzte Instanz vor Datenänderungen.

## 13. Fehlerbehandlung

- Nicht lesbares Bild -> Bild als fehlgeschlagen markieren; andere Bilder weiter verarbeiten.
- Keine Trade-Republic-Struktur erkannt -> Bild nicht interpretieren, Nutzer informieren.
- Unbekanntes Wertpapier -> manuelle Zuordnung.
- Doppelte widersprüchliche Erkennung -> Konflikt.
- Ungültige/negative Positionswerte -> keine Übernahme.
- Fehlende Werte -> leer lassen, nicht mit Null ersetzen.
- Speicherfehler -> kompletter Rollback auf den Sicherungspunkt.
- Marktpreis nicht verfügbar -> verkaufte Position bleibt korrekt geschlossen; UI zeigt „Kurs nicht verfügbar“ statt einen alten Snapshot als Live-Kurs.

## 14. Tests

### 14.1 Parser-Unit-Tests

Mindestens:

- einzelner Trade-Republic-Screenshot;
- mehrere Positionen;
- deutsches Zahlenformat;
- positiver und negativer G/V;
- OCR-Zeichenfehler in typischen Euro-/Prozentformaten;
- abgeschnittener Eintrag;
- keine TR-Struktur;
- überlappende Screenshots;
- widersprüchliche Duplikate.

### 14.2 Matching-Tests

- exakter Name;
- Alias;
- eindeutiger Kandidat;
- mehrere ähnliche Kandidaten -> Prüfung;
- unbekanntes Asset -> keine automatische Zuordnung.

### 14.3 Reconciliation-Tests

- bestehende Position aktualisieren;
- neue Position anlegen;
- unbestätigter Eintrag wird nicht übernommen;
- nicht gefundene Position bleibt standardmäßig unverändert;
- als verkauft markieren;
- löschen;
- `SOLD_MARKED` wieder aktivieren;
- Kauf-/Verkaufshistorie bleibt bei Snapshot-Update identisch;
- kein Cash-/Budgeteffekt durch `SOLD_MARKED`;
- Duplikat/Konflikt blockiert automatische Übernahme.

### 14.4 Store-/Migrations-Tests

- bestehende Datensätze ohne Status lesen als `ACTIVE`;
- `SOLD_MARKED` persistieren und wieder laden;
- `snapshotCapturedAt` persistieren;
- Backup/Rollback;
- atomarer Gesamt-Commit;
- Löschung entfernt positionsbezogene Daten vollständig;
- globales Budget-Journal bleibt bei Löschung unverändert.

### 14.5 UI-/Instrumentierungstests

- Mehrfachauswahl;
- Fortschrittsanzeige;
- Korrektur eines OCR-Werts;
- neue Position zuordnen;
- „Behalten / Verkauft / Löschen“;
- Löschbestätigung;
- Zusammenfassung vor Übernahme;
- Import abbrechen ohne Datenänderung;
- verkaufte Position zeigt Marktpreis, zählt aber nicht zum aktiven Depot.

### 14.6 Regressionstests

Nach Integration müssen weiterhin funktionieren:

- bestehende manuelle Käufe;
- bestehende manuelle Verkäufe;
- Depotwert/Gewichtung;
- Budget-Cockpit;
- Sparpläne;
- Advisor;
- Exit-Strategien;
- Backup/Restore;
- aktuelle Marktpreise;
- App-Update-Funktion.

## 15. Produktionsfreigabe

Die Funktion gilt erst dann als fertig, wenn sie tatsächlich auf Produktion veröffentlicht und dort verifiziert wurde.

Release-Gate:

1. Implementierung auf Feature-Branch;
2. Unit-/UI-/Regressionstests grün;
3. Android-Release-Build erfolgreich;
4. kein Datenverlust in Migrations-/Rollbacktests;
5. Review der Änderungen;
6. Merge in den vorgesehenen Produktionsbranch;
7. bestehende Release-Pipeline erfolgreich;
8. Produktionsversion installiert/verifiziert;
9. erst danach 100 % Fertigstellungsstatus.

Bis zum erfolgreichen Produktionsdeploy bleibt der Produktionsfortschritt unter 100 %.

## 16. Bestehende Vorarbeit auf dem Feature-Branch

Auf `feature/depot-screenshot-review-20261015` existiert bereits eine erste reine Reconciliation-Vorarbeit. Diese ist **nicht** als fertige Implementierung zu behandeln. Sie wird im Implementierungsplan gegen dieses Design geprüft und bei Bedarf ersetzt oder erweitert, insbesondere für:

- `SOLD_MARKED`;
- neue Positionen;
- Mehrfach-Screenshot-Konflikte;
- lokale OCR;
- Snapshot-Zeitstempel;
- atomare Übernahme;
- vollständige UI.

## 17. Akzeptanzkriterien

Das Feature ist fachlich akzeptiert, wenn der Nutzer in der Android-App:

1. „Depot aktualisieren“ öffnen kann;
2. mehrere Trade-Republic-Screenshots auswählen kann;
3. sieht, dass die Analyse lokal erfolgt;
4. erkannte Positionen und Werte vor Speicherung prüfen und korrigieren kann;
5. neue Positionen bestätigen kann;
6. fehlende Positionen behalten, als verkauft markieren oder löschen kann;
7. eine Position als verkauft markieren kann, ohne Verkaufskurs/-erlös eingeben zu müssen;
8. verkaufte Positionen nicht mehr im aktiven Depotwert sieht;
9. für verkaufte Positionen weiterhin den aktuellen Marktpreis sehen kann, wenn Kursdaten verfügbar sind;
10. Snapshotwerte ohne Stückzahl klar als letzten bestätigten Trade-Republic-Wert mit Zeitstempel sieht;
11. keine unbeabsichtigte Änderung von Kauf-/Verkaufshistorie, Sparplänen oder Budget erlebt;
12. vor dem Speichern eine vollständige Änderungszusammenfassung erhält;
13. bei Fehlern automatisch auf den vorherigen konsistenten Depotstand zurückfällt.
