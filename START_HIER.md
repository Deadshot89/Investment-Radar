# START HIER – Investment Radar Live 1.1.9

## A. Azure Flex Consumption

Die Function App ist für **Flex Consumption / Linux / Node.js 22** vorgesehen.

Nach dem Erstellen der Function App brauchst du aus Azure:
- Ressourcenname der Function App, z. B. `InvestmentRadar`
- **Standarddomäne** aus der Übersicht, vollständig inklusive `https://`
- Publish Profile

## B. GitHub eintragen

Repository -> Settings -> Secrets and variables -> Actions

**Variables**
- `AZURE_FUNCTIONAPP_NAME` = Azure Ressourcenname
- `INVESTMENT_API_BASE_URL` = vollständige Standarddomäne, z. B. `https://investmentradar-....germanywestcentral-01.azurewebsites.net`

**Secret**
- `AZURE_FUNCTIONAPP_PUBLISH_PROFILE` = kompletter XML-Inhalt der Publish-Profile-Datei

Den Publish-Profile-Inhalt niemals im Chat oder Repository veröffentlichen.

## C. Workflows

1. `Deploy Backend` starten.
2. Der Workflow deployt mit `sku: flexconsumption` und `remote-build: true`.
3. Health-Check läuft gegen `${INVESTMENT_API_BASE_URL}/api/health`.
4. Erst wenn das grün ist, `Build Android APK` starten.
5. Der Android-Build verwendet exakt dieselbe `INVESTMENT_API_BASE_URL`.

## D. Firebase / Marktdaten

Für den ersten Health-Test noch nicht zwingend nötig. Für Live-Kurse und Push später ergänzen:
- `TWELVE_DATA_API_KEY` in Azure
- `FIREBASE_SERVICE_ACCOUNT_JSON` in Azure
- `FIREBASE_APP_ID`, `FIREBASE_API_KEY`, `FIREBASE_PROJECT_ID`, `FIREBASE_SENDER_ID` in GitHub Secrets


## E. Einmalige Android-Signierung

Vor dem ersten 1.1.9-Build auf Windows im Projektordner PowerShell öffnen und `scripts\create-android-signing-key.ps1` ausführen. Die dabei abgefragten Passwörter sicher notieren und den erzeugten `investment-radar-release.jks` außerhalb des Repositories sichern.

Danach GitHub -> Settings -> Secrets and variables -> Actions -> Secrets:
- `ANDROID_KEYSTORE_BASE64` = kompletter Inhalt von `ANDROID_KEYSTORE_BASE64.txt`
- `ANDROID_KEYSTORE_PASSWORD` = beim Erstellen gewähltes Keystore-Passwort
- `ANDROID_KEY_ALIAS` = `investment-radar`
- `ANDROID_KEY_PASSWORD` = beim Erstellen gewähltes Schlüssel-Passwort

Die alte, vor 1.1.9 installierte Investment-Radar-App einmal deinstallieren. Anschließend die signierte 1.1.9-Release-APK installieren. Danach künftig nicht mehr deinstallieren, sondern einfach über die bestehende App aktualisieren.
