# Repository-Inventar – verifiziert am 19.10.2026

**Basis:** `main` @ `2402a4bcb83f3c73e456adbdb324ebbe5ceffe80`, Android **2.5.51** / `versionCode 121`, zuletzt veröffentlichtes APK-Release [v2.5.51](https://github.com/Deadshot89/Investment-Radar/releases/tag/v2.5.51).
Dieses Dokument aktualisiert die Analyse zu **TASK-006** (`api/`) und **TASK-007** (historische Root-Dateien), ohne Dateien zu löschen oder produktive App-Dateien zu verändern.

## Tatsächlicher Dateibestand

Vollständiger GitHub-Tree von `main` (`recursive=1`, `truncated=false`, 517 versionierte Dateien):

| Gruppe | Anzahl | Rolle / weitere Prüfung |
|---|---:|---|
| Dateien direkt im Repository-Root | **97** | häufig alte Kopien, Hotfix-Protokolle und Tests; keine pauschale Löschfreigabe |
| `tests/` | **11** | historische Tests; fehlende aktive Testabdeckung vor Bereinigung prüfen |
| `api/` | **3** | eigenständiger historischer App-Update-Prototyp; keine im GitHub-Tree vorhandenen `package.json` oder `host.json` |
| **Gesamt außerhalb von `android/`, `backend/`, `.github/`, `docs/`, `scripts/`** | **111** | Inventarkandidaten, **nicht** automatisch unbenutzt |
| `scripts/` (separat gezählt) | **24** | enthält weiterhin genutzte Release-/Dokumentationsprüfungen und lokale Hilfsskripte |

Die Zahlen beschreiben den **Git-Tree**, nicht die Laufzeitdateien auf Benutzergeräten. Historische Root-Kopien können menschliche Referenzen oder nicht dokumentierte lokale Nutzung haben; ihre Löschung ist daher nicht aus der Anzahl ableitbar.

## Aktive Produkt- und Releasepfade

| Pfad | Verwendung |
|---|---|
| `android/` | aktive Kotlin-/Compose-App, Tests, Gradle, Versionierung und Signierung |
| `backend/` | aktives API-Backend (2.1.0 als erwarteter Backend-Vertrag der Android-Veröffentlichung) |
| `.github/workflows/` | Android-Checks, APK-Publish und Backend-Workflows |
| `scripts/assert-android-app-production-unchanged.sh` | Schutz davor, ein bestehendes App-Release unter demselben Tag mit geändertem App-Code zu überschreiben |
| `docs/`, `README.md`, `START_HIER.md`, `SETUP.md` | Projektdokumentation und Übergabe |

### TASK-006 – Historischer Ordner `api/`

Der Tree enthält ausschließlich:
- `api/src/functions/appUpdate.mjs`
- `api/src/lib/updateManifest.mjs`
- `api/test/updateManifest.test.mjs`

Die Android-App bezieht verfügbare APK-Updates unmittelbar über `AppUpdateManager.kt` und die GitHub-Releases-API (`releases/latest`). Der separate `api/`-Ordner ist **kein nachgewiesener Teil** dieses produktiven Updatepfads.

**Entscheidung:** vorerst beibehalten. Nur nach Referenzprüfung, Prüfung möglicher externer Aufrufer und ausdrücklicher Freigabe entfernen. Falls künftig eine eigene Update-Manifest-Route benötigt wird, zuerst den produktiven Backend-Vertrag entwerfen und testen.

### TASK-007 – Historische Kopien und Tests

Im Root liegen unter anderem alte Kopien von `MainActivity.kt`, `MainViewModel.kt`, `PortfolioStore.kt`, `ApiClient.kt`, `android-build.yml` und `backend-deploy.yml`; aktive Versionen befinden sich unter `android/`, `backend/` bzw. `.github/workflows/`.

Empfohlene Vorgehensweise vor **jeder** Löschung:
1. Exakte Pfadreferenzen in CI, Skripten, Dokumentation und Backend-/Android-Builds prüfen.
2. Historische Testfälle auf fehlende Regressionstests in den aktiven Testverzeichnissen untersuchen.
3. Archivierung und Löschung separat planen; nachvollziehbare Backups/Commits und Wiederherstellung sicherstellen.
4. Nach ausdrücklicher Freigabe kleine, isolierte Lösch-PRs mit vollständigen verpflichtenden Checks erstellen.
5. Android-/Backend-Veröffentlichung und Benutzergerät nicht mit Root-Dateibereinigung verwechseln.

Die Hotfix-Notizen haben Dokumentationswert. Solange keine Freigabe vorliegt, bleiben sie unangetastet.

## Branch-Schutz: Befund von 18.10. ist behoben

Die frühere Behauptung, Doku- und Backend-PRs würden **keine vier Android-Pflichtchecks** erhalten und könnten nur per Admin-Override gemergt werden, ist **veraltet**.

Seit [PR #83](https://github.com/Deadshot89/Investment-Radar/pull/83) deklarieren alle vier Android-Workflows einen **ungefilterten `pull_request:`-Trigger**. Die `push`-Trigger sind weiterhin teilweise pfadgefiltert. Das ist kein Widerspruch: Für jeden Pull Request werden die vier erforderlichen Prüfungen gestartet.

Release 2.5.51 wurde am 19.10.2026 auf `main` mit **vier erfolgreichen GitHub-Actions-Läufen** veröffentlicht. Signaturprüfung, Live-Backend-Revision und begleitende Release-Gates waren erfolgreich.

**Bekannter CI-Risikobereich:** Emulatorstart und Dauer der beiden Bildschirmtests. Der UI-Job hat inzwischen **35 Minuten** Zeitlimit. Ein erfolgreicher Emulatorlauf ersetzt keinen Smoke-Test auf dem echten Smartphone.

## Status und Freigabe

- **Android 2.5.51:** auf GitHub als signierte APK veröffentlicht; reale Geräteinstallation, Textdarstellung, Screenshotimport, Budget und Live-Kursabruf beim Nutzer **noch nicht durch die CI nachgewiesen**.
- **TASK-006:** technische Rolle des `api/`-Ordners inventarisiert; **keine Löschung**.
- **TASK-007:** 111 außerhalb der aktiven Verzeichnisse liegende Dateien inventarisiert; **keine Löschung**.
- **Alte P2-Blockade „Doku-PR ohne Android-Checks“:** durch PR #83 behoben; weiterhin erforderliche Tests dürfen nicht umgangen werden.

**Diese Dokumentation ist keine Löschfreigabe und keine Bestätigung eines Tests auf einem echten Gerät.**
