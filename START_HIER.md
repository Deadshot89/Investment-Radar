# START HIER – Investment Radar 2.5.24

**Android:** 2.5.24 / versionCode 94  
**Backend:** 2.1.0  
**API-Schema:** 2026-09-14.1

Diese Datei beschreibt den aktuellen Einstieg. Historische Hotfix- und Plan-Dokumente bleiben als Verlauf im Repository, sind aber nicht automatisch die aktuelle Fachspezifikation.

## A. Azure Flex Consumption

Die Function App ist für **Flex Consumption / Linux / Node.js 22** vorgesehen.

Benötigt werden aus Azure:
- Ressourcenname der Function App
- vollständige Standarddomäne inklusive `https://`
- Publish Profile

## B. GitHub Actions konfigurieren

Repository → **Settings → Secrets and variables → Actions**

### Variables
- `AZURE_FUNCTIONAPP_NAME`
- `INVESTMENT_API_BASE_URL`

### Secrets
- `AZURE_FUNCTIONAPP_PUBLISH_PROFILE`
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Publish Profile, Keystore und andere private Schlüssel niemals im Repository veröffentlichen.

## C. Azure App Settings

Erforderlich bzw. je nach aktivierter Funktion:
- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`

Optional:
- `ALERT_TOPIC`
- `ADMIN_API_KEY`
- `GOOGLE_SHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_JSON`

## D. Backend deployen und prüfen

1. Workflow **Deploy Backend** starten.
2. `${INVESTMENT_API_BASE_URL}/api/health` prüfen.
3. Erwartete Kernwerte:
   - `ok: true`
   - `backendVersion: 2.1.0`
   - `apiSchemaVersion: 2026-09-14.1`
   - `sourceRevision` gesetzt
   - `deployId` gesetzt und nicht `development`
   - `universeTarget: 2000`

## E. Android Release

Die produktive App wird mit einem dauerhaft erhaltenen Keystore signiert. Dieser Keystore darf nicht ersetzt werden, sonst akzeptiert Android spätere Updates nicht mehr.

Aktueller Stand:
- `versionName = 2.5.24`
- `versionCode = 94`

Vor einem Release müssen grün sein:
1. Android Contract Tests
2. Android JVM Tests
3. Build Android APK
4. Android Instrumented UI Tests

Der Build prüft zusätzlich APK-Signatur und Live-Backend-Kompatibilität. Produktive In-App-Veröffentlichungen erfolgen nur von `main`.

## F. Aktuelle Fachregeln

- Das monatliche Kaufbudget ist der einzige Rahmen für neue Kaufempfehlungen.
- Bestätigte Käufe reduzieren das verfügbare Monatsbudget.
- Verkaufserlöse werden privat verwendet und erhöhen **weder App-Cash noch Kaufbudget**.
- Historische Snapshots dürfen nicht als aktuelle Marktwerte ausgegeben werden.
- Fehlende oder unzuverlässige Daten werden sichtbar als Datenlücke behandelt und nicht erfunden.
- Empfehlungen können direkt bearbeitet werden; dabei wird die konkrete Transaktionsausführung angepasst, nicht still die automatische Analyse überschrieben.
- Die App führt **keine Orders automatisch aus**.

Weitere Details stehen in `README.md`, `SETUP.md`, `android/README.md` und `backend/README.md`.
