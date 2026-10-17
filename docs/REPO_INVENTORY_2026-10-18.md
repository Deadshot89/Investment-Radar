# Repository-Inventar – Stand 18.10.2026

Grundlage: `main` @ `048bf7f` (Android 2.5.49 / versionCode 119, Backend 2.1.0).
Zweck: Handover-Aufgaben **TASK-006** (`api/`-Rolle) und **TASK-007** (Legacy-/Hotfix-Dateien) belastbar klären.
**Es wurde nichts gelöscht.** Aufräumen erst nach Produktionsprüfung von 2.5.49 und ausdrücklicher Freigabe.

## Aktive Struktur (produktiv genutzt)

| Pfad | Rolle | Nachweis |
|---|---|---|
| `android/` | Android-App, Tests, Contract-Skripte (`android/tests/*.sh`) | alle vier Android-Workflows, `paths: android/**` |
| `backend/` | Azure Functions Backend 2.1.0, Tests unter `backend/test/` | `backend-deploy.yml`, `npm run check` / `npm test` |
| `.github/workflows/` | CI, Release, Backend-Deploy | GitHub Actions |
| `scripts/assert-android-app-production-unchanged.sh`, `scripts/test-current-docs.sh` | einzige `scripts/`-Dateien, die eine Workflow-Datei aufruft | `grep scripts/ .github/workflows/*.yml` |
| `docs/superpowers/` | Spec und Plan zum Screenshot-Import | Handover |
| `README.md`, `SETUP.md`, `START_HIER.md` | aktuelle Doku | `scripts/test-current-docs.sh` |

## TASK-006 – `api/`

**Ergebnis: historischer, nicht deployter Update-Service. Keine produktive Rolle.**

- Inhalt: `api/src/functions/appUpdate.mjs` (Route `GET /api/app-update`), `api/src/lib/updateManifest.mjs`, `api/test/updateManifest.test.mjs`.
- Kein `package.json`, kein `host.json`, kein Workflow deployt oder testet `api/`.
- Das Backend (`backend/src`) registriert keine Route `app-update`.
- Die App holt Updates direkt über die GitHub-API:
  `android/.../AppUpdateManager.kt` → `https://api.github.com/repos/$repository/releases/latest`.
- Letzte Änderung an `api/`: Commit `2347096` „Stand 1.2.2“.

Empfehlung: nach Freigabe entfernen. Wird ein eigenes Update-Manifest später gewünscht, sollte es eine Route in `backend/` werden und kein zweiter Function-App-Ordner.

## TASK-007 – Root-Level-Dateien

`git ls-files` zählt **111 Dateien** außerhalb von `android/`, `backend/`, `.github/`, `docs/` und `scripts/`.

### Prüfung

Ich habe alle Workflows, `scripts/`, `android/tests/`, `tests/`, `backend/package.json` und `backend/test/` nach Verweisen auf **Repo-Root-Pfade** durchsucht.
Treffer gab es nur in Fehlermeldungstexten oder in `$ROOT/android/...`- bzw. `$ROOT/backend/...`-Pfaden.
**Keine Root-Datei wird von CI, Release oder Deploy gelesen.**

Die Root-Kopien sind deutlich ältere Stände der aktiven Dateien, zum Beispiel:

| Datei | Root (Zeilen) | aktiv (Zeilen) |
|---|---:|---:|
| `MainActivity.kt` | 1340 | 2623 |
| `MainViewModel.kt` | 159 | 673 |
| `ApiClient.kt` | 118 | 379 |
| `PortfolioStore.kt` | 155 | 330 |
| `AppUpdateManager.kt` | 185 | 270 |
| `market.mjs` | 217 | 245 |
| `state.mjs` | 57 | 73 |
| `android-build.yml` | abweichend von `.github/workflows/android-build.yml` | |

Die letzten Änderungen an diesen Dateien stammen aus 1.1.x bis 1.2.0 (September 2026).

### Klassifizierung

