# START HIER – Investment Radar Live 1.1.8

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
