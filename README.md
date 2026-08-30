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