| Gruppe | Dateien | Einstufung | Empfehlung |
|---|---|---|---|
| Hotfix-Notizen | `HOTFIX_1.1.1.md` … `HOTFIX_1.2.0.md` (30 Dateien) | Historie | nach `docs/history/hotfixes/` verschieben oder löschen (Inhalt bleibt in Git) |
| Upload-/Paket-Hinweise | `UPLOAD_HINWEIS.txt` (1.1.25), `FILES.txt` | veraltet, irreführend | löschen |
| Android-Altkopien | `AndroidManifest.xml`, `ApiClient.kt`, `AppUpdateManager.kt`, `CustomInvestmentStore.kt`, `MainActivity.kt`, `MainViewModel.kt`, `Models.kt`, `NetworkRetryPolicy.kt`, `PortfolioPosition.kt`, `PortfolioStore.kt`, `update_file_paths.xml`, `build.gradle.kts` | veraltete Duplikate | löschen |
| Backend-Altkopien | `config.mjs`, `customAsset.mjs`, `customQuote.mjs`, `dashboard.mjs`, `dashboard (1).mjs`, `envDiagnostics.mjs`, `fxCache.mjs`, `health.mjs`, `index.mjs`, `market.mjs`, `marketSupport.mjs`, `marketWatch.mjs`, `push.mjs`, `quoteCache.mjs`, `sheets.mjs`, `signals.mjs`, `state.mjs`, `testPush.mjs`, `host.json`, `package.json`, `investments.json` | veraltete Duplikate | löschen |
| Alt-Tests (Root) | `*.test.mjs` im Root, `test-*.sh` im Root | laufen nirgends | löschen |
| Ordner `tests/` | `AppUpdatePolicyTest.kt`, `InvestmentPlannerTest.kt`, 9 × `*.test.mjs` | laufen nirgends (Backend nutzt `backend/test/`) | löschen, vorher prüfen, ob ein Testfall noch fehlt |
| Workflow-Kopien | `android-build.yml`, `backend-deploy.yml` im Root | inaktiv, aber verwechselbar | löschen |
| `scripts/` (20 Testskripte + `verify-workflows.sh`) | `scripts/test-*.sh` außer `test-current-docs.sh`, dazu `verify-workflows.sh` | lokal, nicht in CI | einzeln prüfen: in `android/tests/` übernehmen oder löschen. `create-android-signing-key.ps1` behalten (Keystore-Erstellung, DEC-007) |

## Weitere Befunde

### Branch-Schutz und Pfadfilter (neu, P2)

`main` ist seit 17.10.2026 geschützt: vier Pflicht-Checks (`contracts`, `unit-tests`, `build`, `instrumented-ui`), `strict`, kein Force-Push oder Löschen, Admin-Override erlaubt.
Die vier Android-Workflows starten aber nur bei `push` mit `paths: android/**` (plus Workflow-Dateien).
**Folge:** Ein PR, der nur `backend/`, `docs/` oder Root-Dateien ändert, bekommt diese Checks nie und lässt sich nur per Admin-Override mergen. Das gilt auch für den PR zu diesem Dokument.
Optionen:
1. so lassen und Backend-/Doku-PRs bewusst per Admin mergen,
2. Required Checks auf Android-PRs beschränken (Rulesets mit Pfadbedingung gibt es bei GitHub nicht direkt; ggf. Gate-Job),
3. einen leichten „Gate“-Job ohne Pfadfilter einführen, der bei Nicht-Android-Änderungen sofort grün meldet.

### CI-Emulator-Flake (beobachtet)

Beim ersten `main`-Lauf von 2.5.49 meldete `instrumented-ui` „Unknown API Level / No compatible devices connected“. Der Emulator war noch nicht bereit, es lief kein Test.
Ein Rerun war grün, der Release-Gate hat korrekt blockiert und nichts veröffentlicht.
Tritt das öfter auf, im Workflow vor `connectedDebugAndroidTest` auf `sys.boot_completed` und `getprop ro.build.version.sdk` warten.

## Vorgeschlagene Reihenfolge

1. Produktions-Smoke 2.5.49 auf echtem Gerät (TASK-003/004) abschließen.
2. Freigabe für das Aufräumen einholen, dann in **einem** PR: Root-Altkopien, `tests/`, `api/`, Workflow-Kopien entfernen und Hotfix-Notizen verschieben.
3. `scripts/test-*.sh` einzeln prüfen (eigener PR).
4. Entscheidung zum Pfadfilter/Branch-Schutz treffen.
