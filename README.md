# Investment Radar Live

Android-Live-App mit Push-Benachrichtigungen fuer Aktien und ETFs.

## Enthalten

- Android-App (Kotlin + Jetpack Compose)
- Live-Dashboard mit Marktampel, Top-Pick und 100-EUR-Plan
- Live-Kurse ueber einen serverseitigen Marktdaten-Provider
- Trade-Republic-Suchname und ISIN
- FCM Push-Benachrichtigungen
- Verkaufs-/Pruefsignale mit Deduplizierung
- Azure Functions Backend
- optionale automatische Synchronisation mit dem bestehenden Google-Sheet
- GitHub Actions zum APK-Build und Backend-Deploy

## Architektur

Android App -> Azure Functions API -> Twelve Data
                         -> Firebase Cloud Messaging
                         -> Azure Blob State

Geheime API-Schluessel liegen nur im Backend, nicht in der Android-App.

## Schnellstart

1. `SETUP.md` abarbeiten.
2. Backend nach Azure Functions deployen.
3. Android-Konfiguration in `android/gradle.properties` bzw. GitHub Secrets eintragen.
4. GitHub Workflow `Build Android APK` starten.
5. APK aus den Workflow-Artefakten installieren.

## Wichtiger Hinweis

Die App fuehrt keine Orders aus. Signale sind Entscheidungshilfen, keine Renditegarantie und keine automatische Anlageberatung.


### Wichtig vor dem APK-Build

Die Live-App benötigt eine echte Azure-Backend-Adresse. Version 1.1.9 baut bewusst keine APK ohne `INVESTMENT_API_BASE_URL` und ohne dauerhafte Android-Signierung. Bei Flex Consumption wird ausschließlich die echte Azure-Standarddomäne verwendet. Der Backend-Workflow ist erst grün, wenn der Deploy erfolgt ist und `/api/health` erreichbar ist.


### Flex Consumption 1.1.9

Backend-Deploy: `sku: flexconsumption`, `remote-build: true`. Health-Check und Android-App verwenden die Repository-Variable `INVESTMENT_API_BASE_URL`.


## Dauerhafte Android-Signierung ab 1.1.9

GitHub baut ab 1.1.9 eine signierte Release-APK. Der private Keystore liegt **nicht** im Repository. Einmalig `scripts/create-android-signing-key.ps1` auf einem Windows-PC mit installiertem Java ausführen und anschließend diese GitHub Secrets setzen:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Den erzeugten `investment-radar-release.jks` zusätzlich offline sichern. Ohne denselben Keystore können spätere APKs nicht als Update installiert werden.

Die vor 1.1.9 installierte Debug-App muss einmal deinstalliert werden. Danach 1.1.9 installieren. Ab dann funktionieren Updates mit demselben Signierschlüssel.
