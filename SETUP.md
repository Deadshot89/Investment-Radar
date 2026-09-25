# Einrichtung

## 1. GitHub + Azure Flex Consumption

Repository **Settings -> Secrets and variables -> Actions**.

### Variables

- `AZURE_FUNCTIONAPP_NAME` = exakter Azure-Ressourcenname der Function App
- `INVESTMENT_API_BASE_URL` = vollständige Azure-Standarddomäne inklusive `https://`

Bei Flex Consumption darf die API-URL nicht aus dem App-Namen geraten werden. Verwende immer die in Azure angezeigte Standarddomäne.

Der Backend-Workflow nutzt Flex Consumption mit Remote Build.

## 2. Marktdaten

Für die aktuell konfigurierte Marktdaten-/Fundamentaldatenquelle wird benötigt:

- Azure App Setting `TWELVE_DATA_API_KEY`

Das Backend meldet über `/api/health`, ob Markt- und Fundamentaldaten konfiguriert sind.

## 3. Firebase Push

### GitHub Secrets für den Android-Build

- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

### Azure App Setting

- `FIREBASE_SERVICE_ACCOUNT_JSON`

Optional kann `ALERT_TOPIC` gesetzt werden. Ohne Angabe verwendet das System den projektspezifischen Standard.

## 4. Weitere Azure App Settings

Erforderlich bzw. je nach Funktion:

- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`

Optional:

- `ALERT_TOPIC`
- `ADMIN_API_KEY`
- `GOOGLE_SHEET_ID`
- `GOOGLE_SERVICE_ACCOUNT_JSON`

## 5. Backend veröffentlichen

1. GitHub Variables und Secrets setzen.
2. Workflow **Deploy Backend** starten.
3. `${INVESTMENT_API_BASE_URL}/api/health` öffnen.
4. Erwartete Kernwerte prüfen:
   - `ok: true`
   - `backendVersion: 2.1.0`
   - `apiSchemaVersion: 2026-09-14.1`
   - `sourceRevision` gesetzt
   - `deployId` gesetzt und nicht `development`
   - `universeTarget: 2000`

## 6. Dauerhafte Android-Signierung

Einmalig auf Windows:

```powershell
scripts\create-android-signing-key.ps1
```

Java/JDK muss installiert sein.

Das Script erzeugt lokal:

- `investment-radar-release.jks` – dauerhaft offline sichern
- `ANDROID_KEYSTORE_BASE64.txt` – Inhalt als GitHub Secret hinterlegen

GitHub Secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Der Workflow bricht bewusst ab, wenn die dauerhafte Signierung nicht vollständig konfiguriert ist.

**Wichtig:** Den Produktions-Keystore niemals ersetzen oder verlieren. Android akzeptiert spätere Updates nur, wenn sie mit demselben Schlüssel signiert sind.

## 7. Android bauen

Aktueller App-Stand:

- `versionName = 2.5.24`
- `versionCode = 94`

Workflow **Build Android APK** starten.

Der Build prüft unter anderem:

1. Regressionstests
2. JVM Unit Tests
3. signierten Release-Build
4. APK-Signatur
5. Live-Backend-Version und API-Schema
6. exakte Backend-Inhaltsrevision
7. Radar-Universum mit mindestens 2.000 Instrumenten

Eine produktive In-App-Veröffentlichung erfolgt nur von `main`.

## 8. Zusätzliche Release-Gates

Vor Merge bzw. Release werden außerdem getrennt ausgeführt:

- Android Contract Tests
- Android JVM Tests
- Build Android APK
- Android Instrumented UI Tests

Die UI-Tests laufen auf Phone- und Tablet-Konfigurationen.

## 9. App-Verhalten

- Die App führt keine Orders automatisch aus.
- Käufe werden erst nach Bestätigung budgetwirksam.
- Verkaufserlöse werden privat verwendet und erhöhen App-Cash oder Kaufbudget nicht.
- Historische Snapshots dürfen nicht als aktuelle Depotwerte verwendet werden.
- Fehlende Daten werden als Datenqualitätsproblem angezeigt statt durch erfundene Werte ersetzt.
