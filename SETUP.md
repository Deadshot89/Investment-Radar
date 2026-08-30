# Einrichtung

## 1. Marktdaten

Ein Twelve-Data-Konto anlegen und einen API-Key erzeugen. Der Key wird als Azure-App-Setting gespeichert:

- `TWELVE_DATA_API_KEY`

Die App fragt den Key niemals direkt ab.

## 2. Firebase Push

In Firebase ein Projekt anlegen und Cloud Messaging aktivieren.

Android-App-Konfiguration als GitHub Secrets oder lokal in `android/gradle.properties` setzen:

- `INVESTMENT_API_BASE_URL`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Fuer das Backend wird das Firebase Service Account JSON als **eine Zeile JSON** in Azure gespeichert:

- `FIREBASE_SERVICE_ACCOUNT_JSON`

Die Android-App abonniert automatisch das Topic `investment-alerts`.

## 3. Azure Functions

Benötigte App Settings:

- `TWELVE_DATA_API_KEY`
- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `AzureWebJobsStorage`
- optional `ALERT_TOPIC=investment-alerts`
- optional `ADMIN_API_KEY=<langes-zufaelliges-passwort>`

Der Timer prueft standardmaessig alle 15 Minuten.

## 4. Android APK ueber GitHub bauen

Der Workflow installiert Android SDK 37, Build Tools 36.0.0 und Gradle 9.5.0 automatisch.

Repo nach GitHub hochladen. Unter Repository Settings -> Secrets and variables -> Actions die folgenden Repository Secrets anlegen:

- `INVESTMENT_API_BASE_URL`
- `FIREBASE_APP_ID`
- `FIREBASE_API_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_SENDER_ID`

Danach Actions -> `Build Android APK` -> Run workflow.

Die fertige Debug-APK liegt im Workflow-Artefakt `investment-radar-apk`.

## 5. Backend deployen

Fuer Azure Functions den Secret `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` setzen und optional die Variable `AZURE_FUNCTIONAPP_NAME`.
Dann den Workflow `Deploy Backend` starten.

## Signalregeln v1

Ein Alarm wird erzeugt, wenn eine Regel aus `backend/data/investments.json` ausloest, z. B.:

- Tagesverlust groesser/gleich `reviewDrop1dPct`
- Kurs unter `hardReviewBelow`
- manueller Status `VERKAUFEN` oder `DRINGEND_PRUEFEN`

Normale Tagesbewegungen erzeugen keinen Alarm.
